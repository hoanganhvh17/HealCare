# Conventions & Gotchas

## Language
Prefer **Vietnamese** for user-facing strings, comments, and commit messages, to match the existing code. Commit messages follow a `feat:` / `fix:` prefix with a Vietnamese description, e.g. `fix: lỗi xung đột lịch với phương thức thanh toán bằng ví`.

## Templates
Thymeleaf templates live in `src/main/resources/templates/{user,admin,doctor,auth,email,error}/`, with shared fragments under each area's `include/` folder (`header`, `footer`, `sidebar`, `ai-chat`). Thymeleaf caching is disabled for development. Security-aware template expressions use `thymeleaf-extras-springsecurity6`.

**`${map[enumKey]}` silently renders empty.** Indexing a `Map` whose keys are an enum (e.g. `Map<DayOfWeek, Integer>`) produces nothing — no error, no log line, the page just loses every value while the surrounding markup renders fine. `Set.contains(enumValue)` and `enumValue.value` both work, which makes it look like the enum is fine. Pass an ordered `List` and index it with `th:each="x, iter : ..."` + `${list[iter.index]}` instead; `HeadRosterController.coverage`/`dayLabels` do exactly that and say why.

**Never load `bootstrap.bundle.min.js` twice on one page.** The footer fragments (`admin/include/footer`, `doctor/include/footer`) already load Bootstrap, simple-datatables, apexcharts, echarts, quill, tinymce and `main.js`; several pages re-declared those `<script>` tags after including the fragment. A second copy of Bootstrap registers its data-api click handler on `document` a second time, so **one click runs `toggle()` twice — the dropdown opens and closes instantly and looks completely dead**. That is what made the notification bell work on `/head/**` (one copy) but do nothing on `/doctor/dashboard` and `/doctor/manage-bookings` (two copies); every other dropdown, modal and dismissible alert on those pages was broken the same way. Include a footer fragment **or** hand-pick scripts, never both. `user/medical-record-detail.html` is the one legitimate exception and guards it: the footer only renders for DOCTOR/ADMIN, so the patient's copy carries `th:unless` on the same condition.

The mirror-image trap on the patient side is **zero** copies: `checkout-qr.html` and `payment-result.html` shipped without Bootstrap JS at all, so every dropdown in the shared header was inert there. Both now load exactly one copy.

**A `th:fragment` name must not collide with a tag name or an id in the same file.** Thymeleaf resolves `~{tpl :: sel}` by matching `sel` against the fragment name **or the tag name or the id**, and it returns **every** node that matches. `admin/include/header.html` held `<head th:fragment="header(title)">` *and* `<header th:fragment="header-nav" id="header">`, so `~{admin/include/header :: header (title='X')}` pulled in both — **all 36 staff pages rendered two stacked `<header id="header">` bars**, one of them the admin bar (with a link to `/admin/dashboard`) on doctor and receptionist pages. Invisible on desktop, because both are `fixed-top` at `top:0` and the second paints over the first.

It broke the mobile sidebar, and the failure is the nastiest kind — the button is *there*, it just does nothing. `assets-admin/js/main.js` binds with `select('.toggle-sidebar-btn')` = **`querySelector`, first match only**, and the first match is the buried duplicate. The visible button on top had no listener. The `<head>` fragments are now named **`pagehead(title)`** in all three header files; keep any new fragment name distinct from every tag name and id in its file.

**`~{user/index :: footer}` is a fragment that does not exist** — `user/index.html` declares no `th:fragment` whatsoever. Six templates referenced it (`career-details`, `careers`, `doctor-schedule`, `medical-process`, `payment-result`, `working-hours`), and Thymeleaf aborts the render at that tag: the page lost its footer **and every `<script>` declared after it**, which is where Bootstrap sits. That is why the notification bell rendered but would not open on those pages. The real fragment is `~{user/include/footer :: footer}`. Grep for the bad form before adding a footer to a new patient page.

