# Scoped class-journal mutation qualification

26 November 2026. **Three killed, zero survived, zero unusable**, after passing
unmodified baselines on integrated source `5b2b024`. These runtime and actual
development-loader checks are outside the conformance `MutateBench` catalog.
They do not change the historical full-catalog result or interaction pair credit.
The broader R-28-04 lifecycle and checkpoint task remains partial.

The isolated scratch was `/private/tmp/jx-class-journal-mutation-integrated`, with
its own physical Maven repository. It borrowed npm dependencies read-only from a
stopped worktree with the identical lockfile. Each mutation changed exactly one
production target, rebuilt successfully through javac/TeaVM, and was restored
before the next target. No deliberately mutated artifact entered a shared cache
or a published branch.

## Baseline and direct failures

The unmodified private reactor install passed in 17.813 seconds. After the runtime
bundle, all **18 hot-class browser tests passed in 6.9 seconds**, including the
external-root/portal controls and committed-versus-abandoned owner attempts.
The real compiled-runtime drift journey passed in **26.3 seconds**.

| Mutant | Successful rebuild | Actual detecting assertion |
| --- | --- | --- |
| `class-root-membership-lost` | 16.903 s | Removing only HotReload's registered-root identity guard makes external-root capture return no error; the required ownership rejection fails. The portal positive control still passes: one failed and one passed in 2.3 s. |
| `class-owner-unmatched-cleared` | 24.807 s | Changing `return value !== accepted;` to `return false;` in the committed-attempt filter loses the abandoned candidate. After a later equal-user-props pulse, capture incorrectly succeeds; the required unmatched-candidate error fails at `hot-class.spec.js:164`. |
| `compiled-runtime-attestation-lost` | 19.478 s | Replacing the Maven bootstrap's compiled runtime marker read with constant `2` causes actual `capture` and `dispose` calls before rejecting a candidate rebuilt with runtime version 1. `hot.spec.js:166` observes `["capture", "dispose"]` instead of `[]`; the test fails in 11.3 s. |

The loader's first diagnostic run stopped at its error-channel assertion before
the existing teardown assertion. The test now checks the same zero-capture/
zero-disposal invariant first; no assertion was removed or weakened. The exact
reordered test passed against unmodified production after a fresh 17.255-second
install, in **29.0 seconds**, before the final mutant rebuild and direct failure.
This separates a real premature teardown from a mere difference in error text,
a timeout, or a broken build.

## Reproduction boundary

Reserve an exclusive local build/browser window and use a private Maven cache.
Compile and run the unmodified baseline before applying one target at a time:

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl jx-maven-plugin,tests/runtime-browser -am install
npm run build:runtime
env RUNTIME_PORT=59190 node_modules/.bin/playwright test -c tests/runtime-browser/playwright.config.js tests/runtime-browser/tests/hot-class.spec.js --workers=1
env HOT_COUNTER_PORT=59192 HOT_TODO_PORT=59189 node_modules/.bin/playwright test -c tests/hot-reload/playwright.config.js -g 'compiled runtime drift' --workers=1
```

After each individual rebuild, the first mutant selects
`external React root|portal outside`, the second selects `equal user props`, and
the third repeats the real loader command. Each target must fail its actual
invariant; a compilation, browser startup or transport failure is unusable.
Restore the single production change before moving to the next mutant.

Local browser artifacts are `/private/tmp/jx-journal-root-mutant-results`,
`/private/tmp/jx-journal-candidate-mutant-results` and
`/private/tmp/jx-journal-attestation-mutant-ordered-results`. The last includes the
real development-server log showing successful builds and the browser failure.
Baseline artifacts use `/private/tmp/jx-journal-baseline-runtime-results` and
`/private/tmp/jx-journal-baseline-loader-ordered-results`. These local artifacts
are not permanent CI evidence or performance measurements.

All production source patches were reversed and campaign processes stopped.
The scratch's private generated artifacts must be rebuilt before reuse because
the final installed plugin was deliberately mutated. Only the test assertion
order and this evidence are intended for integration. Publication gates and
broader hidden/Pure/nullable checkpoint qualification remain separate work.
