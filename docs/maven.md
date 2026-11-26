# Maven builds and development

Use JDK 25 and the checked-in Maven 3.9.11 wrapper. Browser assets require the root
lockfile's React, React DOM, and esbuild packages, installed with Node 20 or later
and `npm ci`. The plugin never installs npm packages implicitly or publishes
artifacts. The `dev.jx:0.1.0-SNAPSHOT` artifacts are built locally; they are not
available from Maven Central.

From the repository root:

```sh
npm ci
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl jx-maven-plugin,jx-react -am install
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -f examples/counter-app/pom.xml package
npm run test:counter
```

The scoped install, counter package, and Chrome browser test have been executed
successfully. The counter test launches the Maven development goal and verifies
component state, parent rerenders, queued updates, remounting, and interaction
with networking disabled. The npm prerequisite uses the existing lockfile; Chrome
is the configured browser for this test.

See the full [counter POM](../examples/counter-app/pom.xml) for a reproducible
consumer configuration. It explicitly binds these plugin goals:

| Goal | Default lifecycle phase | Purpose |
| --- | --- | --- |
| generate | generate-sources | Compile src/main/jx; register generated production Java |
| check | process-sources | javac validation with original-source diagnostics |
| generate-test | generate-test-sources | Compile src/test/jx using production component metadata |
| check-test | process-test-sources | Validate production/test Java with test classpath |
| record-codecs | generate-sources | Generate browser JSON codecs from selected compiled dependency records |
| browser | prepare-package | Bundle TeaVM's ES module and copy web assets |
| web-jar | prepare-package | Attach the built site as a resource-only web classifier JAR |
| dev | Explicit command only | Rebuild, serve loopback assets, and preserve compatible browser state |

A minimal plugin execution configuration is:

```xml
<plugin>
  <groupId>dev.jx</groupId>
  <artifactId>jx-maven-plugin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <executions>
    <execution><id>jx-main</id><goals><goal>generate</goal></goals></execution>
    <execution><id>jx-check</id><goals><goal>check</goal></goals></execution>
    <execution><id>jx-tests</id><goals><goal>generate-test</goal></goals></execution>
    <execution><id>jx-check-tests</id><goals><goal>check-test</goal></goals></execution>
    <execution><id>jx-browser</id><goals><goal>browser</goal></goals></execution>
  </executions>
</plugin>
```

Normal Maven compilation compiles the generated Java. Configure TeaVM 0.16.0
`compile` at `process-classes`, with the application's Java main class,
`targetType=JAVASCRIPT`, `jsModuleType=ES2015`,
`targetDirectory=${project.build.directory}/browser`, and
`targetFileName=application.js`. The browser goal generates the framework's
small main-function bootstrap and bundles it with the locked React runtime.
Application behavior requires no authored JavaScript.

To embed a browser site in a Spring application, bind `web-jar` after `browser`.
The [Todo POM](../examples/todo-app/pom.xml) binds TeaVM first and browser/web-jar
next at `process-classes`, making the attached artifact available to downstream
reactor tests. The backend's runtime dependency selects classifier `web` and
excludes every transitive dependency from the browser module. Spring serves the
archive's `META-INF/resources` using its ordinary classpath resource handler;
no filesystem site location is needed. `web-jar` excludes source maps, source and
class files and dot files, rejects symlinks/noncanonical names, and recreates the
archive so removed assets do not survive. Keep the site directory immutable while
the goal runs. See the [empty-directory proof](evidence/packaged-spring-todo.md).

## Shared record codecs

Put portable response records in a dependency JAR and select them in the browser
module. The shared artifact needs no JX, browser, Spring or Jackson dependency:

```xml
<execution>
  <id>todo-contract</id>
  <goals><goal>record-codecs</goal></goals>
  <configuration><contractTypes>
    <contractType>jx.example.contract.Todo</contractType>
  </contractTypes></configuration>
</execution>
```

