# Portfolio old-UI baseline

Evidence for [GA-02](../ga-02-portfolio.md). A dated record of one measurement
session on 2026-11-21, not a standing claim. Surfaces and journeys are in the
[surface register](portfolio.tsv) and [journey register](journeys.tsv); the
`lighthouse:` names in the journey register refer to rows below.

## Environment

| Item | Value |
| --- | --- |
| Machine | Apple M5, 10 cores, 16 GiB, macOS 26.5.1; other applications open |
| Network | Home connection in Switzerland; not throttled at the operating-system level |
| Browser | Google Chrome 157 headless, driven by Lighthouse |
| Lighthouse | 12.8.2 through `npx`, Node.js 20.20.2; benchmark index 4520–4687 |
| Settings | Mobile form factor, default simulated throttling, performance and accessibility categories |

Lighthouse 13 needs a newer Node.js than this machine has; 12.8.2 was used instead.

## Which public addresses respond

One plain GET per address, following redirects.

| Address | Result |
| --- | --- |
| `lmrr.cc`, `juglugano.ch`, `stack-sensei.com`, `medusa-custody.com` | 200 |
| `gandalfprotocol.com`, `gandalfwallet.com`, `gandalfsystem.com` | 200 |
| `luca4j.com` | 200; redirects to `www.luca4j.com` |
| `lumaspect.com`, `stacktrace.cc` | 200; the root page redirects to a language prefix in script |
| `tetra-protocol.fly.dev`, `medusa.io` | Host did not resolve |

## Deployed public pages

Command, once per run, three runs per page:

```sh
CHROME_PATH="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
npx --yes lighthouse@12.8.2 <url> --quiet --no-enable-error-reporting \
  --only-categories=performance,accessibility --output=json \
  --output-path=<file> --chrome-flags="--headless=new"
```

All 54 runs exited 0. The three `luca4j` pages were measured later in the same
session, after the owner named the site as a pilot. Values are the median of three; the LCP range shows the
spread. LCP and TBT are Lighthouse's simulated mobile values in milliseconds, not
field data. Sizes are transferred KiB for the whole page load.

| Name | Page | Perf | A11y | LCP ms (range) | CLS | TBT ms | Total KiB | Requests | Script KiB |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| lmrr-home | `lmrr.cc/` | 75 | 96 | 8037 (1367–8486) | 0 | 0 | 1418 | 7 | 3 |
| lmrr-blog | `lmrr.cc/blog/` | 100 | 96 | 1392 (1381–1541) | 0 | 0 | 61 | 6 | 3 |
| lmrr-docs | `lmrr.cc/docs/` | 100 | 95 | 1467 (1400–1541) | 0 | 0 | 53 | 6 | 3 |
| lmrr-article | `lmrr.cc/docs/03-backend-architecture/circuit-breakers-and-resilience-patterns-with-resilience4j` | 90 | 100 | 2340 (2090–6724) | 0 | 249 | 827 | 17 | 765 |
| jug-home | `juglugano.ch/` | 89 | 100 | 3798 (3658–3824) | 0 | 0 | 448 | 11 | 9 |
| sensei-web | `stack-sensei.com/web/` | 57 | 82 | 8719 (8456–8859) | 0 | 38 | 2605 | 8 | 1183 |
| sensei-terms | `stack-sensei.com/terms` | 58 | 100 | 7728 (7662–8382) | 0 | 31 | 1279 | 7 | 1183 |
| medusa-custody | `medusa-custody.com/` | 92 | 95 | 2718 (2713–2766) | 0.041 | 0 | 82 | 5 | 0 |
| gandalf-protocol | `gandalfprotocol.com/` | 100 | 100 | 1430 (1367–1595) | 0 | 0 | 48 | 5 | 3 |
| gandalf-wallet | `gandalfwallet.com/` | 100 | 95 | 1295 (1260–1478) | 0 | 0 | 45 | 4 | 0 |
| gandalf-system | `gandalfsystem.com/` | 100 | 97 | 1152 (1110–1164) | 0 | 0 | 57 | 3 | 0 |
| lumaspect-home | `lumaspect.com/it/` | 92 | 95 | 2610 (2610–3674) | 0 | 96 | 487 | 9 | 385 |
| lumaspect-about | `lumaspect.com/it/about/` | 94 | 94 | 2413 (1217–2420) | 0.004 | 67 | 290 | 7 | 210 |
| stacktrace-home | `stacktrace.cc/en/` | 97 | 100 | 2116 (2110–2374) | 0 | 0 | 316 | 8 | 165 |
| stacktrace-privacy | `stacktrace.cc/en/privacy/` | 97 | 100 | 2131 (2109–2194) | 0.001 | 0 | 318 | 9 | 169 |
| luca4j-home | `www.luca4j.com/` | 100 | 100 | 1410 (935–8588) | 0 | 0 | 1530 | 12 | 110 |
| luca4j-privacy | `www.luca4j.com/privacy/` | 100 | 100 | 1391 (1224–4372) | 0.016 | 0 | 713 | 12 | 111 |
| luca4j-module | `www.luca4j.com/video/java-da-zero/` | 84 | 100 | 4351 (1827–4357) | 0 | 0 | 717 | 11 | 110 |

