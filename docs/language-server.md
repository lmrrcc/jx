# Shared language services

`jx-language-server` packages the shared compiler and core contracts with LSP4j
1.0.0 in an executable JAR. Run it with JDK 25:

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl jx-language-server -am package
java -jar jx-language-server/target/jx-language-server-0.1.0-SNAPSHOT.jar
```

The process speaks LSP JSON-RPC on standard input/output. An editor launches it;
running the second command in a terminal waits for protocol messages. Logs use
LSP notifications or standard error, never the protocol stream.

## Implemented behavior

- Full document synchronization with monotonic document versions. Older updates
  are ignored. Replaced analysis is cancelled and its diagnostics cannot replace
  newer results. Closing a document clears its diagnostics.
- UTF-16 positions, including CRLF and surrogate pairs. The server advertises
  UTF-16 explicitly; it does not advertise UTF-8 positions.
- Shared markup syntax and typed-prop diagnostics. Generated Java is checked with
  javac and annotation processing disabled, then errors are mapped to `.jx`.
- HTML/component/property completion, including an unfinished opening tag.
- Component and prop hover with declared Java types, definition navigation, and
  component/prop document symbols.
- Conservative hook-placement diagnostics from the compiler. These do not prove
  purity or correct control flow through arbitrary Java helper methods.

Java type feedback covers the compiler's generated/shadow Java subset. General
Java expression completion, Java method navigation, rename, import organization,
formatting, debugger stepping, and browser stack-map composition are not advertised.

## Project and classpath discovery

The nearest `pom.xml` within the containing workspace identifies an open file's
module. Without a POM, the containing workspace folder is used. The server reads
`.jx` sources and ordinary Java under `src/main/java` and `src/test/java`, applying
open-document overlays. It excludes build outputs, caches, dependency trees, and
symbolic-link traversal. Sources are bounded to 1 MiB each and 2,000 per project;
open a smaller module if the limit is exceeded.

The bundled core supplies JX types. Existing `target/classes` and
`target/test-classes` directories under workspace roots are added to the analysis
classpath. Additional dependencies can be supplied as absolute paths in LSP
initialization options:

```json
{"classpath": ["/absolute/path/to/dependency.jar"]}
```

The server never invokes Maven, project scripts, or annotation processors. Build
the project explicitly to refresh dependency classes, then restart the language
server after changing its classpath settings. This is an initial Maven-convention
analysis path, not automatic resolution of every Maven profile or generated root.
Workspace output inference can include a module that is not a declared dependency;
therefore these editor checks are not authoritative Maven dependency validation.
The Maven `check` goal uses the project's actual compile classpath and remains the
build gate for that boundary.

The protocol tests exercise initialization, original-source type errors,
completion on incomplete markup, hover, navigation, symbols, version rejection,
cancellation, document close, shutdown, and UTF-16/CRLF positions through LSP4j's
actual JSON-RPC transport. Editor packages consume the same executable JAR.
