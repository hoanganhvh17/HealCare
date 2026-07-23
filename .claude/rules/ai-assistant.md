# AI Assistant (multi-persona, OpenRouter)

[AiService.java](src/main/java/com/bookinghealthy/service/AiService.java) calls an OpenAI-compatible endpoint (OpenRouter, configured via `ai.api.url` / `ai.api.key`) using the shared `RestTemplate` bean, with a **model fallback chain**: `openai/gpt-4o-mini` → `openrouter/free` → `google/gemini-2.0-flash-exp:free`. Each model is tried in order until one returns a choice.

## Patient triage (`chatWithMemory`)
A large Vietnamese system prompt constrains the model to return **strict JSON** with these keys:

`reasoning`, `ai_reply`, `suggested_prompts` (always 3), `recommended_departments` (array of department IDs), `is_emergency`, `patient_summary`, `booking_intent`, `booking_target`.

The frontend consumes these fields to surface doctor cards and auto-open the booking form, so **the schema is a contract** — changing key names requires updating the templates under `templates/*/include/ai-chat*.html`.

Department IDs carry special meaning in the prompt: **21 = Cấp cứu (Emergency)** and **22 = Y học gia đình** (the safety fallback when symptoms are ambiguous). These are seeded IDs from `DataInitializer`; re-seeding into a different order would break the prompt's assumptions.

The live department list is injected into the prompt at request time from `DepartmentRepository`, so new departments become selectable without prompt edits.

## Memory
- Conversations persist in `AiChatSession` (`chatHistoryJson`), keyed by a `sessionCode`.
- Only the **last 6 messages** are sent to the model.
- `patient_summary` is re-extracted from prior assistant messages via regex and re-injected each turn as durable memory.
- If the session belongs to a logged-in `User`, the patient's most recent `COMPLETED` booking's `MedicalRecord` (diagnosis, symptoms, doctor notes) is also injected as context.

## Other surfaces
Separate admin and doctor assistants exist: `AdminAiController`, `DoctorAiController`, `DoctorAssistantService`, with the `AiRule` entity for configurable rules.

## Housekeeping
A `@Scheduled` job (`0 0 2 * * ?`) purges guest chat sessions older than 7 days. Scheduling is enabled by `SchedulerConfig`; async support by `@EnableAsync` on the main application class.
