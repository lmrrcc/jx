# Protected Java SSR and hydration proof

This M1-26 fixture connects the real, packaged Spring Todo API to a Node-hosted
Java renderer and an independently compiled Java browser application. It is a
bounded integration proof, not a production SSR server or a public authentication
library.

## Data and ownership

Spring authenticates `JXSESSION` and filters Todo reads and writes by its verified
principal. The framework host forwards only that named cookie to one configured
loopback backend. It never trusts a username from query parameters or headers.
`SessionApi` and `TodoApi` supply endpoint and cookie names from the shared Java
contract. The host does not place the session response in HTML.

The shared `view` JAR contains the immutable `TodoSnapshot`, its explicit JSON
schema, the generated `Todo` codec and the `.jx` view. Each server request captures
its own snapshot before rendering. The two TeaVM entry points compile this JAR
separately: `server` supplies React's Node pipeable renderer; `client` hydrates
the snapshot already in the document. Credentials, CSRF tokens and server-only
code do not belong in that snapshot or the browser artifact.

The host waits for all content before starting HTTP delivery, so this fixture
does not claim progressive streaming. The application suspends on a real React
boundary while test controls hold rendering. Disconnect and deadline handling
abort the request-owned renderer and any pending backend fetch. Native Node
`Writable` completion, rather than renderer readiness alone, determines success.

## Reproduce

From the repository root, after installing the locked npm dependencies:

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl :todo-api,:protected-ssr-server,:protected-ssr-client -am package
npm run test:ssr-protected
```

The test starts the real Boot JAR in an otherwise empty temporary directory on
port 4885 and the Node host on port 4884. Both are owned by Playwright and stopped
afterward. Its fixture identities use the launcher's explicit test-only password;
there is no built-in production password. The scheduling controls require a fresh
random process-only capability and are never included in application assets.

The browser scenarios check:

- Two authenticated requests, completed in reverse order, retain independent
  immutable snapshots even if the backend changes after capture.
- Script delimiters and Unicode separators in private data remain data; one
  user's response never includes the other's records, session or CSRF credentials.
- Hydration retains server DOM nodes and makes no second API request. Local Java
  state updates continue after the browser is taken offline.
- Disconnecting a suspended request releases its owner before the deadline;
  late release cannot revive it or affect another authenticated request.
- Anonymous requests and uncredentialed test controls fail; the client bundle
  does not import the server renderer or contain server configuration.

This does not establish production proxy/TLS deployment, distributed sessions,
cache policy, streaming private data after a session is revoked, or a general
Spring SSR starter. Those require separate contracts and qualification.
