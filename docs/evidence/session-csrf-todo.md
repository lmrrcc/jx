# Spring session, CSRF and private Todo workspaces

Original source `c658cea`, M1-21, 26 November 2026. This is a bounded example
integration with optional Spring dependencies. PR81 acceptance is recorded below.

`todo-contract` owns `SessionApi` paths/cookie/header names and the immutable
`SessionInfo(username, csrfToken)` response. Its generated browser codec is the
same strict record mechanism used by Todo; the contract JAR depends on neither
Spring nor browser code. An empty username denotes an anonymous session. Tokens
are masked, session-bound and excluded from record string representations.

The server uses Spring Security's actual
[form authentication](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html)
and [session CSRF protection](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
It does not implement a password/session protocol in the browser. The repository
pins Boot 4.1.2 and its Security 7.1.2 BOM; no dependency upgrade is part of this slice.

| Operation | Contract |
| --- | --- |
| `GET /api/session` | Public no-store identity and masked CSRF token |
| `GET /api/session/login` | Escaped, server-rendered native HTML form |
| `POST /api/session/login` | Spring credentials + `_csrf`; session ID rotation; relative 303 on success or failure |
| `POST /api/session/logout` | Valid CSRF required; invalidates session and clears cookie; relative 303 |
| `/api/todos` | Authenticated Principal owns every row read/create/update/delete; client never supplies owner |

`JXSESSION` is host-only, Path=/, HttpOnly and SameSite=Lax; the local HTTP fixture
uses Secure=false. Sessions expire after 15 minutes of inactivity. Password hashes
are mandatory server configuration: `JX_DEMO_PASSWORD_HASH` for `demo`, optionally
`JX_OTHER_PASSWORD_HASH` for `other`, using Spring's `{id}encodedValue` format.
There is no built-in account password. Test launchers supply explicitly test-only
credentials; neither those values nor JDBC/server configuration enter the bundle.
For TLS hosting set `JX_SESSION_SECURE=true`. This example security chain belongs
to the application; no framework starter silently replaces a host chain.

Flyway V3 adds an owner to every row and indexes owner/time/id. Historical rows are
retained under `legacy-unassigned`; they are never silently assigned to a newly
authenticated user. Existing installations must deliberately assign such data in
a trusted server migration if needed. Capacity is 500 rows per account. Version
checks and ownership are enforced together in SQL; foreign IDs receive the same
409 as unavailable/stale versions without revealing another account's data.

Java/.jx renders a session indicator and native logout form. A mutation's Elm
command gets the current masked token then sends one JSON request with the typed
HTTP header API. The command owns cancellation of either stage, ignores disposed
responses and never retries mutations. A 401 clears the current protected Todo
model. Login/logout uses full native navigation, creating a fresh browser realm;
no password or session cookie is copied into a Java store or localStorage.

Development explicitly opts in with `-Djx.apiOrigin=http://127.0.0.1:8181`
and `-Djx.apiSessionCookie=JXSESSION`. The proxy still validates exact request
origin, Host, normalized API paths and payload/deadline bounds, and contacts only
that configured loopback backend. It strips all other cookies and Authorization.
It forwards one bounded CSRF header and only relative normalized Location values;
it never follows redirects. Selected backend cookies with Domain, a non-root path,
missing HttpOnly/strict-enough SameSite or duplicate attributes are rejected before
publishing the response. Secure is preserved. Host cookie scope intentionally spans
the backend/dev ports; it does not isolate unrelated applications on one hostname.

## Local evidence

- Scoped reactor package passes **169 JVM tests** and compiles the actual TeaVM
  application. Tests cover header bounds/ownership, proxy credentials and redirects,
  real login and CSRF filters, session ID rotation, stale/missing tokens, logout,
  two identities and denial without mutation. The final proxy validation also
  rejects encoded dot segments in backend locations.
- Existing authenticated Todo checks pass **4/4 Chrome** (including all twelve
  exact-value vectors); empty-directory packaged JAR checks pass **2/2 Chrome**.
- The native form test initially exposed a missing explicit Spring parameter name
  (500 on GET login), then an absolute failure redirect rejected by the dev proxy.
  Both have focused HTTP regressions; the proxy policy was retained.
- `npm run test:auth` runs the actual HTML forms and Java client against both the
  packaged JAR and the actual Maven development goal. It checks bad credentials,
  cookie flags/rotation, CSRF denial, independent users, foreign-ID mutation denial,
  logout/invalidated captured sessions and absence of server secrets in bundles.
  **2/2 Chrome scenarios pass in 11.6 seconds**, including actual Maven startup.
  The final backend's four HTTP checks pass in 4.739 seconds; the narrowed proxy
  reactor passes in 11.164 seconds. Existing Todo and packaged runs took 4.8 and
  4.1 seconds respectively. These timings are run records, not performance claims.

The auth suite is required by Verify/pre-push (17 steps on this branch). No blanket
production security or cross-browser acceptance is claimed. OIDC, credential/user
management, rate/session-count policy, timed expiry/BFCache/cross-tab recovery,
identity-generation cancellation of all caches/streams, context prefixes, distributed
sessions and deployment TLS/proxy configuration remain later tasks. M2-10 remains
partial/planned beyond the login/logout/CSRF journeys demonstrated here.

## Publication acceptance

[PR81](https://github.com/lmrrcc/jx/pull/81) merged as `8795b35d8d7c81c7dc495bab67bda0833d4534d5`. Exact head `cb279b0` passed all 17 local gate steps in 451 seconds and both [PR Verify](https://github.com/lmrrcc/jx/actions/runs/45819857426) and [push Verify](https://github.com/lmrrcc/jx/actions/runs/45819628862). The integrated reactor passes 204 JVM checks and builds TeaVM in 43.189 seconds. Browser verification passes 47 runtime checks, 975 conformance checks with 217 explicit exclusions, four Todo scenarios, two packaged scenarios, two authentication hosting modes (10.6 seconds) and four real source-save hot workflows. The inventory remains 149 cases and 62/6328 registered pairs. These results supplement the original source qualification; they do not broaden the authentication/hosting limits above.
