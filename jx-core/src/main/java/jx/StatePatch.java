package jx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable, schema-owned partial class state. Unlisted fields are omitted; a listed nullable
 * field may contain null. An optional field may explicitly become own-undefined; omission
 * never deletes a previously present property. React performs the merge. Repeated fields
 * are rejected regardless of whether a defined value or undefined was supplied. */
public final class StatePatch<S> {
    /** A patch assignment. When undefined is true, value is necessarily null and denotes
     * no Java value; the field must be optional. The two-argument constructor retains its
     * defined-value meaning, including a nullable field's explicit null. */
    public record Entry<S,V>(ClassSchema.Field<S,V> field, V value, boolean undefined) {
        public Entry(ClassSchema.Field<S,V> field, V value) { this(field, value, false); }
        public Entry {
            Objects.requireNonNull(field);
            if (undefined) {
                if (!field.optional()) throw new IllegalArgumentException("Undefined requires an optional class field: " + field.name());
                if (value != null) throw new IllegalArgumentException("Undefined patch cannot contain a Java value");
            } else field.type().decode(value);
        }
    }
    private final ClassSchema<S> schema;
    private final List<Entry<S,?>> entries;
    StatePatch(ClassSchema<S> schema, List<Entry<S,?>> entries) { this.schema = schema; this.entries = List.copyOf(entries); }
    public <V> StatePatch<S> set(ClassSchema.Field<S,V> field, V value) {
        return append(new Entry<>(field, value));
    }
    /** Assigns an own property with JavaScript undefined. This differs from omitting the
     * field, deleting it, or assigning a nullable field's null value. */
    public StatePatch<S> undefined(ClassSchema.Field<S,?> field) {
        return append(new Entry<>(field, null, true));
    }
    private StatePatch<S> append(Entry<S,?> added) {
        ClassSchema.Field<S,?> field = added.field();
        schema.requireField(field);
        if (entries.stream().anyMatch(entry -> entry.field() == field)) throw new IllegalArgumentException("Duplicate patch field: " + field.name());
        var copy = new ArrayList<Entry<S,?>>(entries);
        copy.add(added);
        return new StatePatch<>(schema, copy);
    }
    public ClassSchema<S> schema() { return schema; }
    public List<Entry<S,?>> entries() { return entries; }
}
