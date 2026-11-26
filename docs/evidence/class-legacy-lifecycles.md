# Native legacy class lifecycles

R-28-03h2a. This is the explicit typed runtime slice for the three deprecated
lifecycle methods and their three `UNSAFE_` counterparts. Compiler declaration
sugar is R-28-03h2b.

## Selected publication integration, 26 November 2026

The final selected source `7ab6a2f` is rebased above consumer candidate `1551e80`.
The reactor package passes **182 JVM tests** (84 core, 11 React, 65 compiler,
22 Maven plugin) and compiles the TeaVM consumers in **35.405 seconds**. The
complete matrix passes **1,001 checks / 223 declared exclusions / 153 cases** in
**4.5 minutes**: client 151 reference / 150 JX, hydration 111, static 117 and lazy
122 per implementation. The recorded conformance table adds exactly the four
legacy cases below; all previous rows are unchanged. The interaction table is
unchanged at **62 / 6,328** pairs. The publication gate and remote Verify remain
pending. These local results do not count as a new mutation campaign.

The source, native reference and shared spec retain the previously qualified
selected runtime semantics. Compiler discovery and advanced hot protocols are
separate candidates.

### Earlier selected focused qualification

Sources `b0fbead`/`a4aaa64` select only the legacy runtime on accepted main
`515f2c2`. The existing nullable/sparse checkpoint admission and committed
journal are preserved; the historical blanket rejection is not reinstated.
No creator changes or hot protocols 4/5 are included.

The selected reactor package passes **169 JVM tests** (83 core, 11 React,
60 compiler, 15 Maven plugin) in 27.692 seconds. An additional integration
contract then passes with all **4 legacy JVM tests**, proving default props,
nullable state, codec identity and pending-null dispatch survive both orders of
legacy modifier composition without mutating the current peer. This is codec
admission/composition evidence, not a new browser checkpoint-restoration claim.

All four differential cases pass **26 Chrome checks / 6 declared mode exclusions**
in 48.0 seconds on port 59185. Initial static HTML, hydration and lazy mounting
remain explicit mode contracts. This earlier focused run left the full result
tables unchanged; the subsequent complete integration is recorded above.
Two runtime mutants were killed in the historical `cc929bb` campaigns; those
results are not a new mutation campaign on this selected integration.

Logs: `/private/tmp/jx-legacy-runtime-integrated-build.log` and
`/private/tmp/jx-legacy-journal-composition.log`. The focused Playwright reporter
printed the 26/6 result; it does not rewrite aggregate result tables for a partial
run. No unrelated capability was imported to make this slice compile.

## Public contract

`ClassComponent` exposes `componentWillMount()`,
`componentWillReceiveProps(P)` and `componentWillUpdate(P,S)`, plus each
`UNSAFE_` spelling. Each is selected independently by its `Lifecycle` flag.
The native type installs only declared methods, and extends actual
`React.Component` or `React.PureComponent`. React decides when to invoke them;
JX does not simulate lifecycle ordering or suppress native warnings.

The context-aware policies are immutable descriptor modifiers:

```java
descriptor.withContextWillReceiveProps(theme, LegacyVariant.UNSAFE,
    (peer, nextProps, nextContext) -> { /* typed pending input */ });
descriptor.withContextWillUpdate(theme, LegacyVariant.DEPRECATED,
    (peer, nextProps, nextState, nextContext) -> { /* typed pending input */ });
```

The requested `Context<T>` must be the exact descriptor context. Null arguments
fail at construction; a different context fails explicitly. The pending context
value itself may be null. A policy replaces only its selected spelling, leaving
the other spelling and existing context/Pure/derivation/checkpoint settings intact.
The peer's current props, state and context are not overwritten before dispatch.
Pending values pass through the declared schemas; callback failures propagate.
No unchecked cast, dynamic property map or private React structure is involved.

These callbacks run during rendering. React may retry or abandon their work;
they must not acquire external resources or be treated as commit notifications.
Mount/receive callbacks can enqueue native state updates; will-update callbacks
must not call `setState`. Native derived-props or snapshot APIs suppress legacy
callbacks. A derived-error callback alone does not suppress them. Supported
deprecated behavior remains distinct from React 19's removed legacy context API.

## Historical first qualification

The [Java consumer](../../tests/conformance/src/main/java/jx/conformance/cases/ClassLegacy.java),
[native React reference](../../tests/conformance/reference/cases/class-legacy.jsx)
and [shared spec](../../tests/conformance/specs/class-legacy.spec.js) exercise:

