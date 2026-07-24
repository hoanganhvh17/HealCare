# AI Assistant (multi-persona, OpenRouter)

[AiService.java](src/main/java/com/bookinghealthy/service/AiService.java) calls an OpenAI-compatible endpoint (OpenRouter, configured via `ai.api.url` / `ai.api.key`) using the shared `RestTemplate` bean, with a **model fallback chain**: `openai/gpt-4o-mini` → `openrouter/free` → `google/gemini-2.0-flash-exp:free`. Each model is tried in order until one returns a choice.

## Patient triage (`chatWithMemory`)
A large Vietnamese system prompt constrains the model to return **strict JSON** with these keys:

`reasoning`, `ai_reply`, `speech_reply`, `suggested_prompts` (always 3), `recommended_departments` (array of department IDs), `is_emergency`, `patient_summary`, `booking_intent`, `booking_target`.

The frontend consumes these fields to surface doctor cards and auto-open the booking form, so **the schema is a contract** — changing key names requires updating the templates under `templates/*/include/ai-chat*.html`.

`speech_reply` is the spoken-aloud variant of `ai_reply` (≤2 sentences, no emoji/HTML/disclaimer) used by the voice layer. The prompt says "ĐÚNG 9 KEYS" in **two places** — keep both in step when adding or removing a key. The voice layer always falls back to `MediTrustVoice.toSpeechText(ai_reply)` when the model omits it, so a non-compliant model degrades rather than breaks.

Department IDs carry special meaning in the prompt: **21 = Cấp cứu (Emergency)** and **22 = Y học gia đình** (the safety fallback when symptoms are ambiguous). These are seeded IDs from `DataInitializer`; re-seeding into a different order would break the prompt's assumptions.

The live department list is injected into the prompt at request time from `DepartmentRepository`, so new departments become selectable without prompt edits.

## Memory
- Conversations persist in `AiChatSession` (`chatHistoryJson`), keyed by a `sessionCode`.
- Only the **last 6 messages** are sent to the model.
- `patient_summary` is re-extracted from prior assistant messages via regex and re-injected each turn as durable memory.
- If the session belongs to a logged-in `User`, the patient's most recent `COMPLETED` booking's `MedicalRecord` (diagnosis, symptoms, doctor notes) is also injected as context.

## Voice Agent (STT/TTS)
Speech runs **entirely in the browser** via the Web Speech API — no API key, no Maven dependency, no server endpoint. OpenRouter has no audio endpoint, so a server-side Whisper/TTS path would need a separate paid key.

