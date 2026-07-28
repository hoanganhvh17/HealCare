# Conventions & Gotchas

## Language
Prefer **Vietnamese** for user-facing strings, comments, and commit messages, to match the existing code. Commit messages follow a `feat:` / `fix:` prefix with a Vietnamese description, e.g. `fix: lỗi xung đột lịch với phương thức thanh toán bằng ví`.

## Templates
Thymeleaf templates live in `src/main/resources/templates/{user,admin,doctor,auth,email,error}/`, with shared fragments under each area's `include/` folder (`header`, `footer`, `sidebar`, `ai-chat`). Thymeleaf caching is disabled for development. Security-aware template expressions use `thymeleaf-extras-springsecurity6`.

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