- `class-legacy`: deprecated then UNSAFE mount queues; pending versus current
  values; queued receive updates before Pure/SCU; skipped render with committed
  callbacks/current state; force updates; pending context bypass; retained DOM.
- `class-legacy-suppression`: derived props and snapshots each suppress all
  six callbacks while preserving committed lifecycle methods; derived errors
  alone leave legacy callbacks active. Exact expected development warnings remain
  observable, and any additional page/console error fails the browser case.
- `class-legacy-interrupted`: initially absent content mounts into Suspense,
  queues state and suspends. Abandoning it leaves the public ref detached and
  never commits its callbacks or unmount method, even after its gate resolves.
  A later keyed attempt commits its own fresh state and callbacks. Two bounded
  fixture-owned promises are settled by explicit events, without timing sleeps.
- `class-legacy-server`: actual Node `renderToString` processes both mount
  queues before HTML, preserves modern suppression, and invokes no committed
  lifecycle or update callbacks. Its three other modes are explicitly `n/a`;
  the browser cases separately cover hydration, static HTML and lazy mounting.

Three JVM tests check variant dispatch, metadata preservation, typed policy
precedence, schema/context guards, current-value retention and original exception
identity. They do not substitute for native scheduling tests.

The package passes **72 core / 11 React / 54 compiler / 15 Maven-plugin tests**
and compiles the actual TeaVM browser/server/lazy programs. The four new cases
pass **26 checks**, with **6 n/a** cells: three cases in all eight projects and
the server trace on both implementations. Static projects compare initial HTML
only. This is Chrome evidence, not browser-wide or IDE qualification.

Logs: `/private/tmp/jx-class-legacy-complete-build.log` (23.460 seconds) and
`/private/tmp/jx-class-legacy-complete-focused.log` (26.7 seconds). The first
package found a fixture call to a nonexistent React factory; it was corrected
to the existing `new Context<>(...)` API before qualification. No runtime
behavior or warning filter was changed to make the reference pass.

