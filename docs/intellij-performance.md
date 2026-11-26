# IntelliJ 0.3.1: presentation and repeated-analysis fixes

The following is the historical four-file baseline. The later
[G0 100-component measurement](evidence/intellij-editor-scale.md) records
actual native completion/diagnostic timings, closed-file structural rename,
the syntax-parser isolation fix and the remaining SDK undo qualification limit.

The 2026-11-19 report showed large tinted Java regions, weak markup colors and
slow editing with plugin 0.3.0. The installed plugin and IDEA build were checked:
this was 0.3.0 on `IU-261.25134.95`, not an older package accidentally left installed.

## Confirmed defects

The injected Java host did not implement IntelliJ's `InjectionBackgroundSuppressor`
contract. Consequently, the daemon painted the theme's injected-language background
over all copied Java ranges, including declarations and whole method bodies.
The host now suppresses that background only. Java syntax highlighting, inspections,
diagnostics and mapped edits remain enabled. Other injections and the user's global
color scheme are unchanged. The public marker is checked by the pinned SDK's actual
injected highlighting pass; package compatibility is checked with Plugin Verifier.

Markup previously used generic `DefaultLanguageHighlighterColors.MARKUP_*` keys.
Those do not necessarily carry the active theme's HTML syntax colors. JX now has
local color keys with HTML tag, attribute, value and entity fallbacks. Optional
overrides are available under **Editor → Color Scheme → JX**. Native theme tests
exercise Default and Darcula, including a JX-only override which leaves HTML intact.

Incremental highlighting had a separate correctness defect: the lexer reported
state zero everywhere, allowing IntelliJ to reuse cached tokens after an edit
changed the surrounding Java/markup context. A real editor-highlighter regression
reproduced stale tag colors after changing `<span>hello</span>` to
`<span/>hello</span>`. The lexer now exposes safe synchronization points only in
neutral outer Java; nested contexts reconstruct through prefix replay. No hash or
unbounded state-ID cache is used. Inverse edits and nested lambda braces are covered
by `JxIncrementalHighlightingTest`.

The projection cache also compared whole-file snapshots with injection-host text.
IntelliJ can keep leading/trailing whitespace outside the host, so an unchanged
file repeatedly missed the cache and recompiled its sibling batch. In addition,
global PSI invalidation recompiled unrelated modules after edits. The service now:

- Uses the same host-relative snapshot for indexing and injection.
- Reuses content-keyed module projections and bounded completion-copy variants.
- Tracks JX PSI, VFS, project roots and indexing-state changes for invalidation.
- Preserves invalidation after sibling-prop changes, file add/rename/delete and
  closed-file refresh; temporary completion text cannot replace canonical source.
- Runs compiler work outside cache locks. Retention is bounded by entry count and
  source/generated-text weights, including an allowance for source-map segments.

The audit also corrected class-rename edits to add the host's offset within the
file, preserving leading whitespace and constructor locations.

Packages containing only `.jx` declarations now participate in Java package lookup,
including ancestor packages and scoped source-directory enumeration. This avoids
false unresolved-package errors for imports from `src/main/jx`; package names also
refresh after unsaved declaration edits. Native import and package tests exercise
these paths without substituting generated files for the authored sources.

## Evidence and limits

`JxProjectionPerformanceTest` uses four JX files in two Maven roots, three warmups
and twenty repeated unchanged-host queries on this workstation with Java 25.
The initial implementation failed the deterministic reuse assertions. Recorded
before/after timings for the same fixture:

| Repeated projection lookup | Median | 95th percentile |
| --- | ---: | ---: |
| Before | 78.876 ms | 154.602 ms |
| After | 0.012 ms | 0.019 ms |

These measurements describe a cache lookup/analysis path, not end-to-end typing,
completion latency, cold startup, or a general IDE speedup. Timings are recorded
evidence, not fragile test thresholds. Identity/invalidation assertions establish
that unchanged requests reuse compiler results and changed sources do not return
stale projections. Editing a JX source still reprojects its changed module batch.

`JxPresentationTest` runs the real injected daemon passes and paints IntelliJ's
editor component in light and Darcula schemes. It verifies the Java-line background
matches the active scheme and that native Java syntax and type errors remain.
PNG outputs are under `tooling/intellij/build/reports/presentation/`. They are
headless editor renders, not screenshots of the user's live IDE session.

A read-only stack sample of the live IDE caught it idle: the event thread was
waiting for events and no JX compiler frame was active. It therefore cannot prove
the cause of every reported pause. Startup logs also showed a completed scan of
119,761 project files. No claim of universally fast editing or complete Java/TSX
feature parity follows from these scoped fixes.

Reproduce the focused checks from `tooling/intellij` with Java 25:

```sh
./gradlew --gradle-user-home ../../.cache/gradle \
  '-PideaLocalPath=/Applications/IntelliJ IDEA.app' test \
  --tests jx.intellij.JxProjectionPerformanceTest \
  --tests jx.intellij.JxPresentationTest \
  --tests jx.intellij.JxThemeHighlightingTest \
  --tests jx.intellij.JxIncrementalHighlightingTest \
  --tests jx.intellij.JxPackageResolutionTest
```

The [tooling guide](tooling.md) records the complete suite, package verification
and supported IDEA version. The [architecture study](decisions/0004-intellij-mixed-language-editing.md)
separates these improvements from deeper structural language integration.