**`th:if="${a} and ${b}"` blows up when either side is a String.** Thymeleaf runs on **SpEL** here (Spring Boot injects a `SpringTemplateEngine`), and SpEL's `and` / `or` coerce each operand with the conversion service — `StringToBooleanConverter` only accepts `true/false/on/off/yes/no/1/0`, so a diagnosis string throws. Write one expression with explicit comparisons instead: `th:if="${a != null and b != null}"`. A *single* `${someString}` in `th:if` is fine — Thymeleaf applies its own null/empty rules to the finished value; it is only the operators that are strict. Same for a ternary: `th:text="${x} != null ? ... "` is parsed as a value followed by literal text, so the whole thing must live inside the braces.

In email templates this is invisible until it reaches a real inbox — `EmailServiceImpl` swallows the render exception. `MedicalRecordMailTemplateTest` renders `email/medical-record-ready.html` with a `SpringTemplateEngine` for exactly that reason; a bare `new TemplateEngine()` would use **OGNL**, which is laxer *and* is not even on the classpath (`NoClassDefFoundError: ognl/PropertyAccessor`).

**`d-flex` on the patient nav `<ul>` disables the theme's whole mobile menu.** Bootstrap emits `.d-flex{display:flex!important}`, and the MediTrust theme hides the menu below 1200px with `.navmenu ul{display:none}` (`assets/css/main.css:387`) — `!important` wins, so on **every one of the 22 patient pages** the menu stayed expanded as a `position:absolute` panel covering the page and the hamburger did nothing. Use **`d-xl-flex`**: it applies only from 1200px, exactly matching the theme's `max-width:1199px` breakpoint, so below it the theme takes over again. The same trap waits for any Bootstrap display utility put on an element the theme also wants to hide responsively.

**Theme sắp xếp header mobile bằng `order`, và nó gọi tên một class dự án KHÔNG dùng.** Dưới 1200px `main.css:214-238` đặt `.header .logo{order:1}`, `.header .btn-getstarted{order:2}`, `.header .navmenu{order:3}`. Header bệnh nhân của dự án không có `.btn-getstarted` — nhóm nút bên phải là `.header-actions`, không khai `order` nên nhận mặc định **0**, tức xếp **trước cả logo**: trên điện thoại hai nút nằm bên trái còn "HealCare" bị đẩy vào giữa. `responsive.css` trả nó về khe số 2. **Bất kỳ phần tử nào thêm mới vào `.header-container` cũng phải tự khai `order` trong dải <1200px**, nếu không nó sẽ nhảy lên đầu hàng — mặc định 0 luôn đứng trước mọi `order` dương của theme.

**Khách chưa đăng nhập phải thấy một nút CÓ CHỮ, không phải icon người.** `user/include/header.html` render `.btn-header-login` ("Đăng nhập", viền teal) dưới `sec:authorize="!isAuthenticated()"`. Trước đây chỗ đó là một `bi-person-circle` trần cỡ `fs-5`: icon người được đọc là "hồ sơ của tôi" — thứ mà khách chưa đăng nhập không có — nên đường vào tài khoản coi như bị giấu. Dùng **viền** chứ không nền đặc vì nó đứng cạnh "Đặt lịch khám", CTA chính của trang.

**Luật thu gọn header dưới 400px phải bám class cụ thể, không bám `.btn`.** `responsive.css` nuốt chữ nút đặt lịch (`font-size:0`) rồi vẽ lại icon lịch bằng `::before`. Luật đó viết khi header chỉ có đúng một nút; bám `.btn` thì mọi nút thêm sau — kể cả "Đăng nhập" — cũng bị nuốt chữ và mọc ra icon lịch, tức giả dạng thành nút đặt lịch thứ hai. Nay nó bám `.btn-header-book`, còn `.btn-header-login` **giữ nguyên chữ** (chỉ ẩn icon + siết đệm) vì chữ chính là lý do nút đó tồn tại.

