package jx.compiler;

import java.io.*;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Signature;
import java.lang.reflect.AccessFlag;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipFile;
import javax.lang.model.SourceVersion;

/** Versioned, source-independent component contracts published with compiled libraries.
 * No class is loaded or initialized. An index binds declaration metadata to the exact
 * compiled owner and Props record bytes; it is integrity checking, not artifact authentication.
 * All I/O is bounded and owned streams/archives are closed before returning. */
public final class ComponentLibrary {
    public static final String RESOURCE = "META-INF/jx/components.idx";
    public static final int VERSION = 1;
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    public static final int MAX_COMPONENTS = 1024;
    private static final int MAGIC = 0x4a58434c;
    private static final int MAX_FIELDS = 256;
    private static final long MAX_ARCHIVE = 64L * 1024 * 1024;
    private static final SourceSpan BINARY = new SourceSpan(0, 0);

    /** Digests bind the source contract to the generated Java binary. No Props digest is
     * present for a component with a no-argument render method. */
    public record Entry(ComponentMetadata component, String ownerSha256, String propsSha256) {
        public Entry {
            Objects.requireNonNull(component, "component"); validate(component);
            digest(ownerSha256);
            if (component.hasProps()) digest(propsSha256);
            else if (!Objects.requireNonNull(propsSha256).isEmpty()) throw new IllegalArgumentException("Unexpected Props digest");
        }
    }
    public record Index(List<Entry> entries) {
        public Index {
            entries = List.copyOf(entries);
            if (entries.size() > MAX_COMPONENTS) throw new IllegalArgumentException("Component index exceeds 1024 declarations");
            Set<String> names = new HashSet<>();
            for (Entry entry : entries) if (!names.add(entry.component().qualifiedName()))
                throw new IllegalArgumentException("Duplicate component in index: " + entry.component().qualifiedName());
            entries = entries.stream().sorted(Comparator.comparing(value -> value.component().qualifiedName())).toList();
        }
    }
    private ComponentLibrary() {}

    /** Creates producer metadata after javac, using only the caller's source declarations
     * and compiled class directory. The caller owns writing the returned index resource. */
    public static Index create(List<ComponentMetadata> declarations, Path classDirectory) throws IOException {
        if (declarations.size() > MAX_COMPONENTS) throw new IOException("Too many library components");
        Path root = classDirectory.toRealPath();
        List<Entry> entries = new ArrayList<>();
        for (ComponentMetadata component : declarations) {
            validate(component);
            byte[] owner = readClass(root, classPath(component.qualifiedName()));
            byte[] props = component.hasProps() ? readClass(root, propsPath(component)) : null;
            verifyShape(component, owner, props);
            entries.add(new Entry(component, hash(owner), props == null ? "" : hash(props)));
        }
        return new Index(entries);
    }