Reading these numbers:

- The static pages (`lmrr.cc` listings, the three Gandalf sites) load with about
  50–60 KiB and almost no script. A JX replacement that ships React and a TeaVM
  runtime to these pages would be a regression unless it emits HTML without them.
- The prerendered React sites (Stacktrace, Lumaspect, luca4j) send 110–385 KiB of
  script. The `luca4j` pages also varied widely between runs.
- Stack Sensei's public pages download 1183 KiB of script before showing content.
- `lmrr-home` and `lmrr-article` varied widely between runs; treat their medians as
  rough. Two `jug-home` runs warned that the page loaded too slowly to finish
  within the time limit, so its values may be incomplete.
- `medusa-custody` is not the Medusa React application.

## Local production bundles

Sizes are bytes; gzip is `gzip -c` at the default level.

| Surface | Source of the files | JavaScript raw / gzip | Largest script raw / gzip | CSS raw / gzip | WASM raw / gzip |
| --- | --- | --- | --- | --- | --- |
| `medusa.app` | Built for this record from the working tree at `08c35436` with its 49 uncommitted frontend changes | 3,317,573 / 833,242 (2 files) | 3,171,173 / 785,871 | 159,510 / 22,274 | 149,140 / 76,941 |
| `sensei.app` | Existing `dist` of 2026-11-17 19:22; frontend clean at `afb8904` | 5,115,250 / 1,388,827 (8 files) | 3,827,720 / 1,017,667 | 249,080 / 37,962 | none |
| `gandalf.extension` | Existing `dist` of 2026-10-11, **older than** the extension's last change on 2026-11-14 | 1,532,184 / 409,970 (4 files) | 1,127,895 / 314,655 | 139,532 / 22,196 | 19,836,783 / 6,743,688 |

How the Medusa build was isolated: the frontend sources were copied to a temporary
directory without `node_modules`, `dist` or environment files; `node_modules`,
`backend` and `docs` were linked read-only; then
`node scripts/build-website.mjs <empty output directory>` ran in the copy. It
exited 0 and its own artifact check passed (220 files). The TypeScript check that
precedes it in `npm run build` was not run. The Medusa checkout had the same 300
uncommitted entries before and after.

Stack Sensei and the Gandalf extension were not rebuilt. Stack Sensei's build runs
a script that rewrites a tracked file, and the extension build writes generated
WASM bindings into its source tree.

## Behavior of the pilot candidates, from source

Not executed. Each line is what a replacement must reproduce.

| Journey | Data and guard | Failure cases to preserve |
| --- | --- | --- |
| `lmrr.blog-index`, `lmrr.docs-index` | Static HTML generated from Markdown; no script needed | Hidden sections and articles stay out of pages, sitemap and robots rules |
| `lmrr.article` | One template; Prism and Mermaid loaded from CDNs; every built article has a diagram | Readable without script; diagrams and highlighting degrade to text |
| `luca4j.privacy`, `luca4j.module` | Prerendered by a Java script after the Vite build; hydrates over the static HTML; local fonts; theme stored and overridable by query | Static HTML must stay meaningful; forms never claim a message was received |
| `stacktrace.legal` | Prerendered per language; canonical and hreflang; hydrates for theme, language and menu | Root page language choice with a no-script fallback; 404 page |
| `gandalf.protocol-home` | Static HTML; headers forbid form actions and network connections | Print, reduced-motion and keyboard behavior covered by its Playwright tests |
| `medusa.changes-list` | Session, access snapshot and selected workspace; one GET | Session change cancels the request; recovery-only users are redirected |
| `medusa.search` | Same guard; one GET; form edits query parameters only | Empty and failed search states |
| `sensei.leaderboards` | Public GET; personalized when a token is present | Anonymous and signed-in states; API failure |
| `sensei.sessions` | Token, verified session and active subscription | Expired token redirects to login; inactive subscription redirects to payment |
| `gandalf.ext-activity` | Renders from wallet state held by the popup; no network, storage or WASM call | Locked wallet; popup close and reopen restores the view stack |

## Not done

- No authenticated journey was run. Medusa, Stack Sensei, the JUG application and
  the Lumaspect console need their backends; that capture belongs to the pilot work
  packages.
- No form was submitted and no login attempted on any production site.
- No desktop-profile, Firefox, WebKit or real-device measurement.
- No field data. None of the sites exposes it to this repository.
- Screenshots were not taken.
- Raw Lighthouse reports are in a temporary directory and are not committed; the
  command above reproduces them.
- `medusa.signer`, `tetra.*`, `jug.app`, `lumaspect.console` and `sensei.admin` have
  no measurement.
