# Source-independent component-library proof

## Publication candidate requalification, 26 November 2026

Candidate `b89db24` preserves the seven selected ABI commits on the sparse
authoring candidate, based on accepted PR86. The seven ABI-specific commits are
retained; the current parent also includes previously qualified compiler/plugin
and gate changes beyond the earlier selected `655f062` source. This is a new
qualification of the combined candidate, not a whole-file identity claim against
that earlier source.
The selected ABI install now passes **213 JVM tests** (84 core, 11 React,
84 compiler, 24 plugin, 10 language server) plus TeaVM in **38.868 seconds**.
The source-independent consumer then passes normal Maven `package` in **9.540
seconds**, followed by **2/2 Chrome journeys in 4.1 seconds** on its saved site.
These timings are execution receipts under coordinated concurrent work, not a
performance comparison. No extra full conformance run was used for this check.

The producer JAR contains class files and ABI metadata but no producer sources;
the consumer's TeaVM classpath names the installed JAR. Its SHA-256 is
`742c8e4ac4980570b5d514dc9435d643196acc4acaa0caba74fd2b11f3797770`.
The retained proof directory is
`/var/folders/y9/29rtk6bd2dz_xj36rw2jn6g40000gn/T/jx-component-library-proof-16254659902728514045`.
Local logs are `/private/tmp/jx-abi-selected-install.log` and
`/private/tmp/jx-abi-selected-standalone.log`. The mandatory **21-step** gate
preserves the preceding 20 checks and adds this standalone proof. Final gate,
remote Verify and publication remain pending.

Commands: `./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl
jx-maven-plugin,jx-language-server,examples/component-library,examples/library-consumer -am install`,
then `java tools/VerifyComponentLibrary.java .cache/maven-repository`.

## Earlier selected publication integration, 26 November 2026

The earlier selected ABI2 source `8f54ce8` and lifecycle fix `655f062` build on the
minimal legacy-runtime → legacy-authoring → sparse-authoring stack above accepted
main `515f2c2`. Source `d2c4c1e` includes the six component-library commits only;
the original R30 work and unrelated creator/hot-protocol changes stay separate.
Accepted nullable/sparse checkpoint support and the separate Pure limitation are
preserved by the prerequisite integration tests.

| Check | Actual selected-integration result |
| --- | --- |
| Private-repository install, including LSP and both examples | 201 JVM tests pass:84 core,11 React,79 compiler,17 plugin,10 LSP;24.180 s |
| Standalone consumer normal Maven `package` | Pass,7.953 s; only installed producer JAR on TeaVM's classpath |
| Standalone Chrome | 2/2 pass,1.6 s; saved external site, not reactor output |
| Producer + consumer reactor `clean compile` | Pass,1.315 s; descriptor is available after producer javac without `package` |
| Baseline and push-list alignment | Pass;155 registered conformance cases, unchanged62 witnesses;16 matching gate steps |

Logs are `/private/tmp/jx-component-library-selected-install.log` and
`/private/tmp/jx-component-library-selected-compile.log`. The independent consumer
is `/var/folders/y9/29rtk6bd2dz_xj36rw2jn6g40000gn/T/jx-component-library-proof-6083276521364800406/consumer`;
its sibling `maven.log` records the standalone build. The receipt is
`target/component-library-proof.txt`; producer SHA-256 is
`ef1cce62e8d14b62c8d866cd4d35ba3050dd7d9425b21ea02d5c03091c5bfd8a`.
The separate Chrome runner followed `--package-only`, so no repeated package was
needed after browser approval. The complete conformance bench, mutations, gate,
Verify and publication have not run on this selected stack.

## Historical complete-class ABI qualification

The earlier G0 M1-10–M1-13 source slice `873179b` plus lifecycle fix `6ee9434` extends the initial JAR work on the
current class/checkpoint baseline `fce6423`. Its component ABI 2 is independent of
the Server Function/client-reference protocol. The earlier ABI 1/R30 branch is
preserved; no general R30 merge was introduced. The full push gate and Verify are
pending.

### Measured checks on the earlier broader baseline

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
