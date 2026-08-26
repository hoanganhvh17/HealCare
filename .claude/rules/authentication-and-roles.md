# Authentication & Role Model

[SecurityConfig.java](src/main/java/com/bookinghealthy/config/SecurityConfig.java) is the **single source of truth** for URL authorization. Five roles drive the whole app: `ROLE_ADMIN`, `ROLE_DOCTOR`, `ROLE_HEAD_DOCTOR` (trưởng khoa), `ROLE_RECEPTIONIST` (lễ tân), `ROLE_USER` (patient).

## URL-to-role mapping
- `/actuator/health` → `permitAll` (block 0), rest of `/actuator/**` → ADMIN. Without the first line every nginx/systemd/docker healthcheck got a 302 to `/login` and the instance was marked unhealthy and restarted in a loop. **Never expose `/actuator/env` or `/actuator/configprops`** — they print the very secrets that were just moved into environment variables.
- `/api/payment/webhook` → `permitAll` (block 0); everything else under `/api/payment/**` → authenticated. The webhook is called server-to-server by Casso/SePay so it has no session; it is authenticated by a **shared secret in a header** inside `VietQRController`, not by Spring Security. The whole prefix used to be `permitAll`, which also opened `/api/payment/check-status` — an endpoint taking an arbitrary `?id=`.
- `/checkout-qr` → authenticated (block 0) **plus an ownership check in the controller**. It was `permitAll` with only a `paymentStatus == UNPAID` guard, so any anonymous visitor could walk `?id=1,2,3…` and read another patient's name, price, doctor and appointment time.
- `/admin/**` and `/api/admin/chat/**` → ADMIN
- `/doctor/**` and `/api/doctor/chat/**` → DOCTOR
- `/head/**` → HEAD_DOCTOR
- `/receptionist/**` → RECEPTIONIST
- `/api/staff/**` → any authenticated user (the controller filters by the logged-in user)
- `/api/notifications/**` → any authenticated user (patient notification bell; `UserNotificationApiController` filters by the logged-in user). Declared in **block 0** even though `anyRequest().authenticated()` would already cover it — block 0 is where every constrained `/api/...` rule must be visible.
- `/api/chat/my-documents` → authenticated. **Cùng một lý do và cùng một chỗ với dòng ngay dưới**: hồ sơ bệnh án ngoại viện bệnh nhân tự tải lên. Khai dưới `/api/chat/**` là giao dữ liệu sức khoẻ cho bất kỳ ai gọi API.
- `/api/chat/hold-slot` → authenticated (khối 0). Endpoint giữ chỗ 3 phút cho một khung giờ; nằm dưới `/api/chat/**` (permitAll) nên trước đây khách vô danh gửi `sessionId` tuỳ ý là làm chatbot báo "đang có người giữ chỗ" cho cả lịch. Chỉ người đã đăng nhập mới đặt được lịch nên siết ở đây không mất chức năng nào.
- `/careers`, `/career-details/**`, `/career-apply` → `permitAll`. Thiếu ba dòng này (tới 2026-08-25) thì khách vãng lai bấm "Tuyển dụng" bị đá sang `/login` — cả tính năng vô hình với đúng đối tượng nó phục vụ.
- `/api/chat/my-bookings` → authenticated. Must sit in **block 0 above the `permitAll` list**, exactly like `/api/chat/medical-record/**`: `/api/chat/**` is whitelisted below, and Spring takes the first matching rule, so omitting this line serves a patient's appointment list to anonymous callers. The rest of `PatientChatLookupApiController` (`/doctor-profile`, `/doctors/filter`) is public data and stays in the whitelist.
- Public pages (home, doctors, services, departments, news, `/api/chat/**`, `/api/bookings/booked-slots`, payment webhooks) are explicitly `permitAll`
- Patient account pages (`/appointment`, `/user/profile`, `/user/change-password`, `/user/review/**`, `/user/booking/**`, `/user/allergy/**`) are `authenticated``/user/allergy/**`, `/user/medical-document/**`) are `authenticated`

**When adding a route, add it to the correct matcher block.** Anything not whitelisted falls through to `anyRequest().authenticated()` and will silently redirect anonymous visitors to the login page.

## `@EnableMethodSecurity` — thiếu nó thì `@PreAuthorize` là chú thích chết

