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

No complete bench or mutation campaign was run for this branch. The curated
`hint-image-sizes-dropped` and `hint-referrer-policy-dropped` mutations are registered
but unexecuted. Result tables are unchanged; Verify and integration remain pending.
The browser check is Chrome evidence, not cross-browser or GA qualification.

Compatibility: existing constructor and accessor descriptors remain available.
Adding record components changes the record's structural equality and textual
representation. Source-set parsing and referrer-policy token interpretation remain
browser behavior; this API does not claim to validate their complete grammars.