**Exactly one place may bind `.mobile-nav-toggle`, and it is `assets/js/mobile-nav.js`.** The block was removed from `assets/js/main.js` because only 12/23 patient pages load that file (it calls `new PureCounter()`, `GLightbox(...)` and `scrollTop.addEventListener` unconditionally, so it cannot simply be added to the rest). `mobile-nav.js` is loaded from `user/include/header.html`, next to the bell script, so it covers all of them. Binding in both places means one tap runs `toggle()` twice — menu opens and shuts instantly — the same shape as the double-Bootstrap bug above.

**Responsive fixes go in the two overlay stylesheets, never into the theme files.** `assets/css/responsive.css` (patient, loaded after `main.css` in all 23 heads) and `assets-admin/css/responsive-admin.css` (staff, loaded from the two `pagehead` fragments) — `main.css` and `assets-admin/css/style.css` are BootstrapMade theme files and a theme upgrade would swallow anything written into them. **Two exceptions, both forced by cascade order:** rules for the AI chat widgets live in the `<style>` of each `ai-chat*` fragment, and rules for the calendar grid live at the end of `work-schedule.css` — those `<style>` blocks sit in `<body>`, i.e. *after* the `<head>` links, so at equal specificity they win and an overlay file cannot reach them.

**Staff tables no longer need a hand-written `.table-responsive` wrapper.** `assets-admin/js/responsive-admin.js` wraps every unwrapped `<table>` on `window.load` — after `main.js` has initialised simple-datatables, so a `.datatable` gets its whole `.datatable-wrapper` wrapped rather than the bare table. This covers the 19 templates that never had one. It runs only in the staff area; patient pages still wrap by hand.

**A plain `<!-- -->` comment is sent to the browser; `<!--/* */-->` is not.** Thymeleaf strips only the second form. Design notes left in the first form ship in the page source for anyone to read, and inside a `th:each` they are **repeated once per row** — which also makes them show up in any `grep -c` you run against the rendered HTML while verifying a change, so a badge you correctly hid still looks present. Use the parser-level form for anything addressed to developers rather than to the reader.

**Pass Vietnamese strings to JS through `th:attr`, never `th:onclick`.** Vietnamese copy is full of apostrophes ("bác sĩ nào cũng được"), and inlining such a string into an `onclick` breaks the whole script; an attribute (`data-ai-prompt`) is escaped by Thymeleaf and read back with `dataset`. The 8 dashboard insight boxes do it this way.

**A clickable element nested inside `<a>` needs a delegated listener with `preventDefault()`.** Two doctor-dashboard stat cards wrap their whole body in `<a>`; without it, one tap both runs the handler and navigates away, so the handler's effect is never seen.

**`doctor/include/header :: header-nav` is shared by 13 templates** — every doctor, staff and head page plus `user/medical-record-detail.html`. It is the right place for anything that must appear app-wide for staff (the notification bell lives there, with its `<script>` next to its markup so no page has to opt in), but the patient page means role-specific items need `sec:authorize`. It is also **not** covered by `work-schedule.css`, so build shared header widgets from plain Bootstrap classes.

**`admin/include/header :: pagehead` được 31 template dùng chung, không phải 17.** Ngoài các trang
admin còn có 6 trang doctor, 4 trang head, 3 trang staff và `user/medical-record-detail.html` — một trang
**bệnh nhân** nhìn thấy. Nên lớp phủ giao diện khu admin (`assets-admin/css/admin-theme.css`, nạp giữa
`style.css` và `responsive-admin.css`) **bọc toàn bộ luật dưới `body.admin-theme`**, và class đó chỉ được
gắn vào `<body>` của 17 tệp `admin/*.html`. Bỏ lớp bọc là restyle luôn trang của bệnh nhân. Muốn mở rộng
phong cách sang khu bác sĩ thì thêm class vào các template đó, **không** phải gỡ lớp bọc.

