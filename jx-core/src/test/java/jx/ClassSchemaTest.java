package jx;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassSchemaTest {
    record Model(int count, String note) {}
    private static final ClassSchema.Field<Model,Integer> COUNT = ClassSchema.field("count", ClassSchema.INTEGER, Model::count);
    private static final ClassSchema.Field<Model,String> NOTE = ClassSchema.field("note", ClassSchema.STRING.nullable(), Model::note);
    private static final ClassSchema<Model> MODEL = new ClassSchema<>(List.of(COUNT, NOTE), reader -> new Model(reader.get(COUNT), reader.get(NOTE)));

    @Test void partialUpdatesDistinguishOmissionFromNullAndProtectOwnership() {
        StatePatch<Model> empty = MODEL.patch();
        StatePatch<Model> cleared = empty.set(NOTE, null);
        assertTrue(empty.entries().isEmpty());
        assertEquals(1, cleared.entries().size());
        assertSame(NOTE, cleared.entries().getFirst().field());
        assertNull(cleared.entries().getFirst().value());
        assertThrows(IllegalArgumentException.class, () -> cleared.set(NOTE, "again"));
        assertThrows(IllegalArgumentException.class, () -> empty.set(COUNT, null));
        ClassSchema.Field<Model,Integer> foreign = ClassSchema.field("count", ClassSchema.INTEGER, Model::count);
        assertThrows(IllegalArgumentException.class, () -> empty.set(foreign, 3));
        assertThrows(UnsupportedOperationException.class, () -> cleared.entries().clear());
    }
    @Test void schemaRejectsReservedAndDuplicateFieldsAndChecksReads() {
        for (String name : List.of("key", "ref", "__self", "__source", "constructor", "prototype", "__proto__", "__jxPath", "bad-name"))
            assertThrows(IllegalArgumentException.class, () -> ClassSchema.field(name, ClassSchema.INTEGER, Model::count));
        assertThrows(IllegalArgumentException.class, () -> new ClassSchema<>(List.of(COUNT, COUNT), reader -> new Model(0, null)));
        assertEquals(new Model(3, null), MODEL.read(field -> field == COUNT ? 3 : null));
        assertThrows(IllegalArgumentException.class, () -> MODEL.read(field -> "wrong"));
        ClassSchema.Field<Model,Integer> foreign = ClassSchema.field("count", ClassSchema.INTEGER, Model::count);
        ClassSchema<Model> wrongReader = new ClassSchema<>(List.of(COUNT, NOTE), reader -> new Model(reader.get(foreign), null));
        assertThrows(IllegalArgumentException.class, () -> wrongReader.read(field -> 1));
    }
    @Test void scalarAndReferenceBoundariesKeepTheirActualSemantics() {
        assertEquals(-0.0, ClassSchema.DOUBLE.decode(-0.0));
        assertTrue(Double.isNaN(ClassSchema.DOUBLE.decode(Double.NaN)));
        assertEquals(9_007_199_254_740_991L, ClassSchema.LONG.decode(9_007_199_254_740_991L));
        assertThrows(IllegalArgumentException.class, () -> ClassSchema.LONG.decode(9_007_199_254_740_992L));
        ClassSchema.ValueType<Model> reference = ClassSchema.reference(value -> {
            if (value instanceof Model model) return model;
            throw new IllegalArgumentException("Expected Model");
        });
        Model model = new Model(1, "one");
        assertSame(model, reference.decode(model));
        assertThrows(IllegalArgumentException.class, () -> reference.decode("wrong"));
        ClassSchema.ValueType<Model> cloning = ClassSchema.reference(value -> new Model(1, "one"));
        assertThrows(IllegalArgumentException.class, () -> cloning.decode(model));
    }
    static final class Counter extends ClassComponent<Model,Model,String> {
        Counter(Model props) { super(props, props); }
        @Override public View render() { throw new AssertionError("Composition cannot render a class"); }
    }
    @Test void compositionIsLazyAndClassRefsAreChecked() {
        ClassComponentType<Model,Model,Counter> type = ClassComponentType.normal("Counter", MODEL, MODEL,
                value -> { throw new AssertionError("Composition cannot construct a class"); }, Set.of());
        View view = Views.component(type, new Model(0, "ready"), "counter", null);
        assertTrue(Views.isValidElement(view));
        assertEquals("counter", Views.key(view));
        assertEquals(1, Children.count(view));
        Counter peer = new Counter(new Model(0, "ready"));
        ClassRef<Counter> ref = new ClassRef<>(value -> {
            if (value instanceof Counter counter) return counter;
            throw new IllegalArgumentException("Expected Counter");
        });
        ref.attach(peer); assertSame(peer, ref.current());
        assertThrows(IllegalArgumentException.class, () -> ref.attach("wrong"));
        ref.attach(null); assertNull(ref.current());
        assertThrows(IllegalStateException.class, () -> peer.setState(MODEL.patch()));
    }
    @Test void bindingHandsOffInitialValuesAndReadsNativeStateWhenRenderingIsSkipped() {
        Model initial = new Model(0, "initial");
        Counter peer = new Counter(initial);
        assertSame(initial, peer.initialState());
        Model changed = new Model(3, "changed");
        peer.bind(new ClassComponent.Host<>() {
            @Override public Model props() { return changed; }
            @Override public Model state() { return changed; }
            @Override public void update(java.util.function.BiFunction<Model,Model,StatePatch<Model>> update, Runnable committed) { fail("No update expected"); }
            @Override public void force(Runnable committed) { fail("No force expected"); }
        });
        assertSame(changed, peer.props());
        assertSame(changed, peer.state());
        assertThrows(IllegalStateException.class, peer::initialState);
        assertThrows(IllegalStateException.class, () -> peer.bind(null));
    }
}
