# Executable Spring JAR with browser assets

Source `f101e5a`, 26 November 2026. M1-20 is locally verified; publication gate
and Verify remain pending. Authentication/CSRF is the separate M1-21 slice.

The optional Maven `web-jar` goal attaches the built site as a `web` classifier
JAR under `META-INF/resources`. It contains no Java bytecode, Java/.jx sources,
source maps or dot files. This uses Spring Boot's documented
[classpath static-resource support](https://docs.spring.io/spring-boot/reference/web/servlet.html#web.servlet.spring-mvc.static-content).
The Todo backend depends on this artifact with all of its browser transitive
dependencies excluded. It retains only the portable contract and ordinary server
dependencies; the artifact inspection rejects JX runtime/TeaVM backend libraries.

The example binds TeaVM, browser bundling and `web-jar` in that order at
`process-classes`, so downstream backend tests can resolve the attached assets
before packaging. The backend no longer reads a workspace-relative site directory.
For a fresh application build:

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl jx-maven-plugin,examples/todo-api -am package
java -jar examples/todo-api/target/todo-api-0.1.0-SNAPSHOT.jar
```

Executed on this source, including the inherited javac-plugin isolation fix:

- Scoped reactor package: **166 JVM checks** plus real TeaVM/browser assets,
  23.546 seconds. Archive tests cover deterministic contents, removed assets,
  excluded sources/maps/classes, symlinks, unbuilt sites and POSIX backslash path
  traversal without replacing the previous artifact.
- `npm run test:packaged`: **2/2 Chrome checks**, 3.2 seconds. The Java launcher
  copies only the executable JAR into a newly empty temporary directory, starts
  from there and owns shutdown/cleanup. It inspects the nested web artifact and
  rejects browser implementation dependencies in the server JAR. Real CRUD,
  direct routes, reload/persistence, MIME types, unknown API/asset paths, private
  file paths and non-GET SPA routing are exercised.
- `npm run test:todo`: **4/4 existing Chrome checks**, 4.4 seconds, now using
  classpath assets, including the exact-value contract profile.

The packaged check is a required Verify/pre-push step (both now have 16 steps).
This is a root-context CSR proof. Context-prefix rewriting, fingerprinted assets,
cache/CSP policy, starter/BOM integration, SSR and deployment hardening remain
separate. Browser inputs must remain immutable while `web-jar` runs; concurrent
writers are not supported. Temporary cleanup is best effort after OS/shutdown
failures, with diagnostics; the owned process is awaited before file removal.
No performance or cross-browser claim is made.
