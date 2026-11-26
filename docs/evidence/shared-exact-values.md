# Exact shared values through Spring and the browser

Source: `9c0a233`, 26 November 2026, on the M1-17/M1-19 shared-record slice.
M1-18 is locally verified; publication gate and Verify remain pending. No new
React feature-pair credit or general Java-library certification is claimed.

The dependency-free [ExactValues record](../../examples/todo-contract/src/main/java/jx/example/contract/ExactValues.java)
and its vectors are compiled by both consumers. Twelve cases include long min/max,
positive and negative values at and adjacent to 2^53, zero and small integers.
BigDecimal cases include a value with a 30-digit integer part and 9 fractional
digits, scaled zero, negative fractional values, negative scale and trailing zeros.

Field policy is explicit: long and decimal values always travel as canonical
strings, never through JS Number. BigDecimal precision/scale are bounded and scale
is part of equality. Required nullable `note` is different from optional `extra`;
absent, present-null, empty string and nonempty text remain distinguishable. JSON
undefined is not invented. Todo identifiers remain UUID strings.

The browser's [Java proof owner](../../examples/todo-app/src/main/java/jx/example/todo/ExactValuesRun.java)
starts only after a React commit, cancels its owned HTTP request on disposal and
compares decoded immutable values. It also computes decimal unscaled values and
scale in compiled Java. Playwright observes actual outgoing JSON string types,
all boundary IDs and missing/null presence; it does not mock HTTP or JSON parsing.
The [Spring adapter](../../examples/todo-api/src/main/java/jx/example/api/ExactValuesController.java)
is enabled only by the `contract-proof` profile and runs the same typed numeric
construction before Jackson writes the response. JSON numeric tokens, missing
required-nullable fields, noncanonical text and invalid optional-field types fail
with HTTP400. The existing public Todo routes retain their original validation.

Executed on this source:

- Scoped reactor package passed in 21.405 seconds: **161 JVM checks**, including
  pure exact-value round trips/rejections and a real Spring HTTP test, plus actual
  TeaVM/browser bundling. Same command as the [Todo record proof](shared-todo-contract.md).
- `npm run test:todo` passed **4/4 Chrome scenarios in 6.0 seconds**: the three
  existing complete Todo tests and the new twelve-vector Spring/browser proof.
  `npm run test:contract` is a convenient selector for that same new scenario;
  the existing Verify `test:todo` step already includes it.

Publication assembly `b71d26f` also includes the accepted class journal, prepared
resource/route slices and the record-codec plugin-discovery fix `c6e110e`.
Its scoped reactor package passes **187 JVM checks in 19.651 seconds** (80 core,
11 React, 8 Elm, 64 compiler, 18 plugin, 2 contract, 3 real Spring HTTP and 1 Todo
model), including actual TeaVM compilation and bundling. The unchanged four Todo
Chrome scenarios pass **4/4 in 4.2 seconds** against that assembly on port4581.
Both subprocess plugin-discovery regressions and the source-less generated-code
consumer are included. These are integrated local results; the publication gate,
remote Verify and merge are still pending, and the earlier 161-check result is
retained as evidence of the original scoped branch.

Limits: the exact-value adapters are explicit, not generated support for these
types. The first `record-codecs` generator remains required String/bool/int only.
No universal Jackson policy, arbitrary recursive schema, numeric money convention,
old/new wire compatibility, authentication, published artifact or browser other
than Chrome is qualified. See [ADR0009](../decisions/0009-shared-record-wire-contract.md).