Khai trên `SecurityConfig` từ 2026-08-25. Trước đó **không tồn tại ở bất kỳ đâu trong dự án**,
nên mọi `@PreAuthorize` chỉ là chữ: chúng không chặn gì cả và **không có cảnh báo nào** lúc
biên dịch lẫn lúc chạy.

Hậu quả thật: `DoctorAssistantController` khai `@PreAuthorize("hasRole('DOCTOR')")` nhưng
`/api/doctor/assistant/*` lại rơi vào luật `permitAll` của `/api/doctor/**` — khách vô danh
`POST /api/doctor/assistant/chat` là gọi được OpenRouter **bằng API key của bệnh viện**. Một
vòng lặp đủ cạn credit và kéo theo toàn bộ chatbot bệnh nhân + 4 trợ lý form khám chết theo.
Cả nhánh đó hoá ra là **mã chết** (tệp JS gọi nó không template nào nạp) nên đã bị xoá hẳn
thay vì vá.

**Luật URL vẫn là nguồn sự thật chính**; `@EnableMethodSecurity` chỉ để annotation thôi nói
dối. Và `"/api/doctor/**"` đã thu hẹp thành **`"/api/doctor/{id:[0-9]+}"`** — luật rộng đó
tồn tại chỉ để mở `/api/doctor/{id}` của `DoctorApiController`.

## Matcher order is load-bearing
Spring Security applies the **first matching rule**, so a narrow rule must be declared **above** any broader one that would also match. Block 0 at the top of `filterChain` exists for exactly that: `/api/doctor/chat/**` and `/api/admin/chat/**` are declared there, *before* the `permitAll` list.

Both were previously declared further down and were dead. `/api/doctor/**` sat in the `permitAll` block to open `/api/doctor/{id}`, but `DoctorAiController` is `@RequestMapping("/api/doctor/chat")`, so the whole doctor AI assistant fell inside that whitelist and the `hasRole("DOCTOR")` line below never ran — anonymous callers could read patient names, diagnoses and schedules. **Put any new restricted `/api/...` rule in block 0.**

The mirror-image trap: `/api/doctor/**` does **not** cover `/api/doctors/search`, because AntPathMatcher compares whole path segments and `doctor` ≠ `doctors`. That endpoint therefore fell through to `anyRequest().authenticated()`, so the AI assistant's doctor-name lookup 302'd to `/login` for anonymous patients and silently degraded to the `/api/doctors` fallback. It is now covered by `/api/doctors/**`.

## Login flows
- Form login **plus OAuth2** (Google and Facebook).
- A custom `successHandler` redirects by role: ADMIN → `/admin/dashboard`, DOCTOR → `/doctor/dashboard`, RECEPTIONIST → `/receptionist/dashboard`, **HEAD_DOCTOR → `/head/dashboard`**, USER → `/`. `OAuth2LoginSuccessHandler` mirrors the same branches — update both together. The **HEAD_DOCTOR branch is checked *after* DOCTOR** on purpose: a seeded head doctor keeps `ROLE_DOCTOR` and so still lands on `/doctor/dashboard`; the branch only catches a **head-doctor-only** account (e.g. one an admin promoted without `ROLE_DOCTOR`), which would otherwise fall through to `/` and look like a patient.
- OAuth2 path: `CustomOAuth2UserService` + `OAuth2LoginSuccessHandler` + provider-specific implementations of `OAuth2UserInfo` under `security/userinfo/`.
- Local logins go through `CustomUserDetailsService`. `User.authProvider` distinguishes `LOCAL` from social accounts.
- Passwords use BCrypt, with the `PasswordEncoder` bean defined in `AppConfig` (not in `SecurityConfig`).

## Trưởng khoa (ROLE_HEAD_DOCTOR)
A head doctor is normally a **doctor with an extra role**, not a separate account type — a seeded head keeps `ROLE_DOCTOR` and lands on `/doctor/dashboard`, reaching `/head/**` via the "Phê duyệt của khoa" item the doctor sidebar reveals with `sec:authorize="hasRole('HEAD_DOCTOR')"`. But an admin can also grant `ROLE_HEAD_DOCTOR` on its own; such an account has no doctor sidebar, so the `successHandler` HEAD_DOCTOR branch (above) sends it straight to `/head/dashboard`, whose own sidebar (`head/include/sidebar`) then self-navigates.

