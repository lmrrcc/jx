# M1-30: independent artifact consumer

The local proof at source commit `daa4345`, based on merged `main` `f4b87ba`,
passes on 26 November 2026. It has no dependency on the queued component-library
ABI or RSC metadata work. The bounded proof is accepted in PR84 after the publication gate and both Verify checks; see the integrated receipt below.

## Exact boundary

`VerifyArtifactConsumer.java` first checks that the installed candidate matches
the checkout's packaged parent POM and core, React, compiler and Maven-plugin
JAR/POM bytes. It copies those nine files and the publisher seed's third-party
Maven artifacts into a separate file repository, recording SHA-256 and producing
Maven checksums. No other JX module is admitted to that repository. The source
seed can be warm; the consumer's local Maven repository is initially absent.
Before freezing that repository, the proof explicitly resolves the parent's
managed Maven clean plugin in the publisher seed through its `help` goal. The
publisher preparation may use its configured repositories; it does not run
`clean` or touch application outputs. An ordinary reactor `install` does not
resolve this plugin. The first publication gate reproduced the missing-plugin
failure in its colder seed, so the proof no longer relies on an earlier local
`clean` invocation. The preparation log is retained with the other receipts.

The consumer is created outside the checkout from only authored fixture files
and the Maven Wrapper scripts/properties. Its parent uses `<relativePath/>`.
There are no source/output links, copied generated Java, classes, browser bundles
or shared `node_modules`. The normal `clean package` lifecycle performs `.jx`
generation and checking, javac, TeaVM and the Maven browser goal.

