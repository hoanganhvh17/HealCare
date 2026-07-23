# Progress Log & Documentation Upkeep (MANDATORY)

**Every time work is completed in this repo, the documentation must be updated in the same change.** Do not treat a task as finished until this rule has been applied — it is part of the task, not optional follow-up.

## What to update

1. **This file** — append an entry to the log below for every completed piece of work (new feature, bug fix, refactor, config change, removed code).
2. **The topic rule file(s) under `.claude/rules/`** — if the change alters how the app actually behaves, update the rule that owns that topic so it stays accurate:
   - booking / slots / cancel / reschedule / queue → [booking-flow.md](booking-flow.md)
   - roles, URL authorization, login flows → [authentication-and-roles.md](authentication-and-roles.md)
   - new controller/service/repository/entity layout, new package → [code-structure.md](code-structure.md)
   - AI prompt, JSON schema, models, memory → [ai-assistant.md](ai-assistant.md)
   - medical records cluster → [medical-records.md](medical-records.md)
   - wallet, recruitment, content, QR, email, dashboards → [supporting-subsystems.md](supporting-subsystems.md)
   - properties, DB, seed data, uploads, port → [environment-setup.md](environment-setup.md)
   - Maven, dependencies, run/build commands → [build-and-run.md](build-and-run.md)
   - conventions, gotchas, error handling, testing → [coding-conventions.md](coding-conventions.md)
3. **[CLAUDE.md](../../CLAUDE.md)** — only when a *new* rule file is created (add the `@` import) or when the one-line project description at the top no longer fits.

If a change spans several topics, update every rule file it touches. If a documented behaviour is deleted, delete the sentence describing it — stale rules are worse than missing ones.

## Entry format

Newest first. One line per completed unit of work:

```
- YYYY-MM-DD — `type:` short Vietnamese description — files/areas touched — rule files updated (or `—`)
```

`type` follows the commit convention: `feat:` / `fix:` / `refactor:` / `docs:` / `chore:`.

Keep entries short (one line). Details belong in the topic rule file, not here.

---

## Log

- 2026-07-23 — `docs:` thêm quy tắc bắt buộc ghi nhật ký tiến độ vào `.claude/rules/` — `.claude/rules/progress-log.md`, `CLAUDE.md` — progress-log.md (mới)
- 2026-07-23 — `feat:` thay đổi lịch khám phía client (bệnh nhân tự dời lịch tại `/user/booking/edit/{id}`) — `UserBookingEditController`, `BookingService.whyCannotReschedule` / `rescheduleByUser`, `templates/user/` — booking-flow.md
- 2026-07-23 — `feat:` role lễ tân: đặt lịch tại quầy, hủy/dời lịch, hàng đợi khám — `controller/receptionist/`, `ReceptionServiceImpl.sortByQueue`, `DataInitializer.ensureReceptionistAccount` — authentication-and-roles.md, booking-flow.md
- 2026-07-21 — `feat:` AI điều hướng tự động đặt lịch (`booking_intent` / `booking_target`) — `AiService`, `templates/*/include/ai-chat*.html` — ai-assistant.md
- 2026-07-21 — `fix:` lỗi hiển thị ảnh avatar và thanh tìm kiếm — `ImageService`, `WebConfig`, static assets — —
