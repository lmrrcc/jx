package jx;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassPropsTest {
    private record Props(String required, Presence<String> optional, Object reference) {}
    private static final ClassSchema.Field<Props,String> REQUIRED = ClassSchema.field("required", ClassSchema.STRING, Props::required);
    private static final ClassSchema.Field<Props,String> OPTIONAL = ClassSchema.optionalField("optional", ClassSchema.STRING.nullable(), Props::optional);
    private static final ClassSchema.Field<Props,Object> REFERENCE = ClassSchema.field("reference", ClassSchema.reference(value -> value), Props::reference);

    private static ClassSchema<Props> schema(AtomicInteger constructed) {
        return new ClassSchema<>(List.of(REQUIRED, OPTIONAL, REFERENCE), reader -> {
            constructed.incrementAndGet();
            return new Props(reader.get(REQUIRED), reader.find(OPTIONAL), reader.get(REFERENCE));
        });
    }

    @Test void inputRetainsMissingUndefinedNullAndReferenceIdentityWithoutConstructingResolvedProps() {
        var constructed = new AtomicInteger();
        var schema = schema(constructed);
        var empty = ClassProps.empty(schema);
        var reference = new Object();
        var partial = empty.undefined(REQUIRED).set(OPTIONAL, null).set(REFERENCE, reference);
        assertEquals(Presence.absent(), empty.find(REQUIRED));
        assertEquals(Presence.undefined(), partial.find(REQUIRED));
        assertEquals(Presence.present(null), partial.find(OPTIONAL));
        assertSame(reference, ((Presence.Present<Object>) partial.find(REFERENCE)).value());
        assertEquals(0, constructed.get());
        assertThrows(IllegalArgumentException.class, () -> schema.readPresence(field -> Presence.absent()));
        assertThrows(IllegalArgumentException.class, () -> empty.set(REQUIRED, null));
        assertThrows(UnsupportedOperationException.class, () -> partial.entries().clear());
    }

    @Test void copiesAndOverridesRemainSchemaOwnedBoundedAndImmutable() {
        var schema = schema(new AtomicInteger());
        var reference = new Object();
        var original = ClassProps.from(schema, new Props("before", Presence.absent(), reference));
        var changed = original.overlay(ClassProps.empty(schema).undefined(REQUIRED).set(OPTIONAL, "after"));
        assertEquals(Presence.present("before"), original.find(REQUIRED));
        assertEquals(Presence.absent(), original.find(OPTIONAL));
        assertEquals(Presence.undefined(), changed.find(REQUIRED));
        assertEquals(Presence.present("after"), changed.find(OPTIONAL));
        assertSame(reference, ((Presence.Present<Object>) changed.find(REFERENCE)).value());
        for (int i = 0; i < 100; i++) changed = changed.set(OPTIONAL, "value" + i);
        assertEquals(3, changed.entries().size());
        assertEquals(Presence.absent(), changed.omit(OPTIONAL).find(OPTIONAL));
        assertEquals(Presence.present("value99"), changed.find(OPTIONAL));
        assertThrows(IllegalArgumentException.class, () -> original.overlay(ClassProps.empty(schema(new AtomicInteger()))));
        var foreign = ClassSchema.field("required", ClassSchema.STRING, Props::required);
        assertThrows(IllegalArgumentException.class, () -> original.set(foreign, "foreign"));
        assertThrows(IllegalArgumentException.class, () -> original.find(foreign));
        assertThrows(IllegalArgumentException.class, () -> original.omit(foreign));
    }
}
