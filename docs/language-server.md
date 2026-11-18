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

The server owns JX syntax and component semantics. Native Java expression
completion, Java navigation and source edits are delegated by the editor adapters
to IntelliJ's Java plugin or VS Code's Red Hat Java extension. Their verified
operations and compatibility limits are listed in [the tooling guide](tooling.md).
The server does not implement a second general-purpose Java language engine.

## Native Java projection protocol

Editor adapters request `jx/editorProjection` with `{uri, version?}`. Protocol
version 1 returns `projectUri`, `files`, and `unavailable`. Each file contains its
`sourceUri`, nullable open-document `version`, `sourceText`, `relativeJavaPath`,
`javaText`, `segments`, `sourceLength`, and `topLevelTypes`. Offsets and lengths are
UTF-16. The version is null for a source read from disk. The response includes
current sibling `.jx` snapshots from the same Maven module.

Editor recovery retains incomplete markup and typed Java expression contexts.
Its per-file expansion budget is 65,536 prop-constructor slots and 16,777,216
generated UTF-16 code units. A source exceeding either budget is unavailable;
other sources in the module still receive projections. These editor-only recovery
rules do not relax build validation. Adapters also enforce their own working-copy
and session bounds.

The requested version must match the analyzed open document. Older versions fail
with LSP `ContentModified`; concurrent changes cancel an obsolete request. No
generated source is written by this operation. An unavailable projection must
invalidate any older native Java copy, rather than keeping stale Java semantics.

Each source-map segment has `generatedStart`, `length`, `sourceStart`, and
`linear`. Only a range entirely contained in a linear segment can authorize an
authored Java edit. Synthetic mappings are diagnostic anchors. Adapters must also
verify the source snapshot before applying edits. See the
[editor architecture decision](decisions/0002-native-java-editor-services.md).

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