    /** Deterministic UTF-8 binary format. No source path, source text, timestamps or
     * machine-local classpath is serialized. */
    public static byte[] encode(Index index) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC); output.writeInt(VERSION); output.writeInt(index.entries().size());
            for (Entry entry : index.entries()) {
                var c = entry.component();
                text(output, c.qualifiedName()); text(output, entry.ownerSha256()); text(output, entry.propsSha256());
                output.writeBoolean(c.hasProps()); output.writeInt(c.propsTypeParameters()); text(output, c.memo().name());
                props(output, c.props());
                output.writeBoolean(c.classSpec() != null);
                if (c.classSpec() != null) {
                    var spec = c.classSpec();
                    text(output, spec.kind().name()); text(output, spec.stateType()); props(output, spec.stateFields());
                    var lifecycle = spec.lifecycles().stream().sorted().toList(); output.writeInt(lifecycle.size());
                    for (var value : lifecycle) text(output, value.name());
                    nullable(output, spec.contextValueType()); output.writeBoolean(spec.derivedProps());
                    output.writeBoolean(spec.derivedError()); output.writeBoolean(spec.contextShouldUpdate());
                }
                if (bytes.size() > MAX_BYTES) throw new IOException("Component index exceeds four MiB");
            }
        }
        if (bytes.size() > MAX_BYTES) throw new IOException("Component index exceeds four MiB");
        return bytes.toByteArray();
    }

    /** Reads and validates index structure. Binary metadata points to its artifact,
     * not a fabricated source file; source navigation requires separate IDE support. */
    public static Index parse(byte[] bytes, Path artifact) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("Component index exceeds four MiB");
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) throw new IOException("Invalid component descriptor magic");
            int version = input.readInt();
            if (version != VERSION) throw new IOException("Unsupported component descriptor version " + version + "; expected " + VERSION);
            int count = count(input, MAX_COMPONENTS, "components");
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String name = text(input), ownerHash = text(input), propsHash = text(input);
                boolean hasProps = bool(input); int parameters = count(input, 64, "Props type parameters");
                var memo = ComponentMetadata.Memo.valueOf(text(input));
                var properties = props(input);
                ComponentMetadata.ClassSpec spec = null;
                if (bool(input)) {
                    var kind = ComponentMetadata.ClassKind.valueOf(text(input)); String state = text(input);
                    var stateFields = props(input); int length = count(input, ComponentMetadata.Lifecycle.values().length, "lifecycles");
                    Set<ComponentMetadata.Lifecycle> lifecycle = EnumSet.noneOf(ComponentMetadata.Lifecycle.class);
                    for (int n = 0; n < length; n++) if (!lifecycle.add(ComponentMetadata.Lifecycle.valueOf(text(input))))
                        throw new IOException("Duplicate component lifecycle");
                    spec = new ComponentMetadata.ClassSpec(kind, state, stateFields, lifecycle,
                            nullable(input), bool(input), bool(input), bool(input));
                }
                int dot = name.lastIndexOf('.');
                var c = new ComponentMetadata(name, name.substring(dot + 1), dot < 0 ? "" : name.substring(0, dot),
                        List.of(), hasProps, properties, artifact, BINARY, -1, memo, parameters, spec);
                entries.add(new Entry(c, ownerHash, propsHash));
            }
            if (input.read() != -1) throw new IOException("Trailing component descriptor bytes");
            return new Index(entries);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IOException("Invalid component descriptor: " + invalid.getMessage(), invalid);
        }
    }

    /** Empty means the artifact does not advertise components. Existing malformed,
     * incompatible or stale indexes always throw; no archive entries are extracted. */
    public static Optional<Index> read(Path artifact) throws IOException {
        Path path = artifact.toRealPath();
        if (Files.isDirectory(path)) {
            Path descriptor = path.resolve(RESOURCE);
            if (!Files.exists(descriptor, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
            var index = parse(readClass(path, RESOURCE), path);
            verify(index, relative -> readClass(path, relative)); return Optional.of(index);
        }
        try (var archive = new ZipFile(path.toFile())) {
            var descriptor = archive.getEntry(RESOURCE);
            if (descriptor == null) return Optional.empty();
            if (Files.size(path) > MAX_ARCHIVE) throw new IOException("Component JAR exceeds 64 MiB");
            Set<String> names = new HashSet<>(); var all = archive.entries();
            while (all.hasMoreElements()) if (names.size() >= 65536 || !names.add(all.nextElement().getName()))
                throw new IOException("Component JAR has too many or duplicate entries");
            Index index;
            try (var input = archive.getInputStream(descriptor)) { index = parse(bounded(input), path); }
            verify(index, relative -> {
                var entry = archive.getEntry(relative);
                if (entry == null || entry.isDirectory()) throw new IOException("Missing component binary " + relative);
                try (var input = archive.getInputStream(entry)) { return bounded(input); }
            });
            return Optional.of(index);
        }
    }

    private interface Reader { byte[] read(String relative) throws IOException; }
    private static void verify(Index index, Reader reader) throws IOException {
        long total = 0;
        for (Entry entry : index.entries()) {
            var component = entry.component(); byte[] owner = reader.read(classPath(component.qualifiedName()));
            byte[] props = component.hasProps() ? reader.read(propsPath(component)) : null;
            total += owner.length + (props == null ? 0 : props.length);
            if (total > MAX_ARCHIVE) throw new IOException("Indexed component classes exceed 64 MiB");
            if (!hash(owner).equals(entry.ownerSha256()) || props != null && !hash(props).equals(entry.propsSha256()))
                throw new IOException("Stale component descriptor for " + component.qualifiedName());
            verifyShape(component, owner, props);
        }
    }
    private static void verifyShape(ComponentMetadata component, byte[] ownerBytes, byte[] propsBytes) throws IOException {
        try {
            var owner = ClassFile.of().parse(ownerBytes);
            if (!owner.thisClass().asInternalName().replace('/', '.').equals(component.qualifiedName()) || !owner.flags().has(AccessFlag.PUBLIC))
                throw new IOException("Component owner is not the declared public class");
            var fields = owner.fields().stream().filter(field -> field.fieldName().equalsString("JX")).toList();
            String expected = component.classSpec() == null ? "Ljx/ComponentType;" : "Ljx/ClassComponentType;";
            if (fields.size() != 1 || !fields.getFirst().fieldType().equalsString(expected)
                    || !fields.getFirst().flags().has(AccessFlag.PUBLIC) || !fields.getFirst().flags().has(AccessFlag.STATIC)
                    || !fields.getFirst().flags().has(AccessFlag.FINAL))
                throw new IOException("Component requires its declared public static final JX descriptor");
            var signature = fields.getFirst().findAttribute(Attributes.signature())
                    .orElseThrow(() -> new IOException("Component JX descriptor must retain its generic signature")).asTypeSignature();
            String propsDescriptor = component.hasProps() ? "L" + component.qualifiedName().replace('.', '/') + "$Props;" : "Ljava/lang/Void;";
            if (!(signature instanceof Signature.ClassTypeSig generic) || generic.typeArgs().size() != (component.classSpec() == null ? 1 : 3)
                    || !(generic.typeArgs().getFirst() instanceof Signature.TypeArg.Bounded argument)
                    || argument.wildcardIndicator() != Signature.TypeArg.Bounded.WildcardIndicator.NONE
                    || !(argument.boundType() instanceof Signature.ClassTypeSig propsType)
                    || !propsType.classDesc().descriptorString().equals(propsDescriptor))
                throw new IOException("Component JX generic Props contract differs from descriptor");
            if (propsBytes != null) {
                var props = ClassFile.of().parse(propsBytes);
                if (!props.thisClass().asInternalName().equals(component.qualifiedName().replace('.', '/') + "$Props")
                        || !props.flags().has(AccessFlag.PUBLIC)) throw new IOException("Component Props is not the declared public record");
                var fieldsInOrder = props.findAttribute(Attributes.record()).orElseThrow(() -> new IOException("Component Props must be a record")).components();
                int typeParameters = props.findAttribute(Attributes.signature()).map(value -> value.asClassSignature().typeParameters().size()).orElse(0);
                if (typeParameters != component.propsTypeParameters()) throw new IOException("Component Props type parameter arity differs from descriptor");
                if (fieldsInOrder.size() != component.props().size()) throw new IOException("Component Props arity differs from descriptor");
                for (int i = 0; i < fieldsInOrder.size(); i++) {
                    var declared = component.props().get(i); var actual = fieldsInOrder.get(i);
                    if (!actual.name().equalsString(declared.name())) throw new IOException("Component Props order differs from descriptor");
                    String primitive = primitive(declared.javaType());
                    if (primitive != null && !actual.descriptor().equalsString(primitive)
                            || primitive == null && actual.descriptor().stringValue().length() == 1)
                        throw new IOException("Component primitive prop type differs from descriptor");
                }
            }
        } catch (IllegalArgumentException invalid) { throw new IOException("Invalid component class file", invalid); }
    }
    private static String primitive(String type) {
        return switch (type) { case "boolean" -> "Z"; case "byte" -> "B"; case "short" -> "S"; case "int" -> "I";
            case "long" -> "J"; case "float" -> "F"; case "double" -> "D"; case "char" -> "C"; default -> null; };
    }
    private static void validate(ComponentMetadata c) {
        if (!SourceVersion.isName(c.qualifiedName()) || c.qualifiedName().length() > 1024
                || !c.qualifiedName().equals(c.packageName().isEmpty() ? c.simpleName() : c.packageName() + "." + c.simpleName()))
            throw new IllegalArgumentException("Invalid component name");
        if (c.propsTypeParameters() < 0 || c.propsTypeParameters() > 64 || !c.hasProps() && (!c.props().isEmpty() || c.propsTypeParameters() != 0))
            throw new IllegalArgumentException("Invalid Props declaration");
        validateProps(c.props()); Objects.requireNonNull(c.memo());
        if (c.classSpec() != null) {
            if (!c.hasProps() || c.propsTypeParameters() != 0) throw new IllegalArgumentException("Invalid native class Props contract");
            validateText(c.classSpec().stateType()); validateProps(c.classSpec().stateFields());
            if (c.classSpec().contextValueType() != null) validateText(c.classSpec().contextValueType());
        }
    }
    private static void validateProps(List<ComponentMetadata.Prop> props) {
        if (props.size() > MAX_FIELDS) throw new IllegalArgumentException("Too many component props");
        Set<String> names = new HashSet<>();
        for (var prop : props) {
            if (!SourceVersion.isIdentifier(prop.name()) || SourceVersion.isKeyword(prop.name()) || !names.add(prop.name()))
                throw new IllegalArgumentException("Invalid or duplicate component prop");
            validateText(prop.javaType());
        }
    }
    private static void validateText(String value) {
        if (Objects.requireNonNull(value).isBlank() || value.length() > 4096 || value.indexOf('\0') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || !StandardCharsets.UTF_8.newEncoder().canEncode(value))
            throw new IllegalArgumentException("Invalid component type text");
    }
    private static void digest(String value) {
        if (!Objects.requireNonNull(value).matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid component binary digest");
    }
    private static void props(DataOutputStream out, List<ComponentMetadata.Prop> props) throws IOException {
        out.writeInt(props.size());
        for (var prop : props) { text(out, prop.name()); text(out, prop.javaType()); out.writeBoolean(prop.optional()); }
    }
    private static List<ComponentMetadata.Prop> props(DataInputStream in) throws IOException {
        int count = count(in, MAX_FIELDS, "props"); List<ComponentMetadata.Prop> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(new ComponentMetadata.Prop(text(in), text(in), BINARY, bool(in)));
        return List.copyOf(result);
    }
    private static int count(DataInputStream input, int max, String kind) throws IOException {
        int count = input.readInt(); if (count < 0 || count > max) throw new IOException("Invalid component descriptor " + kind + " count"); return count;
    }
    private static boolean bool(DataInputStream input) throws IOException {
        int value = input.readUnsignedByte(); if (value > 1) throw new IOException("Invalid component descriptor boolean"); return value != 0;
    }
    private static void nullable(DataOutputStream output, String value) throws IOException { output.writeBoolean(value != null); if (value != null) text(output, value); }
    private static String nullable(DataInputStream input) throws IOException { return bool(input) ? text(input) : null; }
    private static void text(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > 16384) throw new IOException("Component descriptor string too long");
        output.writeInt(encoded.length); output.write(encoded);
    }
    private static String text(DataInputStream input) throws IOException {
        int length = count(input, 16384, "string bytes"); byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new EOFException("Truncated component descriptor");
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException invalid) { throw new IOException("Invalid component descriptor UTF-8", invalid); }
    }
    private static String classPath(String name) { return name.replace('.', '/') + ".class"; }
    private static String propsPath(ComponentMetadata component) { return component.qualifiedName().replace('.', '/') + "$Props.class"; }
    private static byte[] readClass(Path root, String relative) throws IOException {
        Path path = root.resolve(relative);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !path.toRealPath().startsWith(root))
            throw new IOException("Missing or unowned component input " + relative);
        if (Files.size(path) > MAX_BYTES) throw new IOException("Component input exceeds four MiB");
        try (var input = Files.newInputStream(path)) { return bounded(input); }
    }
    private static byte[] bounded(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new IOException("Component input exceeds four MiB"); return bytes;
    }
    private static String hash(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
