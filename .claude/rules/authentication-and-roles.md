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
- `/api/chat/my-bookings` → authenticated. Must sit in **block 0 above the `permitAll` list**, exactly like `/api/chat/medical-record/**`: `/api/chat/**` is whitelisted below, and Spring takes the first matching rule, so omitting this line serves a patient's appointment list to anonymous callers. The rest of `PatientChatLookupApiController` (`/doctor-profile`, `/doctors/filter`) is public data and stays in the whitelist.
- Public pages (home, doctors, services, departments, news, `/api/chat/**`, `/api/bookings/booked-slots`, payment webhooks) are explicitly `permitAll`
- Patient account pages (`/appointment`, `/user/profile`, `/user/change-password`, `/user/review/**`, `/user/booking/**`, `/user/allergy/**`) are `authenticated``/user/allergy/**`, `/user/medical-document/**`) are `authenticated`

**When adding a route, add it to the correct matcher block.** Anything not whitelisted falls through to `anyRequest().authenticated()` and will silently redirect anonymous visitors to the login page.

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
**CSRF is disabled globally.** Keep this in mind for any form or API change; do not assume a CSRF token is present or required.

**This is an open security debt, not a design choice.** Every state-changing POST in the app is CSRF-able — wallet operations, `/user/booking/edit/{id}`, all admin CRUD. `SameSite=Lax` on the session cookie (above) withholds the cookie on cross-site POSTs in every current browser, which is most of the practical mitigation, but it is not the fix. Re-enabling CSRF across this many Thymeleaf forms and `fetch()` calls is **the first thing to do after deployment** — it was deliberately kept out of the deploy-hardening batch because doing it alongside a session-store swap and a security-matcher reshuffle is how a launch breaks.
