package jx;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassDerivedStateTest {
    private record Model(int count, String note) {}
    private static final ClassSchema.Field<Model,Integer> COUNT = ClassSchema.field("count", ClassSchema.INTEGER, Model::count);
    private static final ClassSchema.Field<Model,String> NOTE = ClassSchema.field("note", ClassSchema.STRING.nullable(), Model::note);
    private static final ClassSchema<Model> MODEL = schema();
    private static ClassSchema<Model> schema() {
        return new ClassSchema<>(List.of(COUNT, NOTE), reader -> new Model(reader.get(COUNT), reader.get(NOTE)));
    }
    private static final class Peer extends PureComponent<Model,Model,Void> {
        Peer(Model props) { super(props, new Model(0, "initial")); }
        @Override public View render() { return null; }
    }

    @Test void derivationKeepsNullDistinctFromEmptyAndPartialPatches() {
        var base = ClassComponentType.pure("Derived", MODEL, MODEL, Peer::new, Set.of());
        var configured = base.withDerivedStateFromProps((props, state) -> props.count() == state.count() ? null
                : MODEL.patch().set(COUNT, props.count()).set(NOTE, null));
        assertFalse(base.hasDerivedStateFromProps());
        assertTrue(configured.hasDerivedStateFromProps());
        assertNotSame(base, configured);
        assertNull(configured.deriveStateFromProps(new Model(2, "props"), new Model(2, "preserved")));
        StatePatch<Model> patch = configured.deriveStateFromProps(new Model(3, "props"), new Model(2, "old"));
        assertSame(MODEL, patch.schema());
        assertEquals(3, patch.entries().getFirst().value());
        assertNull(patch.entries().getLast().value());
        var empty = configured.withDerivedStateFromProps((props, state) -> MODEL.patch());
        assertNotNull(empty.deriveStateFromProps(new Model(1, ""), new Model(1, "")));
        assertTrue(empty.deriveStateFromProps(new Model(1, ""), new Model(1, "")).entries().isEmpty());
        assertThrows(IllegalStateException.class, () -> base.deriveStateFromProps(new Model(0, ""), new Model(0, "")));
    }

    @Test void wrongSchemaAndFailuresRemainExplicitWhileContextAndFactoryArePreserved() {
        Context<String> context = new Context<>("theme");
        var base = ClassComponentType.pureWithContext("ContextDerived", MODEL, MODEL, context,
                (Model props, java.util.function.Supplier<String> theme) -> new Peer(props),
                Set.of(ClassComponentType.Lifecycle.MOUNT));
        var otherSchema = schema();
        var invalid = base.withDerivedStateFromProps((props, state) -> otherSchema.patch());
        assertSame(context, invalid.contextType());
        assertTrue(invalid.pure());
        assertTrue(invalid.lifecycle(ClassComponentType.Lifecycle.MOUNT));
        assertThrows(IllegalArgumentException.class, () -> invalid.deriveStateFromProps(new Model(0, ""), new Model(0, "")));
        IllegalArgumentException failure = new IllegalArgumentException("derivation");
        var failed = base.withDerivedStateFromProps((props, state) -> { throw failure; });
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> failed.deriveStateFromProps(new Model(0, ""), new Model(0, ""))));
        assertThrows(NullPointerException.class, () -> base.withDerivedStateFromProps(null));
    }
}
