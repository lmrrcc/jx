package jx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable, schema-owned partial class state. Unlisted fields are omitted; a listed nullable
 * field may contain null. React performs the merge. Repeated fields are rejected. */
public final class StatePatch<S> {
    public record Entry<S,V>(ClassSchema.Field<S,V> field, V value) {
        public Entry { Objects.requireNonNull(field).type().decode(value); }
    }
    private final ClassSchema<S> schema;
    private final List<Entry<S,?>> entries;
    StatePatch(ClassSchema<S> schema, List<Entry<S,?>> entries) { this.schema = schema; this.entries = List.copyOf(entries); }
    public <V> StatePatch<S> set(ClassSchema.Field<S,V> field, V value) {
        schema.requireField(field);
        if (entries.stream().anyMatch(entry -> entry.field() == field)) throw new IllegalArgumentException("Duplicate patch field: " + field.name());
        var copy = new ArrayList<Entry<S,?>>(entries);
        copy.add(new Entry<>(field, value));
        return new StatePatch<>(schema, copy);
    }
    public ClassSchema<S> schema() { return schema; }
    public List<Entry<S,?>> entries() { return entries; }
}
