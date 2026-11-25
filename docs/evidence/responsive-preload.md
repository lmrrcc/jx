# Responsive image preload options

R-31-01 closes the Java API gap for React DOM's `imageSrcSet`, `imageSizes` and
`referrerPolicy` preload options. `Resources.Options` adds immutable copy methods
and retains the original seven-argument constructor. Its new canonical constructor
also rejects image settings on a non-image resource kind. React owns resource
deduplication; JX does not cache or normalize candidate URLs itself.

The compiling consumer is
[`ImagePreload.jx`](../../tests/conformance/src/main/jx/jx/conformance/cases/ImagePreload.jx).
The shared [browser case](../../tests/conformance/specs/image-preload.spec.js) uses
actual React and JX browser bundles with a local image response fixture. It checks:

- Candidate requests change from the small image to the large image as sizes change;
  no fallback URL is requested.
- Equivalent source-set/sizes calls with different fallback URLs reuse the same
  actual link node; changed sizes create a second responsive resource.
- The browser omits Referer for `no-referrer` while the legacy constructor's
  preload retains the normal same-origin Referer.
- Image attributes, ordinary preload attributes and absent options agree with
  React. React normalizes anonymous CORS to `crossorigin=""`.
- The behavior also runs after hydration and lazy route loading. Static mode
  compares the two implementations' complete initial HTML byte for byte.

Local evidence, 25 November 2026:

```sh
./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl tests/conformance -am package
npm run build:bench
npm run build:bench-server
npm run build:bench-lazy
BENCH_PORT=59181 node_modules/.bin/playwright test -c tests/conformance/playwright.config.js -g image-preload --workers=1
java tools/CheckBaseline.java
```

Package passed, including two new JVM construction/immutability tests. The focused
browser run passed eight tests in 12.1 seconds: both implementations in client,
hydrate, static and lazy modes. The first two attempts exposed shared test
assumptions about CORS normalization and interception-only request headers; the
React reference and JX behaved alike. The final assertion checks the observable
Referer header and does not claim unavailable Fetch Metadata headers were tested.

Integrated source `0f41000` over FormStatus candidate `1b65e70` also passes package
(42 core, two React options, 48 compiler and 15 plugin checks) and the complete
bench: **887 passed, 169 skipped, 132 cases**. Client is 130 reference / 129 JX;
hydrate 99, static 105 and lazy 110 per side. Only the `image-preload` case is
added to the recorded results; interaction evidence remains 54/6328.

Both `hint-image-sizes-dropped` and `hint-referrer-policy-dropped` were killed
by `image-preload` after a passing unmutated baseline. They are the preload quota
of an exclusive five-mutant run: 5 killed, 0 survived, 0 unusable. The integrated
candidate `e63ea76` passed 905 checks /175 skipped across 135 cases, with 54/6328
interaction pairs unchanged. The six preload implementation/consumer files are
byte-identical to the qualified slice. Scratch evidence:
`jx-mutation-7730846870036546774`. The historical full campaign remains 100. The local push gate and Verify remain pending.
The browser check is Chrome evidence, not cross-browser or GA qualification.

Compatibility: existing constructor and accessor descriptors remain available.
Adding record components changes the record's structural equality and textual
representation. Source-set parsing and referrer-policy token interpretation remain
browser behavior; this API does not claim to validate their complete grammars.
