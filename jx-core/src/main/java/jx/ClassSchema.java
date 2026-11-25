package jx;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Explicit record-shaped props/state contract for class components. Fields are projected
 * individually, so React compares scalars by Object.is and nested Java values by identity.
 * Constructors and field readers must be pure. No reflective field access is used. */
public final class ClassSchema<T> {
    /** The renderer's scalar/opaque boundary. */
    public enum Kind { INTEGER, LONG, DOUBLE, FLOAT, BOOLEAN, STRING, REFERENCE }

    /** A checked field-value codec. Reference decoders must check the concrete Java type and
     * return the same instance; they must not clone values or substitute equals(). */
    public static final class ValueType<V> {
        private final Kind kind;
        private final Function<Object, V> decoder;
        private final boolean nullable;
        private ValueType(Kind kind, Function<Object, V> decoder, boolean nullable) {
            this.kind = kind; this.decoder = Objects.requireNonNull(decoder); this.nullable = nullable;
        }
        public Kind kind() { return kind; }
        public ValueType<V> nullable() { return new ValueType<>(kind, decoder, true); }
        public V decode(Object value) {
            if (value == null) {
                if (nullable) return null;
                throw new IllegalArgumentException("Null is not allowed for " + kind);
            }
            V decoded = Objects.requireNonNull(decoder.apply(value), "Field decoder returned null");
            if (kind == Kind.REFERENCE && decoded != value)
                throw new IllegalArgumentException("Reference decoder must preserve identity");
            return decoded;
        }
    }
    public static final ValueType<Integer> INTEGER = new ValueType<>(Kind.INTEGER, value -> {
        if (value instanceof Integer number) return number;
        throw invalid(Kind.INTEGER);
    }, false);
    public static final ValueType<Long> LONG = new ValueType<>(Kind.LONG, value -> {
        if (value instanceof Long number && number >= -9_007_199_254_740_991L && number <= 9_007_199_254_740_991L) return number;
        throw new IllegalArgumentException("Long must be inside JavaScript's exact integer range");
    }, false);
    public static final ValueType<Double> DOUBLE = new ValueType<>(Kind.DOUBLE, value -> {
        if (value instanceof Double number) return number;
        throw invalid(Kind.DOUBLE);
    }, false);
    public static final ValueType<Float> FLOAT = new ValueType<>(Kind.FLOAT, value -> {
        if (value instanceof Float number) return number;
        throw invalid(Kind.FLOAT);
    }, false);
    public static final ValueType<Boolean> BOOLEAN = new ValueType<>(Kind.BOOLEAN, value -> {
        if (value instanceof Boolean flag) return flag;
        throw invalid(Kind.BOOLEAN);
    }, false);
    public static final ValueType<String> STRING = new ValueType<>(Kind.STRING, value -> {
        if (value instanceof String text) return text;
        throw invalid(Kind.STRING);
    }, false);
    private static IllegalArgumentException invalid(Kind kind) { return new IllegalArgumentException("Invalid class field: " + kind); }
    public static <V> ValueType<V> reference(Function<Object, V> checkedDecoder) {
        return new ValueType<>(Kind.REFERENCE, checkedDecoder, false);
    }

    /** A named, typed property. Declare once and reuse for construction and partial patches. */
    public static final class Field<T, V> {
        private final String name;
        private final ValueType<V> type;
        private final Function<T, V> getter;
        private Field(String name, ValueType<V> type, Function<T, V> getter) {
            if (!Objects.requireNonNull(name).matches("[A-Za-z_$][A-Za-z0-9_$]*")
                    || List.of("key", "ref", "__self", "__source", "__proto__", "prototype", "constructor").contains(name)
                    || name.startsWith("__jx")) throw new IllegalArgumentException("Reserved or invalid class field: " + name);
            this.name = name; this.type = Objects.requireNonNull(type); this.getter = Objects.requireNonNull(getter);
        }
        public String name() { return name; }
        public ValueType<V> type() { return type; }
        public V value(T record) { return type.decode(getter.apply(Objects.requireNonNull(record))); }
    }
    public static <T,V> Field<T,V> field(String name, ValueType<V> type, Function<T,V> getter) {
        return new Field<>(name, type, getter);
    }

    /** Typed constructor input. Reading a field from another schema fails explicitly. */
    public static final class Reader {
        private final ClassSchema<?> schema;
        private final Function<Field<?,?>, Object> source;
        private Reader(ClassSchema<?> schema, Function<Field<?,?>, Object> source) { this.schema = schema; this.source = source; }
        public <V> V get(Field<?,V> field) {
            schema.requireField(field);
            return field.type().decode(source.apply(field));
        }
    }
    private final List<Field<T,?>> fields;
    private final Function<Reader,T> construct;
    public ClassSchema(List<Field<T,?>> fields, Function<Reader,T> construct) {
        this.fields = List.copyOf(fields); this.construct = Objects.requireNonNull(construct);
        var names = new HashSet<String>();
        for (Field<T,?> field : this.fields)
            if (!names.add(field.name())) throw new IllegalArgumentException("Duplicate class field: " + field.name());
    }
    public List<Field<T,?>> fields() { return fields; }
    public T read(Function<Field<?,?>,Object> source) {
        T value = Objects.requireNonNull(construct.apply(new Reader(this, Objects.requireNonNull(source))), "Schema constructor returned null");
        validate(value);
        return value;
    }
    public void validate(T value) {
        Objects.requireNonNull(value, "Class props/state must be non-null");
        for (Field<T,?> field : fields) field.value(value);
    }
    void requireField(Field<?,?> field) {
        if (!fields.contains(Objects.requireNonNull(field))) throw new IllegalArgumentException("Field does not belong to this class schema: " + field.name());
    }
    /** Starts an empty partial update. Empty updates differ from a null updater result. */
    public StatePatch<T> patch() { return new StatePatch<>(this, List.of()); }
}
