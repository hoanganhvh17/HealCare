# Conventions & Gotchas

## Language
Prefer **Vietnamese** for user-facing strings, comments, and commit messages, to match the existing code. Commit messages follow a `feat:` / `fix:` prefix with a Vietnamese description, e.g. `fix: lỗi xung đột lịch với phương thức thanh toán bằng ví`.

## Templates
Thymeleaf templates live in `src/main/resources/templates/{user,admin,doctor,auth,email,error}/`, with shared fragments under each area's `include/` folder (`header`, `footer`, `sidebar`, `ai-chat`). Thymeleaf caching is disabled for development. Security-aware template expressions use `thymeleaf-extras-springsecurity6`.

## Secrets
`VNPayConfig` holds **hardcoded static sandbox credentials**, and [application.properties](src/main/resources/application.properties) contains live-looking mail, OAuth, and AI keys. Treat all of these as **dev-only** and do not present them as production-safe. Flag it if the app is being prepared for deployment.

## Error handling
Controllers largely catch broad `Exception`, call `printStackTrace()`, and surface a message through `RedirectAttributes` flash attributes. There is no global `@ControllerAdvice`; custom error pages exist at `templates/error/{403,404,500}.html`.

## Testing & tooling
**No test suite currently exists** — `src/test` contains no classes, though `spring-boot-starter-test` and `spring-security-test` are on the classpath. There is no linter or formatter configured beyond the Maven compiler. Do not claim tests pass without actually adding and running them.