Đây là tệp phủ **thứ ba**, cùng luật với hai tệp kia: `main.css` và `assets-admin/css/style.css` là tệp
theme vendored (BootstrapMade), sửa vào đó là một lần nâng cấp theme nuốt sạch.

**`record` không dùng được cho DTO mà Thymeleaf phải đọc.** SpEL `ReflectivePropertyAccessor` tìm
`getX()` / `isX()`, không tìm accessor kiểu record `x()`, nên `${dash.money.gross}` sẽ ném lỗi giải
biểu thức chứ không im lặng trả rỗng. Dùng Lombok `@Getter` — cũng là khuôn sẵn có của mọi DTO khác
trong dự án. Record vẫn hoàn toàn ổn cho thứ chỉ đi lại trong Java (`ReviewService.RatingStats`).

**Trạng thái điều hướng của khu admin do `config/AdminNavInterceptor` bơm, không phải từng controller.**
Ba điều bắt buộc, mỗi điều vá một cách hỏng khác nhau:

- **Mọi thuộc tính nó đặt đều mang tiền tố `nav`.** `AbstractView.createMergedOutputModel` trộn FlashMap
  vào model **trước** model của handler, nên một thuộc tính trùng tên sẽ **nuốt** flash message. Mà
  `RedirectAttributes` là kênh báo lỗi duy nhất của ứng dụng này (không có `@ControllerAdvice` nào) —
  đặt trùng `errorMessage`/`successMessage` là mất sạch thông báo trên mọi redirect admin, không một
  dòng log.
- **Dùng `postHandle` + chốt `mv == null || viewName.startsWith("redirect:")`, không dùng
  `@ControllerAdvice`.** `AdminCandidateController.downloadCv` trả `ResponseEntity<Resource>`; một
  phương thức `@ModelAttribute` sẽ chạy các câu `COUNT` cho **mỗi lượt tải CV** rồi vứt kết quả đi.
- **Bọc `try/catch` quanh phần huy hiệu.** `@ModelAttribute` chạy *trước* handler nên một truy vấn hỏng
  ở đó là HTTP 500 cho **mọi** URL admin; `postHandle` chạy sau khi handler đã xong nên tệ nhất là mất
  cái huy hiệu chứ không mất trang.

**Một huy hiệu chỉ đáng tồn tại khi con số của nó có thể về 0 bằng thao tác của chính người nhìn thấy nó.**
Sidebar admin chỉ có hai: bài nháp chờ duyệt và ứng viên chờ phản hồi. Cố ý **không** badge số lịch hẹn
`PENDING`: từ khi có `PAY_AT_COUNTER`, `PENDING + UNPAID` là trạng thái nghỉ *bình thường* do **lễ tân**
xử lý, badge đó sẽ sáng vĩnh viễn và nhắc admin về việc của người khác. Số 0 thì không render — một chấm
xám ghi "0" chỉ kéo mắt tới chỗ không có gì để xem.

## Không cho bấm, thay vì cho bấm rồi báo lỗi

Khi một thao tác không còn hợp lệ — nhất là vì **dữ liệu đã thuộc về quá khứ** — giao diện phải **không render nút/ô nhập** đó nữa, chứ không phải để người dùng điền xong rồi mới trả về `errorMessage`. Khuôn chung đã dùng khắp dự án:

1. Một hàm `whyCannot…(x)` trên service: trả `null` nếu còn thao tác được, ngược lại là **câu tiếng Việt** giải thích.
2. Controller gọi nó để **chặn thật** (POST/URL tự chế cũng không lách được).
3. Controller cũng đẩy kết quả xuống model (`Map<Long, String>` hoặc cờ boolean) để template **ẩn nút và in đúng câu đó**.

