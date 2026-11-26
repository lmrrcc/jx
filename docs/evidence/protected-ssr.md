# M1-26: protected Java SSR and hydration

Status: **bounded M1-26 proof accepted in PR82 after the publication gate and Verify**. The final
fixture source is `7c2c0aa`, based on the session/CSRF proof `eacf6df`. This closes
the local protected-route experiment, not general SSR hosting or the R-30/R-31
tracks. The [fixture guide](../../tests/protected-ssr/README.md) gives the exact
commands and module boundaries.

## Executed evidence — 26 November 2026

| Check | Result |
| --- | --- |
| Maven package, real Spring API plus independent server/client TeaVM modules and dependencies | 171 JVM tests passed; browser output and executable Boot JAR built; 36.372 seconds |
| Server package after correcting React's `charSet` property | 158 JVM tests passed and server TeaVM output rebuilt; 22.048 seconds |
| `npm run test:ssr-protected`, final source | 3 Chrome tests passed; 4.0 seconds including fixture startup |
| Workflow/local push step alignment | 18 matching required steps, including the new protected SSR test; this check does not execute the full gate |

The first package includes 63 core, 10 React, 8 Elm, 1 browser, 58 compiler,
22 Maven-plugin, 2 shared-contract, 2 snapshot, 1 Todo-model and 4 real Spring
HTTP tests. The final narrower package excludes Elm, Todo-model and Spring HTTP
tests because only the server document's property name changed. The first browser
run also passed all three checks (5.6 seconds), but exposed the React warning
about `charset`; the final run has no such warning. Independent source review
first found and corrected reversed fallback/content arguments in `suspense`.

The run uses the pinned Java 25/TeaVM/React toolchain and Google Chrome on macOS
arm64. There is no new full conformance result, pair-coverage credit, mutation
campaign result, production benchmark or cross-browser claim in this record.

## What the checks establish

The test launches the actual Spring Boot JAR in an empty temporary directory and
authenticates two separate users through its real session endpoints. Each user
creates private domain records. The Node host forwards only the configured
session cookie to the fixed backend; Spring derives ownership from the verified
principal on both session and Todo reads. Forged username headers/query values do
not select the response owner. Anonymous access fails with HTTP 401.

Two requests capture independent immutable Java snapshots and suspend under
React's native boundary. The backend is changed after capture, then the second
request is released before the first. Each HTML response retains its own earlier
snapshot. Neither response contains the other user's record, session cookie,
CSRF token or the proof-control capability. JSON embedded as inert script data
escapes HTML delimiters and Unicode separators. A title containing closing-script
and executable-script text remains text.

The browser delays only the application asset so the test can inspect real SSR
DOM before JavaScript runs. After releasing the asset, independently compiled
Java hydrates the same record nodes, reports no hydration/browser error and makes
no second API request. Local hook updates still work after taking the browser
offline. The host records exactly one session read and one domain read per SSR
request. The client esbuild input graph contains the browser entry and excludes
the server renderer; private host configuration is absent from the bundle.

A separate real HTTP client disconnects while React is suspended. The request's
owner is removed before the five-second test bound, without reaching the
15-second server deadline. A subsequent release returns HTTP 409, and the other
authenticated user can render and hydrate successfully. The Java snapshot tests
also prove defensive copying and rejection of anonymous/unbounded models.

## Ownership and bounds

The host owns fetch cancellation, renderer abort, HTTP destination completion and
shutdown. The Java request owns its snapshot and suspension resolver. No global
application model, authenticated data cache or shared request identity is used.
Retained proof metrics contain request IDs/counters only and are bounded to 64;
active requests are bounded to 16. Input JSON is bounded to 1 MiB before parsing,
with at most 500 decoded records and bounded string fields. Cookie, response and
chunk counts are bounded; backend redirects are rejected. Controls require a
random process-only capability and are not part of the browser application.

## Remaining qualification

- This host loads data before rendering and waits for `allReady` before piping
  HTTP output. It does not prove progressive private-data streaming, HTTP 103,
  mid-response revocation or abort during an in-flight backend fetch.
- Authentication and its two example users belong to the Spring proof; this is
  not an OIDC provider, authorization library or production session deployment.
- The two-process localhost proof does not establish TLS/proxy deployment,
  distributed sessions, SSR caching, packaged renderer distribution or a general
  Spring starter. CSR's one-JAR delivery remains a separate qualified boundary.
- Chrome is the only tested browser. The bounded proof controls and host are test
  infrastructure, not an advertised production API.

The next G0 review must decide the production SSR hosting boundary using this
evidence and its operational limits. RSC transport, general server data APIs and
the later starter/hot-reload tasks retain their own acceptance criteria.

## Publication acceptance

[PR82](https://github.com/lmrrcc/jx/pull/82) merged as `968ccf34ac7ab91743a844d6ebfdb7066c44b8d0`. Exact head `20e0eaa` passed all 18 mandatory local steps in 495 seconds and both [PR Verify](https://github.com/lmrrcc/jx/actions/runs/45823288051) and [push Verify](https://github.com/lmrrcc/jx/actions/runs/45823204357). The integrated reactor passes 206 JVM checks and compiles TeaVM in 42.173 seconds; browser verification passes 47 runtime checks, 975 conformance checks with 217 explicit exclusions, four Todo scenarios, two packaged scenarios, two authentication hosting modes, all three protected SSR scenarios in 4.7 seconds and four actual source-save hot workflows. The 149-case inventory and 62/6328 interaction count remain unchanged. These results supplement the original evidence without broadening the hosting, streaming or cancellation limits above.
