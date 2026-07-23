# Project Overview

MediTrust — a healthcare appointment-booking web app built with Spring Boot 3.2.5 (Java 21).

- The app is **server-side rendered with Thymeleaf**, not a REST-only backend. Most controllers return template view names.
- A smaller set of `@RestController`s under `controller/api` serve JSON (booking slots, AI chat, dashboard stats). Reach for these only for genuinely asynchronous frontend needs; page navigation belongs in an MVC `@Controller`.
- The codebase, comments, and commit messages are primarily in **Vietnamese**.

Entry point: [BookingHealthyApplication.java](src/main/java/com/bookinghealthy/BookingHealthyApplication.java) — declares `@EnableAsync` and the shared `RestTemplate` bean used by the AI layer.
