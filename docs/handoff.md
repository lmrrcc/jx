# JX handoff report — 24 November 2026

For whoever continues the work, human or AI. It states where the project is, how
the work is done here, what is finished, what is open and in which order to take
it. The governing documents stay [AGENTS.md](../AGENTS.md), the
[roadmap](ga-roadmap.md) and the [task list](ga-tasks.md); where this report and
those disagree, check the repository and fix the document that is wrong.

State described: `main` at `14d913a` (pull request #35), Verify green.

## 1. What JX is

A Java framework for complete React browser applications: components are written
in Java and `.jx` markup, compiled to Java, then by TeaVM to JavaScript that runs on
the real React 19 runtime. The goal Luca set on 21 November 2026 is **all React
features**, proven by a differential bench against plain React, before the existing
products are migrated as pilots.

Modules: `jx-core` (public `jx.*` API, no TeaVM), `jx-react` (the TeaVM bridge to
React), `jx-compiler`, `jx-maven-plugin`, `jx-language-server`, `jx-elm`,
`jx-router`, `jx-browser`, the examples, and the test fixtures under `tests/`.

## 2. Numbers on `main`

| Evidence | Result |
| --- | --- |
| React feature list ([conformance.tsv](evidence/conformance.tsv)) | 120 rows: 89 supported, 15 equivalent, 0 unsupported, 16 excluded |
| Bench, client mode | React 104 of 104; JX 103 of 103 (one case only inventories React's exports) |
| Bench, hydrate mode | 79 of 79 on both sides |
| Bench, static HTML mode | 85 of 85 on both sides; JX markup equals React's exactly |
| Curated mutants ([mutation-results.tsv](evidence/mutation-results.tsv)) | 100 of 100 killed (full run on `abe0c32`; the one mutant changed since was rerun alone) |
| Lazy-module fixture | 4 of 4 Chrome tests |
| Unit tests through Cherry Test (22 November) | 82 tests, 77.6% line coverage of five modules, 15 of 427 methods at CRAP 30 or more |

"100 of 103" style counts are coverage of the listed scenarios, not a percentage of
GA. Supported rows carry option-level limits, written in the `note` column.

## 3. How the work is done

1. One task is one pull request. Branch from `main`, implement, push, wait for the
   `framework` check, squash-merge. `main` is protected; nothing is pushed to it
   directly.
2. `git push` runs the local gate (`tools/PrePush.java`, the same 14 steps as
   `.github/workflows/verify.yml`). It takes about ten minutes and refuses to push
   when the working tree is not exactly the commit. Do not edit files while it runs.
3. A React feature is done when: the JX API exists with Javadoc; a bench case exists
   on both sides (`tests/conformance/reference/cases/*.jsx`, registered in
   `reference/cases.jsx`, and `src/main/jx/jx/conformance/cases/*.jx`, registered in
   `Main.component`); one spec in `tests/conformance/specs` passes on both;
   `conformance.tsv` has the row updated; results are recorded with
   `BENCH_UPDATE=1 npm run test:bench`; `docs/language.md` describes it;
   `docs/ga-tasks.md` flags it; at least one mutant in
   `tests/conformance/mutants.tsv` is killed (`java tools/MutateBench.java --only id`).
4. Commit messages end with the `Co-Authored-By` trailer of the model in use; pull
   request bodies end with the Claude Code line.

Things that cost time and are easy to repeat:

- **Bench port.** The bench server uses port 4573, the mutation runner 4574. If a
  bench server is already running by hand, set `BENCH_PORT=4575` for bench runs and
  for `git push`.
- **Mutation runs must run alone.** Under load a mutant can be "killed" by a
  timeout. Run `java tools/MutateBench.java` with nothing else going, about an hour
  now that it covers three rendering modes. Only a full run writes
  `mutation-results.tsv`; `--only` does not.
- **A pending state that lasts an instant passes naive tests.** Three tests needed
  a second check 250 ms later (transitions, form status). Assert that a pending
  state is still there, not only that it appeared.
- **Stale mutants** are now caught by `CheckBaseline`: each target text must occur
  exactly once. When a line a mutant targets is edited, update `mutants.tsv`.
- **Generated cases.** `java tools/GenerateDomTables.java` writes the element,
  attribute and event cases from React DOM's own tables; `--check` runs in Verify.
  Rerun it after a React upgrade.
- **Rendering modes.** A case that cannot run in hydrate or static mode is listed in
  `tests/conformance/modes.tsv` with the reason. The reason comes from what React
  itself does in that mode, never from a JX failure.
- **Evidence files end with one newline** and are tab-separated; the checker
  rejects anything else.
- Sibling product checkouts under the workspace directory are read-only. Nothing is
  built, installed or committed there.

## 4. Done

### Foundation
- GA-01 baseline and GA-02 portfolio inventory with pilot decisions.
- M1-01 to M1-05: bench skeleton, feature list from React's exports, cases for
  everything JX had, mutation runner.
- Protected `main`, Verify workflow, local push gate.

### React parity (R-01 to R-25, R-32)
- Props: typed, optional, default, generic, spread; children utilities,
  `cloneElement`, `isValidElement`; child positions (R-32).
- Hooks: state, reducer (lazy too), effects (layout, insertion, effect events),
  refs (objects, callbacks, imperative handles), memo, context, external stores
  with server snapshots, ids, debug values, transitions, deferred values,
  `use` for promises and contexts, action state, optimistic values, form status.
- Components and APIs: `memo`, portals, `flushSync`, profiler, nested Strict Mode,
  Suspense, `lazy` and `LazyComponent`, `Activity`, `ViewTransition`, root options,
  form actions, raw HTML through `RawHtml.unsanitized`, resource hints.
- **Async in Java:** `jx.Promise`, `Async.run` and `Async.await` on TeaVM's
  suspendable threads.
- **Server:** `ReactServer.renderToString`, `renderToStaticMarkup`, both streaming
  renderers, `resume`, `resumeToPipeableStream`; `ReactStatic.prerender` and the
  resume-and-prerender pair; `React.hydrate`. The compiled application runs as a
  Node host.
- **Generated coverage (R-25):** 165 elements, 390 attribute names, 91 event handler
  names compared with React.
- **Rendering modes (R-26, two of three):** hydrate and static HTML for the whole
  bench.

### G0 proofs
- M1-22, M1-23: a second JX module loaded on demand;
  [decision 0005](decisions/0005-lazy-module-boundary.md).
- M1-24, M1-25: render in Node, hydrate in the browser.

### Tooling
- Cherry Test configuration for unit tests with JaCoCo coverage and CRAP scores
  ([tooling guide](tooling.md)).

### Defects found by the bench and fixed
- Null and collection children shifted later siblings (R-32).
- `Style` lost the order of its properties (found by static mode).
- A flaky size-limit test in the development proxy.

## 5. To do, in recommended order

### A. Finish what is half done
1. **R-26, lazy-route mode.** Now unblocked by M1-22 and M1-23. Decide what the
   mode means for a bench case (the case component loaded from a second module
   through `LazyModules`), add `reference-lazy` and `jx-lazy` projects like the
   other modes, list non-applicable cases in `modes.tsv`. Update the R-26 row, which
   still says the mode waits for M1-22.
2. **Lazy chunks in the Maven plugin.** The two-entry esbuild run exists only in
   the fixture's npm script. `ComponentType.lazy` therefore still does not split an
   ordinary application's bundle.
3. **M1-26:** protected data route under server rendering: two simultaneous
   identities, immutable snapshot, abort, no second fetch. This is the request
   isolation that every server-rendering row lists as missing.

### B. Remaining React parity
4. **R-27:** combined scenarios so that every pair of features meets at least once.
5. **R-28:** class components authored in Java (`Component` lifecycle,
   `PureComponent`).
6. **R-29:** small surface: `act` for JX tests, `captureOwnerStack`, version
   constants, `unstable_batchedUpdates`, `unstable_useCacheRefresh`.
7. **R-30:** React Server Components (`cache`, `cacheSignal`, directives, protocol),
   behind a feasibility proof and a decision record.
8. **R-31:** parity gate. Needs zero unsupported (true today), only approved
   exclusions, and **every option-level limit implemented or explicitly accepted**.
   The limits recorded so far: `useActionState` permalink; `useFormStatus` action
   field; `ViewTransition` event callbacks; `preload` image settings; `FormData`
   file fields; stream options (nonce, namespace, chunk size, headers);
   `onDefaultTransitionIndicator`; custom-element properties and events.

### C. The other G0 proofs (M1, none started)
9. **M1-10 to M1-13:** component library as a JAR: metadata descriptor, resolving
   components from classpath JARs, producer and consumer examples, version errors.
10. **M1-14 to M1-16:** one cancellation type, a `useResource` hook, router loader;
    a decision record on async ownership. `Promise` and `Async` already exist and
    should be the base.
11. **M1-17 to M1-19:** shared contract with the Spring API, exact-value vectors,
    generated browser codec. The same generator would serve the lazy boundary.
12. **M1-20, M1-21:** packaged Spring JAR that serves the UI; session login and CSRF.
13. **M1-27:** MV3 extension popup under its CSP. Note for it: the async bridge
    uses no `eval`, but check `import()` and the TeaVM output under the extension CSP.
14. **M1-28 to M1-31:** editor scale and latency, structural edit proof, and the G0
    gate review.

### D. Test tooling
15. **TT-01 rest:** integration and browser test kinds in Cherry Test.
16. **TT-04:** PIT through Cherry Test. Blocked: the installed Cherry Test 0.3.16
    has no built-in PIT. Updating it is Luca's decision.
17. **TT-05:** Cherry Test looks for `mvnw` in each module directory; JX has one at
    the root, so a `mvn` on the `PATH` is needed today.
18. **TT-06:** quality thresholds in the gate; limits must be agreed first.
19. **TT-02, TT-03:** visual tests and a bench gallery page.

### E. Language and developer experience
20. **DX-01 to DX-05:** components without the class envelope, object-literal syntax
    in markup, `var` and static imports as the documented style, `console.log`,
    documentation on hover in the editors.

### F. Later milestones
M2 to M12 in the task list are planned slices: application skeleton, complete
Spring integration, website rendering, developer platform, portfolio pilots,
extension hardening, release. None is started. They are re-cut when their
milestone starts.

## 6. Decisions waiting for Luca

- Update Cherry Test, to unblock PIT (TT-04).
- Coverage, CRAP and PIT limits for the gate (TT-06).
- Which Stack Sensei route and which website pages go first as pilots.
- Whether to keep the `<h2>Hello World!!</h2>` line in the counter example.
- Approval of the option-level limits in section 5, item 8, or the instruction to
  implement them.

## 7. Known weak points

- **The async bridge names two TeaVM runtime functions** (`$rt_startThread`,
  `$rt_nativeThread`) inside `@JSBody` scripts. They survive minification today
  because TeaVM renames them consistently, but a TeaVM upgrade must recheck them.
  The bench cases `async-function` and `transition-promise` fail if they break.
- **`await` inside a callback that React runs synchronously** (for example the
  action of `flushSync`) is not detected and misbehaves.
- **Coverage figures count JVM unit tests only.** Most of `jx-core` and all of
  `jx-react` run through TeaVM and are measured by the bench, not by JaCoCo.
- **The Maven plugin's `execute` methods have no unit tests** (the worst CRAP
  scores); the example browser runs exercise them.
- **Production builds are unmeasured.** Every bundle in the repository is
  unminified; no size or performance budget is claimed.
- **One browser.** Everything runs in Chrome only.

## 8. Try it

```sh
java tools/CheckAgentSetup.java
java tools/CheckBaseline.java
npm ci
java tools/GenerateDomTables.java --check
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository install
npm run test:bench      # three rendering modes, both sides
npm run test:lazy       # the second module loaded on demand
npm run test:ssr        # only the server cases
```

One case by hand: `node tests/conformance/serve.js`, then
`http://127.0.0.1:4573/jx/?case=<id>` and `/reference/?case=<id>`; add
`&mode=hydrate` only through the tests, which supply the server markup.
