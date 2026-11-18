---
name: build-engineering
description: Bootstrap or maintain builds, dependencies, quality gates, CI, packaging, and release preparation for JVM, Rust, and web repositories. Use for build infrastructure or artifact changes.
---

# Builds and artifacts

Read the repository guide and existing manifests, wrappers, toolchain declarations,
lockfiles, CI, and scripts. Preserve the established build ecosystem. Do not infer
a JDK, Maven/Gradle choice, package manager, or automation language from this skill.

## Reproducible setup

- Use the configured wrappers/toolchains and verify their integrity through the
  supported checksum mechanism. Keep language/compiler/target versions explicit.
- Pin dependency/plugin versions directly or through verified BOMs/parents and
  ecosystem lockfiles. Avoid dynamic release dependencies and accidental snapshots.
  Follow the repository's lockfile policy for applications versus libraries.
- Keep processor/plugin/build dependencies separate from runtime dependencies.
  Do not add Lombok, MapStruct, a Rust proc macro, or frontend plugins without a
  concrete requirement and compatible toolchain configuration.
- A new checkout must not depend on sibling workspace paths, unpublished cached
  artifacts, developer credentials, or absolute local paths. Document required
  network access, services, Docker, SDKs, and browser provisioning.
- Use the repository's language for new custom automation. Standard wrappers, CI
  steps, and existing external tools are valid; do not import another project's
  Rust-only, Kotlin-only, or shell prohibition automatically.

## Quality gates that actually run

- Bind selected formatting, compilation, static analysis, tests, and necessary
  integration checks into executable build/CI targets. Do not claim an unconfigured
  tool or a planning document is an enforced gate.
- For Maven, bind both Failsafe integration-test and verify when using Failsafe.
  For Gradle, connect intended test/source-set tasks to the relevant lifecycle.
  For Cargo, choose workspace/target/feature scope deliberately. For web projects,
  distinguish typecheck, lint, unit tests, browser tests, and bundling.
- A successful package with tests skipped is not a verified release. Required CI
  should fail when required tests cannot run; keep optional/environment-specific
  checks and their coverage limits explicit.
- Start with scoped verification, broaden for affected consumers and releases,
  and update documentation when commands change. Do not invent profiles/tasks.
- Generated outputs need deterministic inputs, tool versions, diagnostics, and a
  regeneration strategy. Fix the source/generator, not hand-edited output.

## Packaging and release preparation

- Test a consumer of the actual packaged artifact. Verify resources, service
  registration, native/FFI metadata, and browser assets where applicable.
- Keep library dependencies minimal; avoid accidentally shipping an executable
  fat JAR, a forced logging backend, private files, caches, or development assets.
- Compare public API/ABI against the previous release when one exists. Before a
  first release, compile representative consumers and check documented behavior.
- Verify promised platform/feature combinations. Do not claim GraalVM/native-image,
  WASM, offline, cross-platform, or reproducible-byte support from a single build.
- Prepare appropriate changelog/migration notes, licenses/dependency inventory,
  sources/API docs, and artifact metadata. Support performance claims with separate
  reproducible measurements.
- Build and inspect concrete artifacts before a publication decision. Uploading,
  deploying, signing with private credentials, or making external releases needs
  authorization for the action; continue already authorized work without asking again.
