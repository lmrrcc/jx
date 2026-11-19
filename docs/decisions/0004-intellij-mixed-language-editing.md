# 0004 — IntelliJ mixed-language editing and the TSX comparison

Status: incremental implementation; complete Java feature parity remains a target,
not an established capability. This supplements decision 0002.

## Required experience

An authored `.jx` file must behave like Java source with structural HTML/component
expressions. Java completion in a few injected ranges is necessary but insufficient:
ordinary editor actions, navigation, outline, folding, comments, formatting and
refactoring need an explicit source-preserving implementation and action-level tests.

## What was inspected

The IntelliJ IDEA 2026.1.3 / build 261.25134.95 Java and JavaScript plugin APIs were
inspected using `javap`, alongside the JX plugin registrations, parser, projection,
injection, references, search integration and existing SDK tests.

In `javascript-plugin-frontend.jar`, `TypeScriptJSXParserDefinition` extends
`JavascriptParserDefinition` and supplies `createJSParser()`. The TypeScript
expression parser participates in primary-expression parsing. Critically,
`JSXmlLiteralExpression` extends both `JSLiteralExpression` and `HtmlTag`, and its
implementation extends `XmlTagImpl`. TSX therefore has actual XML-backed expression
PSI; its seamless behavior is not obtained by coloring angle brackets or simply
injecting an unrelated language into an opaque host.

The Java plugin has a different boundary. Its older
`com.intellij.lang.java.parser.JavaParser` is deprecated. The replacement
`com.intellij.java.syntax.parser.JavaParser` is experimental, while its
`ExpressionParser` and `PrattExpressionParser` classes are final. Public visibility
does not establish a stable supported hook for adding a Java expression grammar.
Declaring a Java language dialect alone does not make markup valid Java syntax.

Primary references (opened and reviewed on 19 November 2026):

- [File View Providers](https://plugins.jetbrains.com/docs/intellij/file-view-providers.html)
  describes one document exposing multiple PSI trees, including JSPX Java/XML/JSP
  trees with outer-language elements.
- [Language Injection](https://plugins.jetbrains.com/docs/intellij/language-injection.html)
  describes context-bound injected fragments, treated as separate files.
- [JetBrains template-language guidance](https://intellij-support.jetbrains.com/hc/en-us/community/posts/204145604-Inject-custom-language-into-JS-CSS-code)
  recommends a view provider for template support. This older advice must be
  checked against current APIs; it does not itself prove a JX implementation.
- [JetBrains dialect discussion](https://intellij-support.jetbrains.com/hc/en-us/community/posts/360010814699-Strategy-to-support-two-dialects-for-same-programming-language-in-my-plugin)
  explains shared parser/PSI infrastructure and specialized expression dialects.

## Current boundary and incremental decision

JX currently retains a host PSI node spanning the authored source, and injects a
complete typed Java projection through copied source slices. That is a legitimate
platform API and preserves enclosing Java scope, expected prop types and callback
types. It does not make the host a Java file or provide the host's missing editor
services automatically. Generated helper declarations also must not appear in the
authored file's structure, navigation targets, folds or source edits.

Keep this working Java semantic integration while implementing host services using
supported extension points. Structure view uses Java declaration PSI, but includes
only declarations whose identifiers map exactly to copied authored ranges. Its
navigation opens the `.jx` document at the mapped identifier. Folding uses exact
authored Java brace-token mappings and markup spans from the shared JX parser.
Neither service treats a synthetic diagnostic anchor as an editable source range.
Native host actions and unsaved document updates must be tested through registered
platform services, not only direct injected-PSI assertions.

A multi-root `FileViewProvider` is a candidate for a later structural prototype,
not an automatic route to parity. JX markup produces Java expressions: removing it
as outer-language whitespace leaves invalid `return ;` syntax and loses typed
component contexts. A replacement must demonstrate source-preserving expression
PSI, scopes, type inference, indexing, completion, rename/undo and recovery before
the existing projection is retired. Do not fork or monkey-patch Java parser globals
to hide this incompatibility.

## Verification and remaining work

`JxStructureAndFoldingTest` exercises the host Structure registration, declaration
filtering, navigation to authored method positions, unsaved edits, actual editor
fold regions, and native collapse/expand actions. These checks supplement existing
Java completion, diagnostics, navigation, search and bounded rename tests.

The staged architecture still needs a richer authored markup PSI (the current host
is opaque), dedicated coverage for additional native Java refactorings and quick
fixes, and a verified strategy for full structural Java/XML integration. Formatting
must preserve meaningful markup whitespace and avoid edits through generated Java
wrappers; passing a formatter test is not arbitrary-refactoring compatibility.
Track individual behavior and tested IDEA versions in the tooling guide. No claim
of universal Java, XML, JSX/TSX or other-IDE parity follows from these changes.
