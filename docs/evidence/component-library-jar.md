# Source-independent component-library proof

This G0 M1-10–M1-13 slice is based on `05945b4` so the existing reference-JAR
metadata/compiler APIs remain available. It adds a separate component ABI 1;
it does not replace the Server Function/client-reference protocol. Integration
with the newer class-authoring work, the full push gate and Verify are pending.

## Measured checks

| Check | Result |
| --- | --- |
| Core / React / compiler / Maven JVM tests | 73 / 2 / 75 / 28 pass; 178 total, including nine new compiler and two plugin contracts |
| Producer/framework/plugin install in a private Maven repository | Pass, 3.899 s; existing JVM tests were not repeated |
| Standalone consumer normal Maven `package` | Pass, 4.395 s; TeaVM compiled 545 classes / 3680 methods |
| Standalone Chrome journey | 1 passed in 1.3 s, using the same saved standalone site; no page or console errors |

The compiler tests build actual public `.jx` components with javac, package only
class files and `META-INF/jx/components.idx`, then compile an independent source
batch against that JAR. The producer's static initializer throws if executed;
metadata discovery never initializes it. The consumers exercise ordinary and
generic Props, no-Props components, nested children and checked native-class refs.
Missing/future descriptors, stale class hashes, duplicate artifacts, malformed
indices and invalid public descriptor shape fail; diagnostics remain on the
consumer's authored tag. The plugin checks output ownership, unchanged timestamps,
stale-index removal, corrupt ownership markers and preservation of the last good
generated source after a library error.

`tools/VerifyComponentLibrary.java` created a temporary project outside the
checkout containing only the consumer sources and npm tool configuration. Its
parent POM uses an empty relative path. A private repository supplied the ordinary
producer JAR, framework and plugin; no producer source/output directory was
linked into that project. The tool checked the JAR has no Java or `.jx` sources
and verified TeaVM's actual classpath names the installed JAR. The only shared
directory is the framework-owned `node_modules` tooling. The generated browser
application is 2.2 MiB with a 3.5 MiB source map in this development build; these
numbers are observations, not a production size budget.

The producer JAR used in this run has SHA-256
`a4af02157db7d037db5fbc4be57dcd8d1df4d84df102ef6082a8ba550ad7b5f3`.
Local command logs are `/private/tmp/jx-component-library-jvm.log` and
`/private/tmp/jx-component-library-install.log`; the retained standalone project,
Maven log and site are recorded in `target/component-library-proof.txt`.

## Scope

The [ABI contract](../component-library-abi.md) states format, bounds, classpath
ownership and type-validation responsibilities. Ordinary consumer behavior stays
Java-only. React owns mounted identity, hooks, state and refs.

The browser journey checks the imported functional counter and
native class instance, typed callbacks, child content, optional props, actual DOM
identity/draft retention, DOM focus, ref detach, remount reset and offline clicks.
The first sandboxed launch did not execute the test; its escalated full-tool retry
waited for approval and was interrupted without a process. Qualification resumed
only the browser step, preserving the successful Maven output. An initial direct
attempt exposed `jwebserver` missing from PATH; the portable test configuration now
uses the same JDK server through `java -m jdk.httpserver`. The direct Chrome retry
passed without rebuilding or reinstalling the producer/consumer.

No full conformance/gate/CI, mutation campaign, browser other than Chrome, artifact
publication, IDE dependency navigation, hot library replacement, authenticated
Spring packaging or general third-party Java compatibility is claimed here.