Nhờ đi qua **một** hàm duy nhất, giao diện và server không bao giờ nói khác nhau. Các hàm hiện có: `BookingService.whyCannotCancel` / `whyCannotReschedule` / `whyStaffCannotChange`, `ReceptionService.whyCannotReorderQueue`, `AllergyService.whyCannotDelete`, `LeaveService.whyCannotDecide`, `StaffScheduleService.whyCannotDecideShift`, `UserService.whyCannotDelete`, `BookingService.whyCannotBookWithoutPayment`. **Thêm luật mới thì thêm vào đúng hàm này, đừng viết `if` rời trong controller.**

Với ô `<input type="date">`, cách rẻ nhất là `th:min` / `th:max` từ một thuộc tính model (`today`, `emergencyMaxDate`): modal đăng ký ca trực, đơn nghỉ phép, báo bận đột xuất và phân công trực của trưởng khoa đều đã có. **Nhưng `min`/`max` chỉ là lớp lịch sự** — nó tính lúc **tải trang**, nên một tab mở từ hôm qua vẫn gửi lên ngày cũ; luật thật vẫn phải nằm ở server (`validateShift`, `LeaveServiceImpl.validate`, `reserve()`).

Ngoại lệ hợp lệ: **màn hình tra cứu**. Bộ lọc ngày của `/receptionist/queue` và `/receptionist/schedule-change` **không** đặt `max`, vì lễ tân vẫn cần xem lại ngày cũ — chỉ khóa các nút *thao tác*, không khóa việc *xem*.

## Tải tệp lên — chỉ một cửa

**`service/FileStorageService` là nơi DUY NHẤT được ghi tệp người dùng tải lên.** Trước đây bảy chỗ lặp lại đúng bốn dòng `new File(dir).mkdirs()` + `Files.write(Paths.get(dir + millis + "_" + getOriginalFilename()))`, và cùng mang đúng ba lỗi: đường dẫn tương đối theo CWD của tiến trình; hai chỗ ghi thẳng vào cây **mã nguồn** (`src/main/resources/static/...`, không tồn tại trong jar, và ngay ở dev thì tài nguyên tĩnh cũng phục vụ từ `target/classes` nên tệp ghi ra không bao giờ là tệp đọc lại); và dùng thẳng `getOriginalFilename()` — dữ liệu do client gửi, một tên chứa `..\..\` ghi được ra ngoài thư mục upload.

Thêm chỗ ghi mới thì gọi service, đừng viết lại bốn dòng đó. Chi tiết thư mục ở [environment-setup.md](environment-setup.md).

## Secrets
Mọi bí mật nay lấy từ **biến môi trường** qua dạng `${BIEN:mặc-định-dev}` trong [application.properties](src/main/resources/application.properties); `VnPayProperties` và `VietQrProperties` thay cho các hằng số `public static` cũ trong `VNPayConfig`. Xem [environment-setup.md](environment-setup.md).

**Giá trị mặc định đang commit là giá trị DEV, và cả năm bí mật bên ngoài đều đã nằm trong lịch sử git** — đưa ra biến môi trường không thu hồi được chúng. Phải xoay (rotate) trước khi mở cho người dùng thật: mật khẩu MySQL, Gmail app password, Google + Facebook client secret, OpenRouter key. Cặp VNPay sandbox thì giữ được. Checklist ở [deploy/README.md](../../deploy/README.md).

**Đừng gắn `@Value` lên setter tĩnh** để nạp các hằng số đó — mìn thứ tự khởi tạo, xem `VnPayProperties`.

## Browser APIs (giọng nói)
The voice layer relies on browser-only APIs, so anything touching it must degrade rather than break:

- `SpeechRecognition` exists **only in Chrome/Edge** (Firefox has none) and **only in a secure context** — `https://` or `http://localhost`. On plain HTTP over a LAN IP it fails silently. `MediTrustVoice.isSupported()` gates every entry point; when false the mic/call buttons are never inserted and the typing chat is untouched.
- Never use regex lookbehind `(?<=…)` in these files. Older Safari treats it as a **parse error** and discards the whole script, which would also kill the graceful-degradation path. **Lookahead `(?!…)` is fine** and is used deliberately — `normalizeTimeHint` needs `h(?![\p{L}])` (with the `u` flag) so the "h" of "hôm"/"hoặc" is not read as "giờ".
- `\b` is ASCII-only, so it can never match a Vietnamese word with diacritics. Use the space-padded idiom (`' ' + text + ' '` then `indexOf(' từ ')`) that `extractSessionHint` and `resolveAlternativeChoice` use. Accent-insensitive matching goes through `stripDiacritics()` — but **only for proper names**: applied to a sentence it turns "sáng" into the preposition "sang" and "tôi" into "toi".
- **Wrap every `sessionStorage` / `localStorage` call.** Reading throws `SecurityError` in private mode or when third-party storage is blocked; if that happens during `DOMContentLoaded` the rest of the script never runs and the feature is silently dead. `ai-chat.js` routes everything through its `safeStorage` helper, which also trims oversized values instead of letting `QuotaExceededError` escape.
- `window.speechSynthesis` is a read-only accessor — it cannot be stubbed with plain assignment, only `Object.defineProperty` (relevant when writing browser tests).
- Chrome quirks already worked around in `MediTrustVoice.speak()`: `getVoices()` is empty until `voiceschanged`, and the TTS queue stalls after ~15s (a `pause()`/`resume()` keep-alive ticks every 10s, but **only for content over 150 chars** — that pause/resume can clip mid-word, and a short reply finishes long before the watchdog).

  **The real limit is ~15 SECONDS, not N characters, and confusing the two made the assistant sound broken.** `MAX_CHUNK_CHARS` was 180 (~8s at rate 1.5), so an ordinary schedule answer (~230 chars) was split into two `SpeechSynthesisUtterance`s — and the browser always inserts a real gap between queued utterances. Because `splitIntoChunks` can only break at `.!?`, that gap landed exactly on a full stop and sounded like the assistant freezing at every period. It is now 300 (~13s), leaving the 10s keep-alive as the actual guard; raising it further starts betting against the watchdog.
