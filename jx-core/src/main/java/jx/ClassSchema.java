package jx;

import java.util.HashSet;
import java.util.ArrayList;
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
        private final Function<T, Presence<V>> getter;
        private final boolean optional;
        private Field(String name, ValueType<V> type, Function<T, Presence<V>> getter, boolean optional) {
            if (!Objects.requireNonNull(name).matches("[A-Za-z_$][A-Za-z0-9_$]*")
                    || List.of("key", "ref", "__self", "__source", "__proto__", "prototype", "constructor").contains(name)
                    || name.startsWith("__jx")) throw new IllegalArgumentException("Reserved or invalid class field: " + name);
            this.name = name; this.type = Objects.requireNonNull(type); this.getter = Objects.requireNonNull(getter);
            this.optional = optional;
        }
        public String name() { return name; }
        public ValueType<V> type() { return type; }
        /** Whether this field can be absent or own-undefined. Null values are governed
         * independently by its ValueType. */
        public boolean optional() { return optional; }
        /** Validated presence in a non-null record; does not synthesize a default value. */
        public Presence<V> presence(T record) { return decodePresence(getter.apply(Objects.requireNonNull(record))); }
        /** Reads an own, defined value; absent and undefined optional properties fail
         * explicitly. Use presence when these states belong to the application contract. */
        public V value(T record) { return defined(presence(record)); }
        private Presence<V> decodePresence(Presence<?> presence) {
            Objects.requireNonNull(presence, "Field presence must be non-null");
            return switch (presence) {
                case Presence.Present<?> present -> Presence.present(type.decode(present.value()));
                case Presence.Absent<?> ignored -> {
                    if (!optional) throw new IllegalArgumentException("Missing required class field: " + name);
                    yield Presence.absent();
                }
                case Presence.Undefined<?> ignored -> {
                    if (!optional) throw new IllegalArgumentException("Undefined required class field: " + name);
                    yield Presence.undefined();
                }
            };
        }
        private V defined(Presence<V> presence) {
            if (presence instanceof Presence.Present<V> present) return present.value();
            throw new IllegalArgumentException("Class field has no defined value: " + name);
        }
    }
    public static <T,V> Field<T,V> field(String name, ValueType<V> type, Function<T,V> getter) {
        Objects.requireNonNull(getter, "getter");
        return new Field<>(name, type, record -> Presence.present(getter.apply(record)), false);
    }
    /** Declares a sparse property whose immutable record stores presence explicitly. The
     * getter must return a non-null presence. Absent properties are omitted on encoding;
     * Undefined properties remain own properties. Neither becomes a Java default value.
     * Present values retain the ordinary type/nullability/reference-identity checks. */
    public static <T,V> Field<T,V> optionalField(String name, ValueType<V> type, Function<T,Presence<V>> getter) {
        return new Field<>(name, type, getter, true);
    }

    /** Typed constructor input. Reading a field from another schema fails explicitly. */
    public static final class Reader {
        private final ClassSchema<?> schema;
        private final List<Presence<?>> values;
        private Reader(ClassSchema<?> schema, List<Presence<?>> values) { this.schema = schema; this.values = List.copyOf(values); }
        public <V> V get(Field<?,V> field) {
            return field.defined(find(field));
        }
        /** Reads validated property presence. Required fields still reject absence and
         * undefined; requesting a field from another schema fails explicitly. */
        public <V> Presence<V> find(Field<?,V> field) {
            schema.requireField(field);
            return field.decodePresence(values.get(schema.fields.indexOf(field)));
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
        Objects.requireNonNull(source, "source");
        return readPresence(field -> Presence.present(source.apply(field)));
    }
    /** Constructs a non-null record from explicitly typed property presence. The constructor
     * and source must be pure. Every source field is read once, in schema order, and validated
     * before the constructor is invoked. Reader access uses that immutable validated snapshot;
     * ignoring a field cannot hide an invalid source. Missing properties receive no defaults.
     * Null aggregate state is a separate ClassComponentType opt-in, not a nullable schema. */
    public T readPresence(Function<Field<?,?>,Presence<?>> source) {
        Objects.requireNonNull(source, "source");
        var values = new ArrayList<Presence<?>>();
        for (Field<T,?> field : fields) values.add(field.decodePresence(source.apply(field)));
        T value = Objects.requireNonNull(construct.apply(new Reader(this, values)), "Schema constructor returned null");
        validate(value);
        return value;
    }
    public void validate(T value) {
        Objects.requireNonNull(value, "Class props/state must be non-null");
        for (Field<T,?> field : fields) field.presence(value);
    }
    void requireField(Field<?,?> field) {
        if (!fields.contains(Objects.requireNonNull(field))) throw new IllegalArgumentException("Field does not belong to this class schema: " + field.name());
    }
    /** Starts an empty partial update. Empty updates differ from a null updater result. */
    public StatePatch<T> patch() { return new StatePatch<>(this, List.of()); }
}
