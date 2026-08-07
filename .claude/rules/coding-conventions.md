# Conventions & Gotchas

## Language
Prefer **Vietnamese** for user-facing strings, comments, and commit messages, to match the existing code. Commit messages follow a `feat:` / `fix:` prefix with a Vietnamese description, e.g. `fix: lỗi xung đột lịch với phương thức thanh toán bằng ví`.

## Templates
Thymeleaf templates live in `src/main/resources/templates/{user,admin,doctor,auth,email,error}/`, with shared fragments under each area's `include/` folder (`header`, `footer`, `sidebar`, `ai-chat`). Thymeleaf caching is disabled for development. Security-aware template expressions use `thymeleaf-extras-springsecurity6`.

**`${map[enumKey]}` silently renders empty.** Indexing a `Map` whose keys are an enum (e.g. `Map<DayOfWeek, Integer>`) produces nothing — no error, no log line, the page just loses every value while the surrounding markup renders fine. `Set.contains(enumValue)` and `enumValue.value` both work, which makes it look like the enum is fine. Pass an ordered `List` and index it with `th:each="x, iter : ..."` + `${list[iter.index]}` instead; `HeadRosterController.coverage`/`dayLabels` do exactly that and say why.

**Never load `bootstrap.bundle.min.js` twice on one page.** The footer fragments (`admin/include/footer`, `doctor/include/footer`) already load Bootstrap, simple-datatables, apexcharts, echarts, quill, tinymce and `main.js`; several pages re-declared those `<script>` tags after including the fragment. A second copy of Bootstrap registers its data-api click handler on `document` a second time, so **one click runs `toggle()` twice — the dropdown opens and closes instantly and looks completely dead**. That is what made the notification bell work on `/head/**` (one copy) but do nothing on `/doctor/dashboard` and `/doctor/manage-bookings` (two copies); every other dropdown, modal and dismissible alert on those pages was broken the same way. Include a footer fragment **or** hand-pick scripts, never both. `user/medical-record-detail.html` is the one legitimate exception and guards it: the footer only renders for DOCTOR/ADMIN, so the patient's copy carries `th:unless` on the same condition.

The mirror-image trap on the patient side is **zero** copies: `checkout-qr.html` and `payment-result.html` shipped without Bootstrap JS at all, so every dropdown in the shared header was inert there. Both now load exactly one copy.

**`~{user/index :: footer}` is a fragment that does not exist** — `user/index.html` declares no `th:fragment` whatsoever. Six templates referenced it (`career-details`, `careers`, `doctor-schedule`, `medical-process`, `payment-result`, `working-hours`), and Thymeleaf aborts the render at that tag: the page lost its footer **and every `<script>` declared after it**, which is where Bootstrap sits. That is why the notification bell rendered but would not open on those pages. The real fragment is `~{user/include/footer :: footer}`. Grep for the bad form before adding a footer to a new patient page.

**Pass Vietnamese strings to JS through `th:attr`, never `th:onclick`.** Vietnamese copy is full of apostrophes ("bác sĩ nào cũng được"), and inlining such a string into an `onclick` breaks the whole script; an attribute (`data-ai-prompt`) is escaped by Thymeleaf and read back with `dataset`. The 8 dashboard insight boxes do it this way.

**A clickable element nested inside `<a>` needs a delegated listener with `preventDefault()`.** Two doctor-dashboard stat cards wrap their whole body in `<a>`; without it, one tap both runs the handler and navigates away, so the handler's effect is never seen.

**`doctor/include/header :: header-nav` is shared by 13 templates** — every doctor, staff and head page plus `user/medical-record-detail.html`. It is the right place for anything that must appear app-wide for staff (the notification bell lives there, with its `<script>` next to its markup so no page has to opt in), but the patient page means role-specific items need `sec:authorize`. It is also **not** covered by `work-schedule.css`, so build shared header widgets from plain Bootstrap classes.

## Secrets
`VNPayConfig` holds **hardcoded static sandbox credentials**, and [application.properties](src/main/resources/application.properties) contains live-looking mail, OAuth, and AI keys. Treat all of these as **dev-only** and do not present them as production-safe. Flag it if the app is being prepared for deployment.

## Browser APIs (giọng nói)
The voice layer relies on browser-only APIs, so anything touching it must degrade rather than break:

- `SpeechRecognition` exists **only in Chrome/Edge** (Firefox has none) and **only in a secure context** — `https://` or `http://localhost`. On plain HTTP over a LAN IP it fails silently. `MediTrustVoice.isSupported()` gates every entry point; when false the mic/call buttons are never inserted and the typing chat is untouched.
- Never use regex lookbehind `(?<=…)` in these files. Older Safari treats it as a **parse error** and discards the whole script, which would also kill the graceful-degradation path. **Lookahead `(?!…)` is fine** and is used deliberately — `normalizeTimeHint` needs `h(?![\p{L}])` (with the `u` flag) so the "h" of "hôm"/"hoặc" is not read as "giờ".
- `\b` is ASCII-only, so it can never match a Vietnamese word with diacritics. Use the space-padded idiom (`' ' + text + ' '` then `indexOf(' từ ')`) that `extractSessionHint` and `resolveAlternativeChoice` use. Accent-insensitive matching goes through `stripDiacritics()` — but **only for proper names**: applied to a sentence it turns "sáng" into the preposition "sang" and "tôi" into "toi".
- **Wrap every `sessionStorage` / `localStorage` call.** Reading throws `SecurityError` in private mode or when third-party storage is blocked; if that happens during `DOMContentLoaded` the rest of the script never runs and the feature is silently dead. `ai-chat.js` routes everything through its `safeStorage` helper, which also trims oversized values instead of letting `QuotaExceededError` escape.
- `window.speechSynthesis` is a read-only accessor — it cannot be stubbed with plain assignment, only `Object.defineProperty` (relevant when writing browser tests).
- Chrome quirks already worked around in `MediTrustVoice.speak()`: `getVoices()` is empty until `voiceschanged`, utterances over ~200 chars get truncated (so text is chunked by sentence), and the TTS queue stalls after ~15s (a `pause()`/`resume()` keep-alive ticks every 10s).

## Error handling
Controllers largely catch broad `Exception`, call `printStackTrace()`, and surface a message through `RedirectAttributes` flash attributes. There is no global `@ControllerAdvice`; custom error pages exist at `templates/error/{403,404,500}.html`.

## Testing & tooling
**No test suite currently exists** — `src/test` contains no classes, though `spring-boot-starter-test` and `spring-security-test` are on the classpath. There is no linter or formatter configured beyond the Maven compiler. Do not claim tests pass without actually adding and running them.