The role only opens `/head/**`; **which department they lead comes from `StaffProfile.headOfDepartment`**, resolved by `CurrentUserService.resolveHeadDepartment`. A head doctor with no such row (role granted but no department assigned) sees an explanatory message rather than another department's data — so granting the role alone is not enough. The **admin user form (`/admin/manage-user`) assigns it**: ticking Trưởng khoa reveals a "Khoa phụ trách" dropdown, and `AdminController.syncHeadOfDepartment` writes/creates the `StaffProfile.headOfDepartment` on save (and clears it when the role is unticked). Seeded heads get theirs from `DataInitializer.ensureHeadDoctors` (most-experienced doctor per department). The admin list renders all five roles (`manage-user.html`); before the fix `ROLE_HEAD_DOCTOR` and `ROLE_RECEPTIONIST` both showed as "Bệnh nhân".

## Resolving the current user
The principal may be either a `UserDetails` (form login) or an `OAuth2User` (social login). Prefer [CurrentUserService](src/main/java/com/bookinghealthy/service/CurrentUserService.java), which encapsulates the username→email fallback and also resolves the user's `Doctor`, department, and head-of-department. The older inline `getCurrentUser` in [BookingController.java](src/main/java/com/bookinghealthy/controller/user/BookingController.java) does the same thing; never assume one principal type.

## Seeding roles
`DataInitializer` only seeds when the `users` table is empty, so a role added later would never appear on an existing dev database. `ensureReceptionistAccount()` runs **outside** that guard and creates `ROLE_RECEPTIONIST` plus the `receptionist`/`123456` account idempotently. **Follow this pattern for any future role** — and keep the block after the `if`, since creating a user first would make `count() == 0` false and skip the whole seed.

## Sessions live in MySQL, not in Tomcat memory
`spring-session-jdbc` backs the session store (`spring.session.store-type=jdbc`; tables in `db/manual/002_spring_session.sql`). The app never touches `HttpSession` directly, but three things live in it and all three break on failover or restart without a shared store:

- Spring Security's `SecurityContext` (every logged-in user);
- the OAuth2 `state` / `OAuth2AuthorizationRequest` — a failover mid-redirect surfaces as `authorization_request_not_found`, which looks like a broken Google login button;
- the **FlashMap** behind every `RedirectAttributes` — and that is this app's *only* error-reporting channel, since there is no `@ControllerAdvice`.

Chosen over Redis because it reuses the existing MySQL, and over nginx `ip_hash` sticky sessions because those still log everyone out on a rolling deploy and fail for users behind carrier NAT.

Two details that bite: `ATTRIBUTE_BYTES` is **`MEDIUMBLOB`, not `BLOB`** — 64KB is reachable and MySQL *truncates*, surfacing as random logouts. And the session cookie is **`SameSite=lax`, never `strict`**: strict withholds the cookie on cross-site top-level navigation, which breaks both the OAuth2 callback and VNPay's redirect back to `/payment-return`.

### The principal must hold plain strings, never an entity
Anything stored in the session must be `Serializable`, **including the whole graph reachable from the principal** — and this is not theoretical: switching the store to JDBC turned *every* login into an HTTP 500 with `NotSerializableException: com.bookinghealthy.model.User`, thrown by `JdbcIndexedSessionRepository.serialize` while writing `SPRING_SESSION_ATTRIBUTES`. `CustomUserDetails` held the `User` entity; in Tomcat's in-memory store nothing ever serialized it, so the defect was invisible for the whole life of the project and surfaced the moment the store changed.

Both principals now flatten at construction time — same rule as `MedicalRecordMailDTO`: data crossing a boundary (another thread, or serialization) is plain strings, not entities. **Adding a field means reading its value in the constructor**; keeping the `User` around "to read later" puts the entity straight back into the session.

Three things that make this trap hard to see, all worth keeping in mind:

- **The compiler cannot warn you.** `UserDetails` already extends `Serializable`, so `CustomUserDetails` always *promised* to be serializable while a field silently broke the promise. Only the field types matter.
- **`OAuth2User` does NOT extend `Serializable`** — unlike `UserDetails`. Spring declares it by hand on its own `DefaultOAuth2User`, so `CustomOAuth2User` must declare it explicitly too. Drop that keyword and social login breaks again, with no compiler complaint.
- **`CustomOAuth2User.getAttributes()` must keep the provider's raw map.** Seven call sites identify the user with `principal.getAttribute("email")` (`AiController` ×3, `DoctorAiController`, `DoctorExamAiController`, `PatientChatLookupApiController`, `BookingController`, `ProfileController`, `CurrentUserService`). Flattening it away makes every one of them read `null`, and a Google user silently becomes an anonymous visitor — no exception anywhere. Only `OAuth2UserInfo` is reduced to three strings, because it is the one part that is neither serializable nor read for anything but name/email/avatar.

`User.roles` being `@ManyToMany` is a second reason to flatten: holding the entity drags a Hibernate `PersistentSet` into the session, which is both unserializable and frozen stale until the user logs out.

**Changing the shape of either principal invalidates every stored session** — old rows deserialize into the new class and fail. Clear `SPRING_SESSION_ATTRIBUTES` then `SPRING_SESSION` when you do (it just forces everyone to log in again).

Verified end-to-end from the packaged jar: all four roles land on their correct dashboard, the session round-trips through MySQL (`SPRING_SECURITY_CONTEXT` present in `SPRING_SESSION_ATTRIBUTES`), `principal.fullName` / `principal.avatar` still render, and `/api/notifications` + `/api/chat/my-bookings` still resolve the user. **Google and Facebook login still need a real browser test** — the fix above is the same defect on both paths, but only the form path could be exercised from the CLI.

## Gotcha
**CSRF is ON** (since 2026-08-22 — it was globally disabled for the whole life of the project before that). The token lives in a **cookie**, not the session: `CookieCsrfTokenRepository.withHttpOnlyFalse()`. Two rules for anything you add:

- **A new Thymeleaf form needs nothing** — the `th:action` processor injects the hidden `_csrf` input. But it is `th:action` **on the `<form>` element** that does it: `th:formaction` on a `<button>` does **not**, and a form whose action is assigned by JS gets nothing either. Both shapes exist in this repo and both had to be fixed by hand; see [coding-conventions.md](coding-conventions.md).
- **A new `fetch` POST must send the token**: `headers: MediTrustCsrf.headers({...})` from `assets/js/csrf.js`. It reads the `XSRF-TOKEN` cookie fresh on every call (Spring issues a **new** token after login, so a value cached at page load dies at the next sign-in).

### The two Spring Security 6 traps, both silent

- **Deferred token loading.** Spring 6 only writes the cookie when the token is actually *read*. A page that renders no form — the patient home page with just the chat widget is the exact case — would never issue one, and its first `fetch` POST 403s. `config/CsrfCookieFilter` touches `getToken()` on every request to force it. **Do not remove it**; the failure appears only on the most static pages, i.e. the ones tested last.
- **XOR masking.** The default `XorCsrfTokenRequestAttributeHandler` masks the token per render (BREACH protection), while the cookie holds the **raw** value — so a token read from the cookie and sent as a header would be un-masked and rejected. `config/SpaCsrfTokenRequestHandler` routes by source: header → raw, form param → un-mask. Verified live: the form value is 96 chars, the cookie value is a 36-char UUID, both are accepted, and putting the raw value in the form param is correctly **refused**.

### Exactly one exemption

`.ignoringRequestMatchers("/api/payment/webhook")`. Casso/SePay call it server-to-server with no session and no cookie, so it cannot carry a token; it authenticates on a shared secret header (`VietQRController.isAuthorizedWebhook`). **`permitAll()` does not help** — `CsrfFilter` runs *before* the authorization layer. Forgetting this line stops real bank transfers from being recorded, silently.

### CSRF does not protect GET — so nothing may mutate on GET

Spring's `CsrfFilter` ignores GET/HEAD/OPTIONS/TRACE, and `SameSite=Lax` **does** send the session cookie on top-level GET navigation. So a state-changing `@GetMapping` is exploitable by simply sending someone a link, CSRF on or off. The project had **19** of them (delete user, delete booking, approve candidate, publish article — which also fanned a notification to every patient); all were converted to `@PostMapping` on 2026-08-22, with their 25 `<a>` links rewritten as inline POST forms.

**Never add a `@GetMapping` that writes.** GET is for reading; the `whyCannot…()` guards decide *whether* the button renders, `@PostMapping` decides *how* it is invoked.
