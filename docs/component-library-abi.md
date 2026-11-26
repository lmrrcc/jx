# Component library ABI 2

Status: the bounded M1-10–13 proof is accepted in [PR89](https://github.com/lmrrcc/jx/pull/89), merged as `248293a`. The 21-step gate and both Verify checks pass, including 231 JVM tests and the source-independent Maven/two-Chrome consumer. The complete current class metadata is retained; general release and library hot replacement remain separate. See
[measured evidence](evidence/component-library-jar.md).

An application imports a library component using ordinary Java imports and `.jx`
markup. Its compile classpath contains the producer's JAR, without `.jx` or Java
sources. The generated code calls the producer's public `JX` field and public
`Props` constructor. React still owns the mounted component instances and state.

## Producer

Bind `jx:generate` and `jx:check` as usual, then bind `jx:library` to its default
`compile` phase. For JAR packaging Maven runs its bound `compiler:compile` before
goals configured in the POM, so the library goal runs after javac and writes
`META-INF/jx/components.idx` into the module's ordinary class output. Maven's JAR
packaging includes that resource; a separate sources JAR is unnecessary. The index
is already available to the next module during a reactor `compile`, without
requiring `package`. See the [Maven lifecycle ordering](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html).

The descriptor contains a format version, component identity/kind, Props fields
in constructor order, optional/default-value semantics, generic arity and the
current compiler's class metadata, including native defaults, typed context
lifecycles, nullable aggregate state and ordered Presence fields. SHA-256 digests bind each entry to the exact
owner and nested Props class bytes. No source text, filesystem path, timestamp,
runtime state or environment configuration is serialized. Digests detect stale
or modified inputs; they do not authenticate downloaded artifacts.

ABI 2 rejects the unpublished ABI 1 prototype explicitly. Its shorter class
record lost native defaults and sparse-state metadata; those fields must not be
inferred as false/absent. Rebuild that producer with the current `library` goal.
The importing tag receives `JX120` for an unsupported version.

`ComponentLibrary.create/encode` are the compiler APIs behind publication. The
Maven goal owns only its descriptor and ownership marker, preserves timestamps
when unchanged, removes an owned descriptor when all declarations disappear,
and rejects handwritten files at the reserved output path.

## Consumer

`JxCompiler.compile(sources, classpath)` is additive; the original source-only
overload remains unchanged. Maven generate/check goals pass resolved compile or
test dependencies. A project's own class output is excluded from this dependency
index: its current sources remain authoritative over old compiled metadata.

The reader accepts class directories (reactor builds) and JARs. It validates
format, bounds, digests, public component/JX field shape, JX generic Props type,
public Props record and field ordering. It never loads application classes,
executes annotation processors or extracts an archive. Ordinary javac validates
the complete consumer types after lowering and maps failures back to `.jx`.
The class-file check compares primitive prop descriptors; reference type spelling
and the declared class-state metadata are not a complete binary signature proof.
Consumer javac and the typed runtime descriptor remain responsible for their
actual types. The index is not a substitute for artifact trust.

Missing, incompatible, duplicate or stale library contracts produce `JX120` on
the component's tag name. A failed generation leaves the previous generated
consumer sources intact. Metadata failures are associated with the referenced
binary; unrelated classpath artifacts do not become components.

Bounds: 1024 classpath entries, 1024 declarations per index, 256 props/state
fields per declaration, 64 Props type parameters, four MiB per descriptor or
class file, 64 MiB per indexed JAR/class payload, and 65536 archive entries.
The aggregate dependency catalog is limited to 4096 components and 16 Mi UTF-16
units of contract names/type text. Strings use bounded strict UTF-8. This is a versioned boundary; future compiler
metadata that changes lowering must receive an explicit compatibility decision.

## Example and qualification

`examples/component-library` publishes a functional counter with optional props,
children, callbacks and a DOM ref, plus a native class component with a checked
class ref. A third native class starts with null state and uses omitted native
default props plus sparse Presence fields. `examples/library-consumer` imports
all three through the normal Maven
dependency. Its application behavior is Java-only; no handwritten application
JavaScript or producer source copying is required.

The focused checks are compiler real-javac/JAR contracts, Maven output ownership
and original-tag diagnostics, then the packaged consumer in Chrome. The browser
journey checks local state, callback delivery, child content, focus, ref detach,
remount reset, retained DOM and operation after the page goes offline. The second
journey proves native defaults and null aggregate → empty object → partial state →
own undefined → present null while preserving another field and mounted DOM.

The standalone proof uses an explicitly supplied Maven repository containing the
installed framework/plugin and producer. For example, after preparing a private
repository at `/tmp/jx-library-repository`:

```sh
./mvnw -B -ntp -Dmaven.repo.local=/tmp/jx-library-repository -pl jx-maven-plugin,jx-react,examples/component-library -am install
java tools/VerifyComponentLibrary.java /tmp/jx-library-repository
```

The Java proof copies only consumer source and npm tool configuration into a new
temporary directory, uses the installed parent POM with no relative lookup, and
runs ordinary `package` followed by the browser journey. It verifies that the
producer JAR contains its index and no `.java`/`.jx` sources, and that TeaVM's
classpath uses that JAR. The temporary project and Maven log remain available;
`target/component-library-proof.txt` records the JAR digest, paths and actual
browser result. `LIBRARY_PORT` selects the browser port (default 59198).
`--package-only` prepares the standalone consumer and records browser status as
pending, allowing a separately authorized runner to test the saved site. The
recorded ABI 2 run used that split workflow; its two Chrome tests passed.
Verify and the local push gate invoke this standalone proof after reactor install;
`npm run test:library` can also test the already-built in-repository consumer.

This slice does not claim published Maven Central artifacts, arbitrary bytecode
producers, general class-authoring extensions beyond this compiler baseline,
automatic source navigation/decompilation in IDEs, browser compatibility of every
dependency, hot replacement of a library JAR, or an SSR/Spring packaging proof.
