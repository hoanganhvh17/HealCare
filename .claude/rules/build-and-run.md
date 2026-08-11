# Build, Run & Test Commands

Uses the Maven wrapper (`./mvnw` on Unix, `mvnw.cmd` on Windows). Java 21 is required.

```bash
./mvnw spring-boot:run          # Run the app (starts on http://localhost:8090)
./mvnw clean package            # Build the jar
./mvnw test                     # Run tests (NOTE: no test classes exist yet under src/test)
./mvnw test -Dtest=ClassName#methodName   # Run a single test once tests exist
```

- **Never run a build in another terminal while `spring-boot:run` is up.** `mvnw compile`, `mvnw test` and `mvnw package` all rewrite `target/classes` underneath the running process, and spring-boot-devtools reloads mid-write — the app dies with `Illegal factory instance for factory method 'restTemplate'` or a `BeanInstantiationException` that looks like a code defect and is not one. Stop the app first, or build in a separate checkout. (Editing a file under `src/main/resources/static/` has the mirror problem: devtools serves `target/classes`, so the browser keeps getting the **old** asset until something copies it across — `cp` the file or restart.)
- **Preview features are off.** `maven-compiler-plugin` pins `source`/`target` to 21 with no `--enable-preview`. Do not re-add that flag: `javac` only accepts it when `-source` equals the compiler's own version, so it breaks any IDE or toolchain running a JDK newer than 21 (Eclipse JDT in VS Code compiles at release 26 and aborts the build, leaving `target/classes` nearly empty and the app failing with `ClassNotFoundException`). Stick to standard Java 21 language features.
- There is **no frontend build step** — CSS/JS live as static assets under `src/main/resources/static/` and are served directly. Do not introduce a bundler without discussing it first.
- **Jsoup** (`org.jsoup:jsoup`) is the only HTTP/HTML library besides `RestTemplate`, added for `NewsFeedService`. It does three jobs there — parse RSS (`Parser.xmlParser()`), extract the article body from a news page, and **sanitize the model's HTML** before it is stored (`news-details.html` renders it with `th:utext`). Reach for it rather than adding a second HTML or RSS library. See [supporting-subsystems.md](supporting-subsystems.md).
- `maven-resources-plugin` pins UTF-8 encoding; keep it, as templates and prompts contain Vietnamese text.
