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
| browser | prepare-package | Bundle TeaVM's ES module and copy web assets |
| dev | Explicit command only | Rebuild, serve loopback assets, and reload the browser |

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
lifecycle on each package. No state-preserving hot reload is claimed.

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
Saving Java, JX, or assets triggers a debounced rebuild. Successful builds reload
the page, resetting browser state. Failed builds print the original-source
diagnostics and display an error notice over the last successful page. Fixing and
saving retries automatically. Ctrl+C stops the server and its active Maven child.

Useful properties:

| Property | Default / behavior |
| --- | --- |
| jx.port | 4173; loopback binding only |
| jx.browserDirectory | target/site |
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
responses. Its `/__jx_dev/version` endpoint supports reload polling.

The dev command rebuilds only the selected Maven project using locally installed
dependencies. When changing another framework module, rebuild/install that module
first or run the reactor build. For production, serve the built target/site assets
with the application's chosen server; the dev server is not a deployment server.