- [meditrust-voice.js](src/main/resources/static/assets/js/meditrust-voice.js) — shared module (`window.MediTrustVoice`). `attach({inputId, sendBtnId, messagesId, botSelector})` adds a mic button and, via a **debounced MutationObserver**, a 🔊 button on each assistant bubble. It is wired into all three live chat widgets (patient, doctor, admin) and needs no changes to their render functions. Speaker clicks use event delegation so buttons restored from `sessionStorage` still work.
- `toSpeechText()` turns HTML/Markdown into speakable Vietnamese: strips tags/emoji/the ⚠️ disclaimer and rewrites machine strings — `"T5 24/07 (09:00 - 09:30)"` → *"thứ Năm ngày 24 tháng 7 (9 giờ đến 9 rưỡi)"*. It mirrors `translateDay()` in `AiController`, so changing that slot-label format breaks the spoken output.
- [meditrust-voice-call.js](src/main/resources/static/assets/js/meditrust-voice-call.js) — hands-free call mode, **patient only**. State machine `idle → listening → thinking → speaking → listening`, auto-submits after `SILENCE_MS` of silence (its own timer; Chrome's `onend` is unreliable for Vietnamese), restarts on `onend` with a burst guard, and drops to typing after 2 unclear turns.
- **Half-duplex is mandatory**: always stop recognition before `speak()`, restart only on `onend`. The Web Speech API does not cancel laptop-speaker echo, so listening while speaking makes the assistant answer itself in a loop.
- `ai-chat.js` exposes `window.MediTrustChat` (`sendMessage`, `openChat`, `sessionId`, `suppressAutoRedirect`, `onReply[]`). `onReply` fires **exactly once per turn including on error**, so the call module never hangs. While a call is active `suppressAutoRedirect` blocks the 900ms auto-redirect in `resolveBookingHandoff` so the patient can confirm by voice first.
- Emergency (`is_emergency`) turns the overlay red, offers `tel:115`, and **abandons the booking flow**. Booking is never created by voice — a confirmed "đồng ý" only opens the prefilled `/appointment` form. When anonymous, the target URL is stashed in `sessionStorage` and offered again after login (`window.MEDITRUST_IS_LOGGED_IN` is written by the Thymeleaf fragment).
- `parseYesNo()` returns `no` **only for a bare refusal**. Anything carrying an instruction ("đổi sang bác sĩ B", "chuyển sang chiều mai") returns `unknown` and is forwarded to the model — returning `no` there made the assistant re-ask "đổi bác sĩ hay đổi giờ ạ?" after the patient had already said which.
- Tuning constants: `DEFAULT_RATE = 1.5` in `meditrust-voice.js` (speaking speed, one place for the whole app) and `SILENCE_MS = 1100` in `meditrust-voice-call.js` (how long a pause means "done talking").

## Picking the right doctor
`resolveBookingHandoff()` must never silently substitute a doctor. Two guards enforce that, and both are load-bearing:

- `/api/chat/doctors/department/{id}` takes an optional **`doctorId`** that sorts the named doctor to the front *before* the `.limit(3)` truncation. Without it, asking for a doctor outside the department's first three silently booked the first one instead.
- `pickBestDoctorMatch()` matches on **word boundaries only** (exact full name → Vietnamese given name → all words present). The search API runs `LIKE %keyword%` on full names, so "bác sĩ B" matches nearly everyone; taking the first hit booked the wrong person. When nothing matches it returns `null`, and `resolveBookingHandoff` returns `{doctorNotFound: true, requestedDoctorName}` — both the chat widget and the voice layer must surface that instead of redirecting.

`extractDoctorName()` strips trailing filler ("đi", "nhé", "ạ", "cho tôi"). It anchors the strip to end-of-string rather than `\b`, because JavaScript's `\b` only understands ASCII letters and would never match a word ending in a diacritic.

## Picking the right time slot
Slot labels are `"T5 24/07 (10:00 - 10:30)"`, so a requested time must be compared against the slot's **start** via `slotStartTime()` — never with `indexOf`. `"10:30"` is a substring of both `(10:00 - 10:30)` (its *end*) and `(10:30 - 11:00)`, and the earlier slot comes first in the list, so substring matching silently booked the patient 30 minutes early. The same rule applies to the free/busy check against `/api/bookings/booked-slots`, whose entries are bare `"HH:mm - HH:mm"` ranges.

`normalizeTimeHint()` requires an explicit time marker (`h`, `:`, `giờ`, `rưỡi`) before treating a number as a time; a loose number in `"đặt lịch ngày 5/8"` is a date. It also resolves `"10 giờ rưỡi"` → `10:30` and afternoon phrasing (`"3 giờ chiều"` → `15:00`; hours 1–6 are PM unless the patient said "sáng", since the clinic opens at 07:30).

When the requested slot is not among the doctor's free slots, `resolveBookingHandoff` still falls back to the nearest one but sets `fallback: true` and echoes back `requestedTime`. **Both surfaces must announce that** — the chat card names the unavailable time, and the voice layer says it before asking for confirmation. A silent substitution is the same class of bug as booking the wrong doctor.

`lastHandoffDate` remembers the date of the last resolved handoff, because a correction turn ("đổi thành 10h30") carries no date and without one the real slot list cannot be queried. It is only reused when the model omits the date and the remembered date is not in the past.

## Xưng hô
The prompt's section 0 forces the assistant to call itself **"em"** and the patient **"anh/chị"** — never "bạn", "tôi", or "mình" — in both `ai_reply` and `speech_reply`. Every hardcoded Vietnamese string in the voice modules follows the same convention; keep new strings consistent.

## Other surfaces
Separate admin and doctor assistants exist: `AdminAiController`, `DoctorAiController`, `DoctorAssistantService`, with the `AiRule` entity for configurable rules.

## Housekeeping
A `@Scheduled` job (`0 0 2 * * ?`) purges guest chat sessions older than 7 days. Scheduling is enabled by `SchedulerConfig`; async support by `@EnableAsync` on the main application class.