`TodoJson.decode(Json)` and `TodoJson.encode(Todo)` are generated beside the
record's package in `target/generated-sources/jx-contracts`. javac reads dependency
class symbols without loading the application or running processors. The initial
subset is public top-level non-generic records with required `String`, `boolean`
and `int` fields, using exact component names. Unknown fields in incoming JSON
are ignored; missing/null/wrong-type required fields fail. Custom retained
annotations and other types fail generation rather than being interpreted as
Jackson policy. Actual server serialization must match this subset, as the
[Todo proof](evidence/shared-todo-contract.md) verifies through real HTTP.

The bounded, hashed ownership manifest is stored under `target/jx/contracts`.
Removing a selected type deletes only owned generated sources/classes. Unknown
or manually edited output is preserved with an explicit failure. This goal does
not generate request DTOs, server endpoints, async clients or compatibility rules.

## Files and incremental behavior

| Input/output | Default path |
| --- | --- |
| Production JX | src/main/jx |
| Test JX | src/test/jx |
| Generated production Java | target/generated-sources/jx |
| Generated test Java | target/generated-test-sources/jx |
| Maps, output ownership, check-only classes | target/jx/main and target/jx/test |
| TeaVM JavaScript and Java source maps | target/browser |
| HTML/CSS/static assets | src/main/web |
| Bundled application and copied assets | target/site |
| Framework bootstrap, bundler metadata | target/jx/browser |
| Development assets and metadata | target/jx/dev-site and target/jx/dev-browser |

Declared Java packages determine generated output paths. Each invocation reads and
reanalyzes the complete source batch, so component signature edits are propagated.
It only rewrites Java/maps whose content changed. `check` reads the current Maven
classpath on each invocation and does not reuse semantic results across builds.

Ownership manifests record generated Java and declared top-level types. Removing
or renaming a source removes only its owned Java/map files. Editing/deleting a
source removes its old classes, including nested and auxiliary classes. Cleanup
inspects classfile SourceFile metadata so an unrelated top-level class with a
dollar sign in its name is preserved. Missing class ownership metadata fails with
a clean-build instruction; it does not guess which file to delete.

Unowned files are preserved. A collision with an unowned generated Java or web
asset fails explicitly. Source-generation path traversal and output symlinks are
rejected. Generated output directories belong to one Maven module; sharing them
between modules is unsupported. There is no cross-module mutable plugin cache.

Static assets have their own manifest; removed assets are removed from the site
directory. `application.js` and `application.js.map` are reserved bundle outputs.
The browser goal resolves the nearest ancestor package-lock.json and installed
esbuild. It uses the lockfile's existing installation, without network downloads.

This is deterministic regeneration with selective writes, not a complete dependency
incremental compiler. Use a normal Maven `clean package` after changing Java build
options, processors, module structure, or external generators if stale output
from those tools is possible. TeaVM and esbuild rebuild through their configured
lifecycle on each package. The development client migrates compatible committed
state into the new Java generation; see [the runtime contract](runtime.md).

The compiler/plugin regression tests run real javac compilation and exercise
repeated builds, source edits, nested-class removal, rename/deletion, malformed
input, test-source indexing, output ownership, same-size/same-timestamp watch
changes, and the HTTP serving boundary.

## Development loop

After the local artifact install and npm setup:

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository \
  -f examples/counter-app/pom.xml \
  dev.jx:jx-maven-plugin:0.1.0-SNAPSHOT:dev