At the first qualification, two curated mutations were registered but unrun:
`class-legacy-unsafe-mount-aliased` and `class-legacy-next-context-stale`.
They target native callback selection and the pending context passed to user
code. No new pair witness or aggregate result-table credit is claimed yet.

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl tests/conformance -am package
npm run build:bench
npm run build:bench-server
npm run build:bench-lazy
npm run build:bench-production
BENCH_PORT=59191 node_modules/.bin/playwright test -c tests/conformance/playwright.config.js -g 'class-legacy'
```

## Direct `.jx` authoring — R-28-03h2b

### Selected publication qualification, 26 November 2026

Final selected source `815b47f` above legacy candidate `d1899eb` passes **195 JVM
tests** (84 core, 11 React, 68 compiler, 22 Maven plugin, 10 language server)
plus TeaVM in **27.912 seconds**. The complete React/JX comparison passes
**1,009 checks / 223 declared exclusions / 154 cases** in **4.0 minutes**:
client 152 reference / 151 JX, hydration 112, static 118 and lazy 123 per side.
Only the `class-legacy-authoring` row is added to the result table; all preceding
rows and the **62 / 6,328** interaction result are unchanged. The publication gate
and remote Verify are still pending. The compiler mutant execution below remains
the earlier two-mutant campaign, not a new execution on this final candidate.

### Earlier selected focused qualification

Source `8092a1d` adds only the compiler/consumer slice on the selected legacy
runtime. That earlier package passes **183 JVM tests** (84 core, 11 React,
63 compiler, 15 Maven plugin, 10 language server) in 40.633 seconds, including
the nullable-checkpoint modifier regression inherited from the runtime branch.
The new consumer passes **8/8 Chrome projects** in 16.9 seconds: native React
and JX in client, hydration, static HTML and lazy modes. Expected native legacy
warnings are asserted; they are not filtered away to make the case pass.

Build log: `/private/tmp/jx-legacy-authoring-integrated-build.log`. The focused
command uses port 59185 and `-g class-legacy-authoring --workers=1` with the
shared conformance configuration. Its reporter leaves aggregate result tables
unchanged. `CheckBaseline` validates all 154 registered cases. The subsequent
full matrix and scoped mutation qualification are recorded separately in this
document; publication gate and Verify remain pending. No new pair witness or
native IDE qualification is claimed.

### Declaration contract and historical qualification

The compiler discovers all six lifecycle declarations on direct normal and Pure
class components. It validates public/protected instance visibility, `void`,
concrete bodies, exact declared parameter types and the absence of method type
parameters. Receive/update methods may have one ordinary override and one typed
next-context overload. The latter requires `contextType` and takes precedence
only for its selected spelling. Unsupported fields, ambiguous/extra overloads,
wrong arity/types, static/private methods and removed legacy context members
remain source-located `JX111` errors. Java attribution continues to check bodies
and checked exceptions.

`ClassSpec.contextLifecycles` is an immutable set restricted to the four typed
receive/update capabilities. It requires matching native flags and context
metadata. The previous four-, seven-, eight- and nine-argument metadata
constructors remain; record component enumeration, patterns and printed shape
expand. The generated descriptor composes typed adapters before native defaults,
preserving the independent lazy schema holder and existing `defaultProps` flag.

The compiling [normal class](../../tests/conformance/src/main/jx/jx/conformance/cases/AuthoredLegacy.jx),
[Pure class](../../tests/conformance/src/main/jx/jx/conformance/cases/AuthoredLegacyPure.jx)
and [parent](../../tests/conformance/src/main/jx/jx/conformance/cases/ClassLegacyAuthoring.jx)
exercise all six declarations. The normal class has ordinary overrides that fail
if incorrectly chosen, typed overloads, context and an omitted prop receiving its
native default. The Pure class uses ordinary overrides. The shared
`class-legacy-authoring` case proves native queued state, callbacks, no Pure
render on equal fields, pending versus current context including null, retained
DOM and exact development warnings. Static mode compares initial HTML only.

On the combined source baseline `cc929bb`, the package passes **82 core /17 React
/62 compiler /15 Maven-plugin tests** and real TeaVM compilation (29.464 s).
The new case passes **8/8 React/JX projects** (10.5 s). Three new compiler tests
compile both executable and editor projections against the actual core, run the
generated descriptors, verify defaults/policy composition in both modifier
orders, and map body type errors and editable expressions to original `.jx`
locations. The former negative test for a supported mount callback now tests
React's removed `getChildContext` instead. No test is disabled.

Logs: `/private/tmp/jx-class-legacy-authoring-compiler.log`,
`/private/tmp/jx-class-legacy-authoring-build.log` and
`/private/tmp/jx-class-legacy-authoring-focused.log`. No separate full run or result
table update is claimed. The new `class-legacy-authoring-context-lost` mutant was
unrun at that qualification; its later selected-source execution is below. Full
reference/JX integration was pending at that point; the later full result is
recorded above, while publication remains pending. The active
interaction scope stays 113 features/6,328 candidate pairs, with no added witness.

### Selected compiler mutation qualification — 26 November 2026

An isolated campaign on selected source `a225add` first passes the unmodified JX
bench in all four rendering modes, then compiles the
`class-legacy-authoring-context-lost` mutation. Omitting the generated typed
`UNSAFE_WILL_RECEIVE_PROPS` overload is **killed by `class-legacy-authoring`**.
This source also contains the subsequent sparse-authoring slice; it is not a new
execution on the earlier standalone legacy head. No native IDE, new interaction
pair, complete reference/JX comparison or publication gate credit follows.

The command selects this mutant and `class-authoring-nullable-lost` together:
`java tools/MutateBench.java --only class-legacy-authoring-context-lost,class-authoring-nullable-lost --keep`.
Both compile and fail their intended browser cases: **2 killed, 0 survived,
0 unusable**, process exit 0. This paragraph credits the one legacy mutant only.
The scratch directory ends in `jx-mutation-14847958175598351148`; the generator
source is restored and byte-identical to `a225add`, but its retained compiled
outputs still contain the final sparse mutant and must not be reused as valid
artifacts. The original worktree/cache are unchanged. The historical whole-catalog
mutation table is deliberately not rewritten by this scoped campaign.

## Remaining scope

The compiler and explicit Java consumers do not establish native IDE completion,
production-mode warnings, all possible
concurrent interruption sequences or checkpoint compatibility for these methods.
Default props and general sparse/nullable authoring have separate acceptance.
No supported class option is silently marked not applicable.

The public [React Component reference](https://react.dev/reference/react/Component)
describes the supported legacy methods and their caveats. The pinned React 19.3.1
client and server implementations determine the exact dual-spelling order,
modern-method suppression and pending context used in the differential fixtures.
