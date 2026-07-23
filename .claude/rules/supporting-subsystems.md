# Supporting Subsystems

## Wallet & transactions
`User.balance` (a `BigDecimal` on the user) plus a `WalletTransaction` ledger typed by `TransactionType`. `WalletService` handles debit (`payWithWallet`, returns `false` on insufficient funds rather than throwing) and `refundToWallet`. Booking cancellations refund to the wallet.

## Recruitment
`JobPosting` + `Candidate` (with `CandidateStatus`) — public careers pages plus admin management (`AdminJobController`, `AdminCandidateController`). Applicants receive a confirmation email.

## Content
- `Post` — news/blog articles, authored in admin with TinyMCE rich text (vendored under `static/assets-admin/vendor/tinymce`).
- `Review` — patient ratings of doctors, submitted via `UserReviewController`.
- `Service` and `Department` — catalog entities surfaced on public pages.

## QR codes
`util/QRCodeGenerator` uses ZXing to render QR images — used for the VietQR bank-transfer payment page and booking tickets.

## Email
`EmailServiceImpl` sends HTML mail rendered from Thymeleaf templates in `templates/email/`: booking confirmation, booking cancellation, candidate confirmation, and a general notification template. Sending is asynchronous (`@EnableAsync`), so failures surface in logs rather than in the request.

## Dashboards
`AdminDashboardService` + `DashboardApiController` aggregate booking statistics (`DailyBookingStatsDTO`, `AdminDashboardSummaryDTO`) consumed by charts on the admin dashboard.