- **`joinSentences()` rewrites mid-text `". "` into `", "` before speaking**, because a Vietnamese voice pauses far longer at a full stop than at a comma — and `toSpeechText` *manufactures* extra full stops by turning every `<br>` and every closing `</p>/</div>/</li>` into `". "`, so a card built from several `<div>`s was read as a string of disconnected fragments. It runs inside `speak()` rather than `toSpeechText()` so it also covers `raw: true` (the hand-written Vietnamese in the voice-call module). Three things it must never touch: the **final** period (the sentence needs somewhere to drop pitch), `?` and `!` (the assistant always closes on a question — losing that intonation means the patient doesn't realise they were asked), and a period with **no whitespace after it**, which is what keeps the Vietnamese thousands separator in `350.000 đ` intact.

## Error handling
Controllers largely catch broad `Exception`, call `printStackTrace()`, and surface a message through `RedirectAttributes` flash attributes. There is no global `@ControllerAdvice`; custom error pages exist at `templates/error/{403,404,500}.html`.

## Testing & tooling
**There are two test classes**: `MedicalRecordMailTemplateTest` (renders the medical-record email template — see the SpEL note above) and `PdfFontTest` (the embedded PDF fonts). Both exist for the same reason and it is the reason worth copying: **each guards a failure that the app swallows into a log line**, where "no error on screen" and "working" look identical. Everything else is untested, and there is no linter or formatter configured beyond the Maven compiler. Do not claim tests pass without actually adding and running them.

Two things about running them:
- Prefer **plain JUnit over `@SpringBootTest`** where possible. Booting the context needs a live MySQL (`ddl-auto=update`) and fires `DataInitializer`, so a context test is a migration against the dev database, not a unit test.
- **The first `./mvnw test` must run online.** `maven-surefire-plugin` resolves its `surefire-junit-platform` provider lazily, so `-o` fails with "artifact has not been downloaded from it before" until one online run has cached it.
