# AI Assistant (multi-persona, OpenRouter)

[AiService.java](src/main/java/com/bookinghealthy/service/AiService.java) calls an OpenAI-compatible endpoint (OpenRouter, configured via `ai.api.url` / `ai.api.key`) using the shared `RestTemplate` bean. `callModels()` is the single call site; it walks `FALLBACK_MODELS` (`openai/gpt-4o-mini` → `google/gemini-2.0-flash-exp:free`) until one returns a choice.

Four rules around that call, each of which was a real outage:

- **Only real model ids in the chain.** `openrouter/free` was in it and does not exist, so every turn paid for a guaranteed-failing round trip.
- **The `RestTemplate` bean has timeouts** (8s connect / 25s read, set in `BookingHealthyApplication`). `new RestTemplate()` waits forever.
- **`chatWithMemory` and `getConversationalResponse` are deliberately NOT `@Transactional`.** They call the network mid-method, so a transaction there pins a HikariCP connection (pool of 10) for the whole wait — a slow OpenRouter took the entire site down, not just the chat. It is safe because every query already happens *before* the call and only a `save` follows it; `spring.jpa.open-in-view` keeps lazy loading working. **If open-in-view is ever disabled, materialise the entity reads into Strings in a separate `@Transactional` method first.**
- **Failures must be logged with a reason.** `AiResponse` maps OpenRouter's `error` object because it returns **HTTP 200** with it on out-of-credit / rate-limit, so `getChoices()` was simply null and the fallback loop ran silently; the old `catch` printed only the model name. Everything now logs `[AI][sessionId]`.

`AiRequest.maxTokens` (1200) caps the reply — without it a long 9-key JSON could be truncated mid-string and the browser printed raw JSON into the chat.

`loadOrCreateSession()` catches the unique-key race on `sessionCode` and re-reads. Two parallel turns on one session both inserted, and the exception escaped the caller's try as an HTTP 500.

