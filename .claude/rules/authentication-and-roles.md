# Authentication & Role Model

[SecurityConfig.java](src/main/java/com/bookinghealthy/config/SecurityConfig.java) is the **single source of truth** for URL authorization. Five roles drive the whole app: `ROLE_ADMIN`, `ROLE_DOCTOR`, `ROLE_HEAD_DOCTOR` (trưởng khoa), `ROLE_RECEPTIONIST` (lễ tân), `ROLE_USER` (patient).

## URL-to-role mapping
- `/admin/**` and `/api/admin/chat/**` → ADMIN
- `/doctor/**` and `/api/doctor/chat/**` → DOCTOR
- `/head/**` → HEAD_DOCTOR
- `/receptionist/**` → RECEPTIONIST
- `/api/staff/**` → any authenticated user (the controller filters by the logged-in user)
- `/api/notifications/**` → any authenticated user (patient notification bell; `UserNotificationApiController` filters by the logged-in user). Declared in **block 0** even though `anyRequest().authenticated()` would already cover it — block 0 is where every constrained `/api/...` rule must be visible.
- Public pages (home, doctors, services, departments, news, `/api/chat/**`, `/api/bookings/booked-slots`, payment webhooks) are explicitly `permitAll`
- Patient account pages (`/appointment`, `/user/profile`, `/user/change-password`, `/user/review/**`, `/user/booking/**`, `/user/allergy/**`) are `authenticated`

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

## Gotcha
**CSRF is disabled globally.** Keep this in mind for any form or API change; do not assume a CSRF token is present or required.