Both user and global Maven settings are explicit. A `mirrorOf=*` file URL is the
only Maven resolution source, including third-party dependencies and build
plugins. The first build deliberately does **not** use `-o`: Maven must transfer
artifacts from that file repository into the empty local cache. A second
`-o clean package` rebuilds generated outputs from the newly populated cache.
These meanings follow Maven's [single-repository mirror contract](https://maven.apache.org/guides/mini/guide-mirror-settings.html#using-a-single-repository)
and [CLI settings/offline options](https://maven.apache.org/ref/3.9.11/maven-embedder/cli.html).

Every resolved JAR/POM must belong to the initial inventory, retain its original
size/SHA-256, match the file-repository bytes and record only `jx-proof-file` in
Maven's `_remote.repositories` provenance. The prepared repository's final
JAR/POM set must equal its initial set. Separate validator controls reject a
late artifact addition and changed resolved bytes. A second prepared repository
omits only the JX Maven-plugin JAR; a separate empty cache must fail to resolve
that exact artifact before source generation. Neither prepared repository is
modified during its Maven run.

The consumer has its own pinned npm lockfile and initially empty npm cache.
`npm ci` uses the public npm registry and creates independent dependencies.
**This is not an offline npm bootstrap.** Java, the Maven Wrapper distribution,
Node/npm and Chrome are provisioned tools, outside the Maven-artifact claim.
Ambient Maven/Java launch extensions and Node module/preload settings are removed
from child processes; the real toolchain and ordinary proxy environment remain.
This configuration is a build-isolation proof, not an operating-system sandbox.

## Actual results

| Check | Result |
| --- | --- |
| Candidate reactor install | 166 JVM tests: 80 core, 11 React, 60 compiler, 15 Maven plugin; 18.506 s |
| Inventory validator controls | Valid inventory accepted; added repository/resolved artifacts and changed bytes rejected |
| Missing-plugin control | Expected failure, `dev.jx:jx-maven-plugin:jar:0.1.0-SNAPSHOT` absent in the file mirror; 0.053 s |
| First file-only `clean package` | Passed, 2.812 s |
| Offline `clean package` | Passed, 2.413 s |
| Browser interaction | Chrome 1/1 passed in 1.2 s; evidence-copy rerun 1/1 in 2.2 s |
| Distribution provenance | 991 staged Maven JAR/POMs; 429 actually resolved, all initial-inventory hashes and file-origin checks pass |

The integrated candidate exposed a publisher-seed gap in its first mandatory
gate: steps 1–19 passed, then the file-only consumer correctly rejected the
missing managed clean plugin. With explicit publisher preparation, the new
isolated proof passes both `clean package` runs (5.771 s and 4.302 s) and Chrome
1/1 (1.8 s). It stages 966 artifacts and resolves 429; all initial hashes and
file-origin checks pass, including the clean plugin. These figures describe
this seed; the historical table above remains unchanged. The corrected candidate subsequently passes the complete publication gate and both Verify checks, as recorded below.

The browser asserts two independent component states, queued functional updates,
keyboard activation and preservation of the actual output DOM node. After assets
load it disables networking and continues operating both counters. Unexpected
browser/console errors fail the test. The Java-owned HTTP server binds an
ephemeral loopback port, serves only the built site and closes after the check.

[Candidate artifact hashes](artifact-consumer-jx.tsv) identify the nine JX files
used for this run. These are candidate-build receipts, not a reproducible-byte
or signed-release promise. The complete staged/resolved manifests, separate
Maven logs and receipt are retained under `target/artifact-consumer-reports/`;
the Verify workflow uploads that evidence even on failure. The full isolated
project, repositories and npm installation remain in the temporary directory
identified by `target/artifact-consumer-proof.txt`.

## Reproduce

With the documented JDK, Node/npm and Chrome toolchain:

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository install
npm run test:consumer
```

The existing Verify and local push gate now include `npm run test:consumer`.
The script is Java automation and owns all child-process deadlines. It refuses
an installed JX candidate whose bytes differ from the current packaged files.
Third-party preparation is bounded at 20,000 artifacts, 128 MiB per artifact
and 2 GiB total; no settings, credentials, cache-tracking files or symlinks are
copied from the publisher seed. Different seeds can contain different unused
artifacts; the resolved inventory is recorded separately.

For diagnosis, `java tools/VerifyArtifactConsumer.java <publisher-repository>
--package-only` leaves browser qualification explicitly pending. The preserved
site can be tested with `java tools/VerifyArtifactConsumer.java --browser-only
<proof-directory>` without rebuilding or reinstalling. `--self-test` runs only
the inventory controls. These diagnostic modes are not replacements for the
complete gate command.

## Remaining scope

M1-30 proves this local file-distribution rehearsal. GA-09 still needs verified
namespace ownership, supported artifact identities, publication/signing and an
independently reproduced release path. This run does not publish to Maven
Central, establish offline npm/toolchain provisioning, qualify Windows or a
second browser, provide a Spring starter, or exercise development hot reload.
The standalone consumer intentionally uses already merged functional
component APIs; source-less third-party component metadata is the separate
M1-10–M1-13 proof.

## Publication acceptance

[PR84](https://github.com/lmrrcc/jx/pull/84) merged as `20267ab0d94d6dc4579f09f8b49b4ad10151af32`. Exact head `1551e80` passed all 20 local gate steps in 503 seconds and both [PR Verify](https://github.com/lmrrcc/jx/actions/runs/45852805256) and [push Verify](https://github.com/lmrrcc/jx/actions/runs/45852684431). Integrated 206 JVM checks plus TeaVM, 47 runtime checks, 975 conformance checks with 217 exclusions, four real hot workflows and all packaged/auth/SSR/MV3 examples pass. The 149-case inventory and 62/6328 interaction count remain unchanged.

The final isolated consumer stages 966 Maven artifacts and resolves 429. Missing-plugin rejection, exact inventory/hashes and file-origin checks pass; the first file-only clean build takes 3.612 s and the offline clean rebuild 4.599 s. Chrome passes 1/1 in 897 ms. Publisher clean-plugin preparation is explicit and occurs before the frozen repository is constructed. The first gate's missing-seed failure remains recorded above; no consumer network fallback was introduced. All release and provisioning limits above remain.
