# Source-independent component-library proof

This G0 M1-10–M1-13 source slice `873179b` plus lifecycle fix `6ee9434` extends the initial JAR work on the
current class/checkpoint baseline `fce6423`. Its component ABI 2 is independent of
the Server Function/client-reference protocol. The earlier ABI 1/R30 branch is
preserved; no general R30 merge was introduced. The full push gate and Verify are
pending.

## Measured checks

| Check | Result |
| --- | --- |
| Core / compiler / Maven JVM tests | 102 / 78 / 17 pass; 197 total, including eleven JAR compiler and two plugin contracts |
| New complete-class JAR regressions | 2 fail before the ABI correction; both pass afterward |
| Producer/framework/plugin install in a private Maven repository | Pass, 1.913 s; existing JVM tests were not repeated |
| Standalone consumer normal Maven `package` | Pass, 2.579 s; normal `.jx` → javac → TeaVM → browser lifecycle |
| Standalone Chrome journeys | 2 passed in 1.5 s, using the saved standalone site; no page or console errors |
| Producer + consumer reactor `clean compile` | Failed with missing descriptors before the phase fix; passes in 0.944 s afterward |
| Agent setup / baseline ledger / push-step alignment | Pass; the local and CI lists contain the same 16 steps, including the standalone proof |

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

The new regressions first demonstrated that ABI 1 discarded defaultProps, typed
context lifecycles, nullable state and Presence value types; the imported omitted
prop became a materialized value instead of an absent native input. ABI 2 preserves
all ClassSpec fields and rejects version 1 on the consumer tag. Its JVM probe
inspects the real typed view/descriptor; actual React default application and
sparse-state transitions are checked separately in Chrome.

The lifecycle probe then found that a `process-classes` default never ran in the
pinned Maven 3 reactor's `compile`. The producer compiled but all three consumer
tags reported missing metadata. Moving `library` to `compile` follows Maven's
packaging javac goal and makes the same clean two-module command pass. The private
plugin was rebuilt/reinstalled for that proof; browser/runtime sources did not
change and the prior standalone site was preserved. Logs are
`/private/tmp/jx-component-library-compile-red.log` and
`/private/tmp/jx-component-library-compile-green.log`.

`tools/VerifyComponentLibrary.java` created a temporary project outside the
checkout containing only the consumer sources and npm tool configuration. Its
parent POM uses an empty relative path. A private repository supplied the ordinary
producer JAR, framework and plugin; no producer source/output directory was
linked into that project. The tool checked the JAR has no Java or `.jx` sources
and verified TeaVM's actual classpath names the installed JAR. The only shared
directory is the framework-owned `node_modules` tooling. The generated browser
application is 2.4 MiB with a 3.6 MiB source map in this development build; these
numbers are observations, not a production size budget.

The producer JAR used in this run has SHA-256
`57917e43a76fb86ea9e4fc0fecb9b2cef18edf2b435362f6999f7f73c7e31590`.
Local command logs are `/private/tmp/jx-component-library-current-red.log`,
`/private/tmp/jx-component-library-current-green.log` and
`/private/tmp/jx-component-library-current-install.log`; the retained standalone project,
Maven log and site are recorded in `target/component-library-proof.txt`.

## Scope

The [ABI contract](../component-library-abi.md) states format, bounds, classpath
ownership and type-validation responsibilities. Ordinary consumer behavior stays
Java-only. React owns mounted identity, hooks, state and refs.

The first browser journey checks the imported functional counter and
native class instance, typed callbacks, child content, optional props, actual DOM
identity/draft retention, DOM focus, ref detach, remount reset and offline clicks.
The second checks omitted native default props and state null → empty object →
partial value → own undefined → present null, preserving the other field and the
same DOM node across parent rerender, then resetting on remount. The proof used
`--package-only` and a directly authorized Chrome runner against the saved site;
no repeated Maven build was needed for the browser phase. The JDK server runs
through `java -m jdk.httpserver` so a separate `jwebserver` PATH entry is unnecessary.

Historical ABI 1 evidence at `2545bf1` on the R30 branch remains 178 JVM tests,
3.899 s install, 4.395 s standalone package and one Chrome test in 1.3 s. Those
counts are not added to the current run. That unpublished format is no longer
accepted; the original branch remains available without dropping its R30 work.

No full conformance/gate/CI, mutation campaign, browser other than Chrome, artifact
publication, IDE dependency navigation, hot library replacement, authenticated
Spring packaging or general third-party Java compatibility is claimed here.