`AiController.askAi` rejects an empty prompt with 400, truncates at 2000 chars, and generates a `sessionId` when the client omits one (a null `sessionCode` violated `NOT NULL` and 500'd), returning it in the response.

## Patient triage (`chatWithMemory`)
A large Vietnamese system prompt constrains the model to return **strict JSON** with these keys:

`reasoning`, `ai_reply`, `speech_reply`, `suggested_prompts` (always 3), `recommended_departments` (array of department IDs), `is_emergency`, `patient_summary`, `booking_intent`, `booking_target`.

The frontend consumes these fields to surface doctor cards and auto-open the booking form, so **the schema is a contract** — changing key names requires updating the four scripts that parse the JSON (`assets/js/ai-chat.js`, `assets/js/meditrust-voice-call.js`, `assets-admin/js/ai-chat-doctor.js`, `assets-admin/js/doctor-ai-chat.js`). The templates under `templates/*/include/ai-chat*.html` only load those scripts and read no key themselves. The `/skills/ai-schema-change` skill carries the full checklist and the template→script mapping.

`speech_reply` is the spoken-aloud variant of `ai_reply` (≤2 sentences, no emoji/HTML/disclaimer) used by the voice layer. The prompt states the key count in **two places, worded differently** — "ĐÚNG 9 KEYS" (`AiService.java:78`) and "đủ 9 trường" (`AiService.java:100`) — so grepping for one misses the other; keep both in step when adding or removing a key. The voice layer always falls back to `MediTrustVoice.toSpeechText(ai_reply)` when the model omits it, so a non-compliant model degrades rather than breaks.

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
- `pickBestDoctorMatch()` matches on **word boundaries only** (exact full name → Vietnamese given name → all words present). The search API runs `LIKE %keyword%` on full names, so "bác sĩ B" matches nearly everyone; taking the first hit booked the wrong person. It has **three** outcomes and the middle one is load-bearing: a doctor object, `{ambiguous: true, candidates}` when several people match (a hospital with two doctors named Bình must **ask**, not pick the first), or `null`. `resolveBookingHandoff` turns those into `{doctorAmbiguous, candidates}` and `{doctorNotFound, requestedDoctorName}`; the chat widget and the voice layer must surface both instead of redirecting.

Matching runs through `stripDiacritics()` so "dat lich voi bac si binh" finds "Trần Văn Bình" — a large share of patients type without accents and previously could never name a doctor at all. **Only names go through it**: stripping accents from a whole sentence turns "sáng" into the preposition "sang" and "tôi" into "toi", the exact two traps documented under `extractSessionHint`.

`extractDoctorName()` strips trailing filler ("đi", "nhé", "ạ", "cho tôi"). It anchors the strip to end-of-string rather than `\b`, because JavaScript's `\b` only understands ASCII letters and would never match a word ending in a diacritic. Punctuation is stripped **before** the filler and again after — with the old order "bác sĩ Bình ạ." kept "bình ạ", since the `$` anchor could not see "ạ" behind the full stop. `NOT_A_DOCTOR_NAME` rejects the words that follow "bác sĩ" but name no one ("nào cũng được", "khác", "nữ", "chuyên khoa …", "trực hôm nay"), which otherwise reached the patient verbatim as *"Em chưa tìm thấy bác sĩ nào cũng được"*.

## Picking the right time slot
Slot labels are `"T5 24/07 (10:00 - 10:30)"`, so a requested time must be compared against the slot's **start** via `slotStartTime()` — never with `indexOf`. `"10:30"` is a substring of both `(10:00 - 10:30)` (its *end*) and `(10:30 - 11:00)`, and the earlier slot comes first in the list, so substring matching silently booked the patient 30 minutes early. The same rule applies to the free/busy check against `/api/bookings/booked-slots`, whose entries are bare `"HH:mm - HH:mm"` ranges.

`normalizeTimeHint()` requires an explicit time marker (`h`, `:`, `giờ`, `rưỡi`) before treating a number as a time; a loose number in `"đặt lịch ngày 5/8"` is a date. It also resolves `"10 giờ rưỡi"` → `10:30`, `"8h kém 15"` → `07:45`, and afternoon phrasing (`"3 giờ chiều"` → `15:00`; hours **1–5** are PM unless the patient said "sáng", since bookable hours are 07:30–11:30 and 13:30–17:30 — "6 giờ" is outside them either way).

Two anchors in it are load-bearing. The marker is `h(?![\p{L}])`, **never a bare `h`**: a bare `h` matches the h that opens the next Vietnamese word, so "đau bụng **3 hôm** nay" produced 15:00, "sốt **2 hôm** rồi" 14:00, and "**2 hoặc 3** ngày nữa" 14:00 — invented times that then went on to be booked. (Lookahead is fine here; only **lookbehind** is banned, see coding-conventions.md.) And the "sáng" test scans only the text right after the match, not the whole sentence: "đặt **4h chiều** mai, **sáng** nay tôi bận" was resolving to 04:00.

Section 1 of the prompt states the bookable hours and adds that **outside office hours only the on-call team is present and no appointment can be booked**. Keep that in step with `ALL_SLOTS` (see [booking-flow.md](booking-flow.md)) — if the model advertises hours the slot grid does not have, every such request dead-ends in the "khung giờ kín" branch.

## When the requested slot cannot be booked
`fallback: true` on the handoff means the patient asked for something they cannot have. That is **never** resolved silently:

- The auto-redirect is skipped entirely — the chat renders `buildSlotFullHtml()` instead of the "mở trang đặt lịch" card, and the voice layer asks an open question rather than a yes/no one (`awaitingConfirm` is always `null` here, so the answer goes back to the model — including when there are no options left, where the handoff carries **no slot at all** and a "vâng" would otherwise confirm an empty booking).
- Options come from `GET /api/chat/slot-alternatives?departmentId=&date=[&time=][&session=][&doctorId=]`, returning `sameTimeDoctors` (same department, free at what the patient asked for) and `otherTimes` (the requested doctor's nearest free slots). It applies the same filters as the doctor-list endpoint — past times, **off-duty**, bookings, `DoctorBlockTime`, other sessions' soft locks — but **takes no soft lock of its own**, since asking the patient is not claiming a slot.
- **`reason` + `reasonText` say WHY**, and are the point of the endpoint. `reason` is one of `OFF_DUTY` / `BOOKED` / `BLOCKED` / `HELD` / `PAST` / `TOO_FAR` / `OUTSIDE_HOURS` / `FREE`; `reasonText` is the ready-made Vietnamese sentence used by **both** the chat card and the spoken line.
- **A past date is rejected up front**, before any slot logic. The per-slot `PAST` check only applies when the date *is today*, and a doctor has no `Schedule` for a bygone date, which `slotsOutsideWorkingHours` reports as "unrestricted" — so every slot came back free and the assistant cheerfully offered to book last week. `TOO_FAR` caps the other end at `MAX_BOOKING_AHEAD_DAYS` (90), which is what stopped `date=2035-01-01` reading as wide open.
- **`resolveCanonicalSlot` maps a time to the slot that CONTAINS it**, not `startsWith`. "9 giờ 15" resolved to nothing and the assistant answered "phòng khám chỉ nhận đặt khám trong giờ hành chính…" about a time squarely inside office hours; it now becomes `09:00 - 09:30`. Only genuinely off-grid times (12:15, 19:00) still return `OUTSIDE_HOURS`. `OFF_DUTY` deliberately outranks `BOOKED` — "hôm đó bác sĩ chỉ khám 13:30 - 17:30" is more useful than "khung này có người đặt". `reasonText` is written in the **machine formats** `T3 28/07` and `13:30 - 17:30` so `MediTrustVoice.humanizeSchedule()` speaks it correctly; do not add a separate speech variant. Without this the assistant could only ever say "đã kín lịch", which was simply wrong when the doctor was off duty.
- `slotBlockReason()` returns the reason and `isSlotFree()` is derived from it, so the filter and the explanation can never disagree.
- `requestedDoctorWorkingRanges` is the complement of the off-duty set, adjacent slots merged (the lunch break splits it naturally). **Display only — never invert it into a whitelist:** a doctor with no `Schedule` rows has an empty off-duty set, which would turn "unrestricted" into "nothing allowed".
- **`otherTimes` may be on a different day.** When the doctor is off for the whole requested day, the scan moves to their nearest working day within 7 days, so every entry carries its own `date` and `slotLabel`. Every consumer must use `item.date`, not the handoff's date — otherwise the button books the right time on the wrong day. Only one day is ever emitted, so `distance` (minutes) stays meaningful.
- `sameTimeDoctors` is ranked by `nearbyLoad` (that doctor's bookings within `NEARBY_MINUTES` of the requested time), then `dayLoad`. Fewest first, so the patient waits least at the clinic. Both surfaces must state the **reason** out loud ("em gợi ý anh/chị bác sĩ này vì quanh giờ đó chỉ có 1 ca…") — a bare name gives the patient nothing to choose on.
- If the endpoint reports `requestedDoctorFree`, the fallback was an artifact of the doctor-list preview returning only 4 slots of the nearest day; the handoff is rebuilt at the exact requested slot and is no longer a fallback.

## `resolveBookingHandoff` must never guess
`selectedDoctor.availableSlots` is only a **4-slot preview of the nearest day** (`AiController` stops at the first day with any opening). It is decoration for the doctor cards and is never the basis of a decision. The function has exactly three branches, and only confirms `fallback: false` when what the patient asked for was actually honoured:

- **Time stated** → ask `/slot-alternatives` with `time=`. Free → confirm the **canonical slot the server returns**; otherwise alternatives with the reason.
- **Session and/or date stated** → ask `/slot-alternatives`; it returns the earliest free slot *within that session on that date* or the reason it cannot.
- **Nothing stated** → pick the preview's first slot, **re-verify it** (the preview can be 3 minutes stale via the soft-lock TTL), and mark `suggested: true`.

**The browser must never decide whether a time is on the grid.** Branch 1 used to build the slot itself and check it against `/api/bookings/booked-slots`, which was wrong twice over: it built the range from `booking_target.appointment_time` (the model's invented time — so "10h30" became the model's `"09:00 - 11:00"` and was confirmed as *"khung giờ anh/chị vừa chọn"*), and `booked-slots` only ever lists slots that are **inside** the grid, so anything outside it ("10h20", "12h trưa", "7 giờ tối", "6h sáng") matched no busy entry and read as free — the patient was confirmed into a slot that does not exist and `/appointment` then opened with nothing selected. It also made the server's `OUTSIDE_HOURS` branch dead code. `/slot-alternatives` owns `resolveCanonicalSlot`, so routing through it keeps the grid in one place (`/skills/sync-slot-grid` count stays 11). `isSlotBookable()` survives **only** for branch 3, where the slot came from the server; its docstring says so.

`candidateDepartmentId` falls back to `selectedDoctor.departmentId` — the model often carries a `department_id` from the previous turn's symptoms, and without the fallback `/slot-alternatives` was never called and the reason was lost.

`suggested` changes the wording, and that is the whole point: the card says *"Em xin phép chọn giúp anh/chị khung giờ trống sớm nhất [trong buổi sáng]"*, never *"khung giờ anh/chị vừa chọn"*. **That sentence may only appear when the patient actually chose a clock time.** Naming a *session* is not choosing a time, so branch 2 is `suggested` too. The voice layer mirrors it.

**The model's `booking_target` is the lowest-priority source for time and date, because it invents both.** Observed: for "sáng thứ 3" it filled `appointment_time` with `"09:00 - 11:00"`, and it dated Tuesday as 29/07 when Tuesday was the 28th. So:

- **Time** — `normalizeTimeHint(userText)` first; if the patient named only a *session*, the model's time is **discarded entirely** (otherwise an invented clock time blanks `requestedSession` and the session request is lost).
- **Date** — the patient's own sentence, then `lastHandoffDate` (already resolved and shown to the patient), and only then the model's date. A correction turn ("đổi sang buổi chiều") carries no date, and borrowing the model's hallucinated one moved the booking to another day.

An empty preview must **not** abort the handoff — a doctor off all week produces one, and returning `null` there would be the same silence in a new costume. It falls through to the alternatives path so the patient is told why.

### Answering the offer
The offered options exist only in `pendingAlternatives` in the browser — **the model never saw them**, so it cannot resolve "hướng 1" on its own and used to just re-ask. Two mechanisms close that:

- `resolveAlternativeChoice()` answers locally, before any model call: option ordinals ("hướng 1", "cách 2", a bare "1"), a named doctor from the list, a spoken time from the list, or a stated intent ("đổi bác sĩ" / "giữ bác sĩ, đổi giờ"). It checks the *keep-doctor* phrasings first, since those also contain the words "bác sĩ". A hit skips the model entirely and goes straight to the confirmation card via `finishBookingHandoff()`.
- Anything it cannot resolve still goes to the model, but `buildAlternativeContext()` prepends the offered list to that turn's prompt so the reply is informed rather than blind.

Ordinals map to *directions*, not list positions, and a direction is only offered when its list is non-empty — with no `sameTimeDoctors`, "hướng 1" means the time change.

## The assistant cannot see the schedule
`AiService` has no access to bookings **or to `Schedule`**, so prompt section **5B forbids the model from claiming a slot is recorded, held, or booked, and from claiming a doctor works on a given day/session**. The frontend prints the real answer underneath.

5B also forbids the model from **narrating that it is checking** ("em kiểm tra khung giờ 10 giờ 30 giúp anh/chị ngay ạ") and from **naming any time or date at all** when talking about availability. That phrasing used to be mandated, but every number in it is invented — the model announced "9:00 - 11:00" for "sáng" and "ngày 29 tháng 7" for a Tuesday that was the 28th, and the card underneath then showed something else. The patient does not need a progress report, only the result; the reply is now one short number-free sentence. Before that rule the model would open with "em đã ghi nhận giờ khám 10 giờ 30" and the availability card directly under it said the opposite. 5B also bans the *evasions* of that rule ("em đã ghi nhận **yêu cầu**", "em đã cập nhật", "em đã chuyển sang") — the model reached for those once the literal phrasing was banned. Section **5C** bans the boilerplate that came with it — asking "anh/chị có muốn chọn bác sĩ cụ thể không ạ?" when the doctor cards render anyway, re-asking what the patient already said, and more than one question per turn.

Section 1 states office hours for the **whole clinic** and then says explicitly that each doctor only works their own registered shifts, because the model kept treating "mở cửa cả 7 ngày" as "bác sĩ này khám cả 7 ngày".

**`buildTodayBlock()` injects today's date** ("Hôm nay là Thứ Bảy, ngày 2026-07-26") at the end of the system prompt. Without it the model has no clock at all, so asking it to resolve "thứ ba" into `appointment_date` produced invented dates. The browser's `extractDateHint` still wins whenever it parses a date (see below) — the block only covers phrasings the parser misses.

## The patient may name a session, not a time
"sáng thứ ba" carries a weekday **and** a session but no clock time, and neither was parsed before: `normalizeTimeHint` deliberately ignores "sáng", and `extractDateHint` only knew hôm nay / ngày mai / `d/m`. Both were empty, so the handoff borrowed the previous turn's date and silently confirmed an unrelated slot.

- `extractDateHint` resolves weekday names (`thứ ba` / `thứ 3` / `t3`, `chủ nhật` / `cn`), `tuần sau`, `ngày mốt`, `cuối tuần này`, `N ngày nữa`, and `mùng 5 tháng 8`. When the named weekday **is today**, it stays today unless that session has already elapsed (sáng after 11:30, chiều after 17:30, and **no session named → 17:30**, so "đặt lịch thứ 5" at 18h on a Thursday means next Thursday), in which case it rolls forward a week.
- **Weekday names are matched BEFORE `hôm nay` / `ngày mai`.** A weekday is the more specific instruction, and "hôm nay em bận, đặt giúp em thứ 5" used to return today — the time of the *symptom* was read as the time of the *appointment*.
- **"thứ 2 tuần sau" is computed from next week's Monday**, not "next occurrence + 7". `(day - today + 7) % 7` already lands in next week whenever the weekday has passed, so adding 7 on top overshot by exactly one week (said on a Wednesday, "thứ 2 tuần sau" gave 12 days instead of 5). The week starts Monday, matching `LeavePolicy.weekStartOf`.
- **The `d/m` form accepts only `/`, and both ends must be non-digits.** In a medical conversation `-` is nearly always a measurement range: "sốt 39-40 độ" became 39/40 and "uống 2-3 lần" became a past date, while "huyết áp 120/80" matched as 20/80. Junk dates were worse than missing ones because `lastHandoffDate` stored them and every later turn reused them. `buildDateHint()` also validates the day/month against a real `Date` and rolls a bare `3/1` said in December into next year.
- `extractSessionHint` returns `morning` / `afternoon` / `evening`, and is only consulted when no clock time was parsed. `evening` is refused locally with the `OUTSIDE_HOURS` sentence. It checks **chiều/tối before sáng**, because "đổi sang buổi chiều" contains both cues and the patient means the later one. Naming *both* sessions with "hoặc/hay/đều được" returns `''` — that is a patient with no preference, not a patient asking for the afternoon.
- **Never put bare `sang` or `toi` in that word list.** `sang` is an extremely common preposition ("đổi **sang** buổi chiều", "chuyển **sang** chiều mai"), so matching it as unaccented "sáng" asked the server for the *opposite* session — the doctor worked mornings, so the request was silently confirmed at 07:30 when the patient had asked for the afternoon. `toi` collides with "tôi". Unaccented input is served by the unambiguous `buoi sang` / `buoi toi` instead.
- **Session → slot mapping lives on the server**, not here. The browser sends the word `morning`; `/api/chat/slot-alternatives` decides which slots that covers. This is deliberate — mapping it client-side would make `ai-chat.js` a 12th declaration of the slot grid (see `/skills/sync-slot-grid`).
- Both use **space-padded substring matching, never `\b`** — JS word boundaries are ASCII-only, so `\bđêm\b` can never match (see coding-conventions.md). That idiom matches whole words only, so any alias added to these lists must not be a word that occurs in ordinary Vietnamese sentences.

`lastHandoffDate` remembers the date of the last resolved handoff, because a correction turn ("đổi thành 10h30") carries no date and without one the real slot list cannot be queried. It is only reused when the model omits the date and the remembered date is not in the past.

## The patient may want to stop
`parseCancelIntent()` runs in `sendMessage` **before** `buildAlternativeContext`, and that order is the whole point: the alternatives context orders the model *"TUYỆT ĐỐI KHÔNG hỏi lại khách chọn hướng nào"*, so forwarding "thôi không đặt nữa" together with it forced the model to pick a direction and the browser opened the booking page — right after the patient said no. Only the voice layer had `parseYesNo`; typed chat understood nothing. On a hit it clears `pendingAlternatives` and `lastHandoffDate`, cancels the redirect, and answers locally without calling the model.

`looksLikeBookingRequest()` replaces the old bare `/đặt lịch/` test, which fired on *questions about* booking — "phí đặt lịch bao nhiêu ạ?", "làm sao để hủy đặt lịch?", "em đặt lịch hôm qua mà chưa thấy xác nhận" all ran the full handoff and navigated away. `aiData.booking_intent === true` stays the primary path.

## Failing out loud
`resolveBookingHandoff` returns `{error: 'NETWORK' | 'NO_DOCTORS' | 'NO_DEPARTMENT'}` instead of `null`. Every one of those used to be a bare `return null`, so a patient who said "đặt lịch giúp em" got no card and no explanation — just the model's generic advice. The chat widget and `meditrust-voice-call.js` both render a Vietnamese sentence per code; the voice layer **must** keep its branch, because such a handoff carries no doctor and no slot and would otherwise reach the confirm branch where "vâng" books an empty appointment.

`NETWORK` exists to keep an infrastructure failure from being reported as the patient's mistake: swallowing the fetch error and concluding `doctorNotFound` told them *"Em chưa tìm thấy bác sĩ X"* and they retyped a correct name over and over.

Handing off no longer navigates after 900ms. `startRedirectCountdown()` shows a 5-second countdown with a "Ở lại trang này" button, and `cancelRedirect()` also fires when the patient types, sends another message, or asks to cancel. The old timer could not be stopped by anything, took the half-typed message with it, and made the "Mở trang đặt lịch" button decorative. `suppressAutoRedirect` still disables the whole thing during a voice call.

`isSending` blocks a second turn while one is in flight (two POSTs on the same `sessionId` overwrote each other's history) and Enter is ignored while `e.isComposing`, since Vietnamese IMEs use it to commit a syllable.

All `sessionStorage` access goes through `safeStorage`. Reading it throws `SecurityError` in private mode or with third-party storage blocked, and the first read sits in `DOMContentLoaded` — the exception killed the rest of the script, so the send button was never wired and the widget looked alive but did nothing. `setChatHtml()` also trims the stored transcript, since it grew unbounded and `QuotaExceededError` was thrown inside `appendMessage`, outside `sendMessage`'s try.

## Xưng hô
The prompt's section 0 forces the assistant to call itself **"em"** and the patient **"anh/chị"** — never "bạn", "tôi", or "mình" — in both `ai_reply` and `speech_reply`. Every hardcoded Vietnamese string in the voice modules follows the same convention; keep new strings consistent.

## Other surfaces
Separate admin and doctor assistants exist: `AdminAiController`, `DoctorAiController`, `DoctorAssistantService`, with the `AiRule` entity for configurable rules.

`DoctorAiController` computes "nearest free slot" for the logged-in doctor. It **reads the grid from `TimeSlotService.allSlots()`** and filters it through `BookingService.slotsOutsideWorkingHours` — it must never list slot times of its own. Its old private copy still contained the evening slots dropped on 24/07, so the assistant told doctors they were "rảnh lúc 18:30", a time no patient can book, and it ignored `Schedule` entirely so it offered slots on days the doctor was off. It also skips a booking whose `appointmentTime` will not parse; one odd row used to 500 the entire assistant.

**`/api/doctor/chat/**` is DOCTOR-only and `/api/admin/chat/**` is ADMIN-only, declared in block 0 of `SecurityConfig`** — see the matcher-order section in [authentication-and-roles.md](authentication-and-roles.md) for why they cannot live lower down.

## The doctor dashboard "AI Insight" boxes are NOT AI

All 8 boxes on `/doctor/dashboard` are **deterministic Java rules** in `DoctorInsightService` — no LLM call, no network, no cost. The label says "AI Insight" but the sentence is `if/else` on the numbers already computed for the page. **The AI is the click**: each `DoctorInsightDTO` carries a `prompt`, the template puts it in `data-ai-prompt`, and one delegated listener in `dashboard.html` opens the doctor chat widget and sends it to `/api/doctor/chat/ask`. Do not "upgrade" a box to call the model inline without pricing 8 calls per dashboard load.

This was already how the single original box worked; it just wasn't written down, which is exactly why it read as a real AI feature. Two things followed from making it explicit:

- **`/quick-review-advice` is gone from `DoctorAiController`** (and its byte-identical dead twin from `DoctorDashboardController`). Insights are rendered server-side by Thymeleaf now, so the fetch, its spinner and its network-error branch no longer exist. The card's number and its sentence read the **same variable** and therefore cannot disagree.
- **Rules key off `reviewCount`, never `avgRating != null`.** `ReviewServiceImpl.getAverageRating` returns **0.0, not null**, when a doctor has no reviews — so the old "Chưa có đủ đánh giá" branch was unreachable and every brand-new doctor was greeted with a red "Điểm đánh giá đang thấp, cần khắc phục ngay".

The insight text must agree with the number printed above it. `countToday` counts **CONFIRMED only**, so the "Cần khám hôm nay" box counts the same set — an earlier draft included PENDING and printed "Còn 3/4 ca" under a card reading 2.

Two things in `dashboard.html` are load-bearing and easy to undo by accident:

- **The click handler must be ONE delegated listener with `preventDefault()`.** The "Cần khám hôm nay" and "Yêu cầu mới" cards are wrapped in `<a>`, so a per-box `onclick` makes one tap both open the chat *and* navigate away.
- **The question travels in `data-ai-prompt` via `th:attr`, never `th:onclick`.** Vietnamese prompts contain apostrophes ("bác sĩ nào cũng được"), which break the script when inlined into JS; an attribute is escaped by Thymeleaf.

This whole feature was built in `2287cc2`, reverted by `25d2a3a`, then restored — if the boxes ever go missing again, check git before rewriting `DoctorInsightServiceImpl` from scratch.

## The doctor's exam form has 4 AI assists — and those ARE real LLM calls

Unlike the dashboard boxes, `DoctorExamAiController` (`/api/doctor/chat/exam/**`) does call the model. It is a **separate controller from `DoctorAiController`** on purpose: that one serves the floating chat widget and was already long. The path still sits under `/api/doctor/chat/`, so the block-0 rule `/api/doctor/chat/**` → `hasRole("DOCTOR")` covers it and **no new security matcher is needed** — keep any further exam endpoints under that prefix for the same reason.

| Endpoint | Does |
|---|---|
| `POST /check-prescription` | Cross-checks the prescription being typed against the patient's real `Allergy` rows, then drug interactions |
| `POST /draft-notes` | Drafts `doctorNotes` |
| `POST /suggest-icd` | Suggests ICD-10 codes from the diagnosis text |
| `GET /patient-summary/{bookingId}` | 3-bullet summary of the patient's previous records |

Rules that must survive any edit:

- **None of them is `@Transactional`.** Each calls the network mid-method; a transaction here pins a HikariCP connection (pool of 10) for the whole wait — the same reason `AiService.getConversationalResponse` has none.
- **Allergies are read from the DB by `bookingId`, never accepted from the request body.** This is patient-safety data; a hand-made request with an empty list would silently turn the warning into "nothing found". The typed-in medicines *do* come from the browser, because `MedicalRecord` does not exist yet at that point — the form saves once, at the end.
- **`requireOwnedBooking` gates every endpoint that touches patient data**, and returns the same 403 for "not yours" and "does not exist" so the API cannot be used to probe booking ids.
- **`draft-notes` must force the exact phrase `"Tái khám sau N ngày/tuần/tháng"`.** That is the pattern `FollowUpReminderTask.REVISIT_PATTERN` extracts from `doctorNotes`; the regex deliberately does not read absolute dates, so free-form advice makes the follow-up reminder silently never fire. See [medical-records.md](medical-records.md).
- **Everything is a suggestion.** No endpoint writes to `MedicalRecord`; the doctor edits and presses save. The ICD box only fills the input — a wrong diagnosis code is a wrong legal record.
- **Answers render with `textContent`, not `innerHTML`** — model output must never become live markup inside a medical record page.

## Housekeeping
A `@Scheduled` job (`0 0 2 * * ?`) purges guest chat sessions older than 7 days. Scheduling is enabled by `SchedulerConfig`; async support by `@EnableAsync` on the main application class.
