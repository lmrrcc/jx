package jx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable, schema-owned input properties of a native class element or its defaults.
 * Unlike a resolved props record, input may omit required fields or supply own-undefined:
 * React applies the descriptor's defaults before the strict record schema is decoded.
 * Explicit null is a value and must be allowed by the field's value type. No defaults
 * are applied here, no record is constructed, and no component is invoked.
 *
 * <p>Reference values retain their identity; callers must respect the field's ownership
 * contract. Each copy retains at most one entry per schema field. Use on the rendering
 * thread when entries contain browser-owned values. */
public final class ClassProps<P> {
    /** A defined value or own-undefined assignment. Undefined has no Java value. */
    public record Entry<P,V>(ClassSchema.Field<P,V> field, V value, boolean undefined) {
        public Entry {
            Objects.requireNonNull(field, "field");
            if (undefined) {
                if (value != null) throw new IllegalArgumentException("Undefined input cannot contain a Java value");
            } else field.type().decode(value);
        }
        /** Defined value, including null when the field permits it. */
        public Entry(ClassSchema.Field<P,V> field, V value) { this(field, value, false); }
    }

    private final ClassSchema<P> schema;
    private final List<Entry<P,?>> entries;

    private ClassProps(ClassSchema<P> schema, List<Entry<P,?>> entries) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.entries = List.copyOf(entries);
    }
    /** Empty input: every field is absent, including fields required in resolved props. */
    public static <P> ClassProps<P> empty(ClassSchema<P> schema) { return new ClassProps<>(schema, List.of()); }
    /** Copies the presence and values of a validated record without applying native defaults. */
    public static <P> ClassProps<P> from(ClassSchema<P> schema, P value) {
        Objects.requireNonNull(schema, "schema").validate(value);
        ClassProps<P> result = empty(schema);
        for (ClassSchema.Field<P,?> field : schema.fields()) result = copyField(result, field, value);
        return result;
    }
    private static <P,V> ClassProps<P> copyField(ClassProps<P> result, ClassSchema.Field<P,V> field, P value) {
        return switch (field.presence(value)) {
            case Presence.Absent<V> ignored -> result;
            case Presence.Undefined<V> ignored -> result.undefined(field);
            case Presence.Present<V> present -> result.set(field, present.value());
        };
    }
    /** Returns a copy with this defined field value, replacing any prior assignment. */
    public <V> ClassProps<P> set(ClassSchema.Field<P,V> field, V value) { return replace(new Entry<>(field, value)); }
    /** Returns a copy with an own-undefined field. This requests native default resolution;
     * it is distinct from both absence and an explicit null value. */
    public ClassProps<P> undefined(ClassSchema.Field<P,?> field) { return replace(new Entry<>(field, null, true)); }
    /** Returns a copy in which this input field is absent. Other entries are retained. */
    public ClassProps<P> omit(ClassSchema.Field<P,?> field) {
        schema.requireField(field);
        return new ClassProps<>(schema, entries.stream().filter(entry -> entry.field() != field).toList());
    }
    private ClassProps<P> replace(Entry<P,?> added) {
        schema.requireField(added.field());
        var copy = new ArrayList<>(entries);
        for (int i = 0; i < copy.size(); i++) {
            if (copy.get(i).field() == added.field()) {
                copy.set(i, added);
                return new ClassProps<>(schema, copy);
            }
        }
        copy.add(added);
        return new ClassProps<>(schema, copy);
    }
    /** Copies explicit overrides over this input, retaining fields absent from overrides.
     * Own-undefined and explicit null are real overrides. Both inputs must share the
     * exact schema instance. Neither input is changed. */
    public ClassProps<P> overlay(ClassProps<P> overrides) {
        if (Objects.requireNonNull(overrides, "overrides").schema != schema)
            throw new IllegalArgumentException("Class props override belongs to another schema");
        ClassProps<P> result = this;
        for (Entry<P,?> entry : overrides.entries) result = result.replace(entry);
        return result;
    }
    /** Raw input presence, before native defaults; required fields may be absent/undefined. */
    public <V> Presence<V> find(ClassSchema.Field<P,V> field) {
        schema.requireField(field);
        for (Entry<P,?> entry : entries) {
            if (entry.field() == field) return entry.undefined() ? Presence.undefined()
                    : Presence.present(field.type().decode(entry.value()));
        }
        return Presence.absent();
    }
    /** Owning schema identity. */
    public ClassSchema<P> schema() { return schema; }
    /** Immutable ordered assignments; reference values are deliberately not cloned. */
    public List<Entry<P,?>> entries() { return entries; }
}
