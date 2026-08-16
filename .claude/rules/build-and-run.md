# Build, Run & Test Commands

Uses the Maven wrapper (`./mvnw` on Unix, `mvnw.cmd` on Windows). Java 21 is required.

```bash
./mvnw spring-boot:run          # Run the app (starts on http://localhost:8090)
./mvnw clean package            # Build the executable (fat) jar
java -jar target/booking-healthy-0.0.1-SNAPSHOT.jar   # Run the packaged artifact
./mvnw test                     # Run tests (two classes exist under src/test)
./mvnw test -Dtest=ClassName#methodName   # Run a single test
```

- **`spring-boot-maven-plugin` must stay declared in `<build><plugins>`.** It was missing until 2026-08-15, and the failure mode is silent: `mvn package` still reported BUILD SUCCESS while producing a **thin** jar with no bundled dependencies and no `Main-Class`, so `java -jar` died with `no main manifest attribute`. Dev never saw it because `./mvnw spring-boot:run` resolves the `run` goal by prefix and takes its version from the parent's `pluginManagement` — but the `repackage` **execution** is only bound when the plugin is declared in `<build><plugins>`. Verify with `unzip -p target/*.jar META-INF/MANIFEST.MF | grep Main-Class`.
- Deployment is documented in **[deploy/README.md](../../deploy/README.md)**, with `deploy/env.example` (every environment variable) and `deploy/nginx.conf.example`. Schema objects Hibernate cannot express live in `db/manual/*.sql` and are run by hand — `SchemaGuard` checks them at boot. See [environment-setup.md](environment-setup.md).

- **Never run a build in another terminal while `spring-boot:run` is up.** `mvnw compile`, `mvnw test` and `mvnw package` all rewrite `target/classes` underneath the running process, and spring-boot-devtools reloads mid-write — the app dies with `Illegal factory instance for factory method 'restTemplate'` or a `BeanInstantiationException` that looks like a code defect and is not one. Stop the app first, or build in a separate checkout. (Editing a file under `src/main/resources/static/` has the mirror problem: devtools serves `target/classes`, so the browser keeps getting the **old** asset until something copies it across — `cp` the file or restart.)
- **Preview features are off.** `maven-compiler-plugin` pins `source`/`target` to 21 with no `--enable-preview`. Do not re-add that flag: `javac` only accepts it when `-source` equals the compiler's own version, so it breaks any IDE or toolchain running a JDK newer than 21 (Eclipse JDT in VS Code compiles at release 26 and aborts the build, leaving `target/classes` nearly empty and the app failing with `ClassNotFoundException`). Stick to standard Java 21 language features.
- There is **no frontend build step** — CSS/JS live as static assets under `src/main/resources/static/` and are served directly. Do not introduce a bundler without discussing it first.
- **Jsoup** (`org.jsoup:jsoup`) is the only HTTP/HTML library besides `RestTemplate`, added for `NewsFeedService`. It does three jobs there — parse RSS (`Parser.xmlParser()`), extract the article body from a news page, and **sanitize the model's HTML** before it is stored (`news-details.html` renders it with `th:utext`). Reach for it rather than adding a second HTML or RSS library. See [supporting-subsystems.md](supporting-subsystems.md).
- `maven-resources-plugin` pins UTF-8 encoding; keep it, as templates and prompts contain Vietnamese text.