```

Open http://127.0.0.1:4173. The goal performs an initial package build and watches
`src`, `browser`, `public`, and `pom.xml` in the selected module.
Saving Java, JX, or assets triggers a debounced rebuild. Successful builds replace
the Java application while retaining compatible counters, controlled inputs, and
explicitly coded application models. The browser document and URL remain in
place; CSS, focus, text selection, and scroll update with the application. Effects
clean up and restart. Failed builds print original-source diagnostics and leave
the last successful application interactive. Fixing and saving retries
automatically. Ctrl+C stops the server and its active Maven child.

Scalar hook values are automatic. Records, collections, and Elm stores need a
typed `StateCodec`; the Todo application demonstrates this. Changed hook structure,
codec schema, or unsupported external custom hooks reset affected state with a
development notice. Uncontrolled DOM state and arbitrary service objects are not
checkpointed. This is state migration across rebuilt Java bundles, with deliberate
effect remounting; see [the decision](decisions/0003-state-preserving-development.md).

Development builds share a single React vendor bundle and use removable scripts
for new Java generations. Restart development and reload the page after changing
React or other vendor dependencies, or changing the HTML document shell (including
adding/removing stylesheet links). Existing stylesheets refresh on every update.
Production `package` emits the normal bundle without the client or development
bootstrap. Development writes `target/jx/dev-site`, leaving packaged `target/site`
assets intact for a separately running backend. Do not serve development output
as a production artifact.

### Development runtime attestation

A browser entry point using this JX runtime automatically makes the framework's
`HotRuntime` class reachable. TeaVM exports it as `JxHotRuntime`; no additional
application annotation or method is required. Its one `protocolN` export name
and the runtime's initialization check derive from the same Java version constant.
The bootstrap validates that marker without calling it: invoking an exported Java
wrapper can initialize the TeaVM application's entry class before `main`.

The development bootstrap imports the attestation from the actual compiled
JavaScript, rather than inferring compatibility from the Maven plugin version.
An old main-only module fails bundling with a missing `JxHotRuntime` export; the
last published application remains live. A compiled runtime advertising another
protocol is rejected during candidate evaluation, before capture or disposal.
Rebuild the application with the matching JX runtime and reload the development
page when migrating from an older protocol. Do not copy a JavaScript version
constant into an old bundle.

Ordinary production builds still import only `main` and do not activate hot
replacement. This uses TeaVM's documented
[JavaScript module exports](https://www.teavm.org/docs/runtime/js-modules.html).

Useful properties:

| Property | Default / behavior |
| --- | --- |
| jx.port | 4173; loopback binding only |
| jx.apiOrigin | Optional loopback HTTP backend, e.g. http://127.0.0.1:8181; forwards /api/ only |
| jx.browserDirectory | target/jx/dev-site for dev; target/site for package |
| jx.buildGoal | package; verify is also accepted |
| jx.buildTimeoutSeconds | 180, allowed range 10–1800 |
| jx.mavenExecutable | Find Maven Wrapper in project or ancestor |
| jx.compiledJavaScript | target/browser/application.js |
| jx.webDirectory | src/main/web |
| jx.nodeExecutable | node, when the installed esbuild launcher requires Node |
| jx.sourceDirectory / jx.testSourceDirectory | Conventional JX input roots |
| jx.outputDirectory / jx.testOutputDirectory | Conventional generated Java roots |

Additional watch directories can be listed in the plugin's `watchDirectories`
configuration. Watch inputs are bounded to 10,000 files, 32 MiB per file, and
128 MiB total. Builds have a timeout and terminate owned child processes on
shutdown. The HTTP server serves only the selected asset directory, validates its
loopback Host header, rejects path traversal/symlink escapes, and uses no-store
responses. Its `/__jx_dev/version` endpoint supports update polling. Successful
builds publish a bounded immutable asset snapshot; partially written or failed
builds cannot replace the served application. The development client is served
at `/__jx_dev/client.js` and is injected only by this server.

For the Todo application, keep its Spring API running on port 8181 and start the
frontend separately:

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository \
  -f examples/todo-app/pom.xml \
  dev.jx:jx-maven-plugin:0.1.0-SNAPSHOT:dev \
  -Djx.port=4180 -Djx.apiOrigin=http://127.0.0.1:8181
```

Open http://127.0.0.1:4180/todos for hot updates. The development proxy forwards
only `/api/` to that loopback backend, with bounded bodies and timeouts. It rejects
cross-origin mutations and does not forward cookies or authorization headers.
The page served directly by the backend on 8181 is the normal packaged app.

`npm run test:hot` copies the Counter and Todo sources into isolated cache
directories, edits `.jx` and CSS, and verifies actual Maven-driven state-preserving
updates in Chrome. It does not edit the examples or use the developer's Todo data.

The dev command rebuilds only the selected Maven project using locally installed
dependencies. When changing another framework module, rebuild/install that module
first or run the reactor build. For production, serve the built target/site assets
with the application's chosen server; the dev server is not a deployment server.
