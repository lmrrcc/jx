package jx;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassContextContractTest {
    private record Model(int count) {}
    private static final ClassSchema.Field<Model,Integer> COUNT = ClassSchema.field("count", ClassSchema.INTEGER, Model::count);
    private static final ClassSchema<Model> MODEL = new ClassSchema<>(List.of(COUNT), reader -> new Model(reader.get(COUNT)));
    private static final ClassComponentType.ContextAccess DEFAULTS = new ClassComponentType.ContextAccess() {
        @Override public <T> Supplier<T> current(Context<T> context) { return context::defaultValue; }
    };
    private static final class Normal extends ClassComponent<Model,Model,Void> {
        private final String constructedWith;
        private final Supplier<String> theme;
        Normal(Model props, Supplier<String> theme) {
            super(props, props); this.theme = theme; this.constructedWith = theme.get();
        }
        @Override public View render() { return Views.element("span", Attributes.create(), theme.get()); }
    }
    private static final class Pure extends PureComponent<Model,Model,Void> {
        private final Supplier<String> theme;
        Pure(Model props, Supplier<String> theme) { super(props, props); this.theme = theme; }
        @Override public View render() { return Views.element("span", Attributes.create(), theme.get()); }
    }

    @Test void typedContextIsAvailableDuringConstructionAndCanBeNull() {
        Context<String> context = new Context<>("default");
        var normal = ClassComponentType.normalWithContext("ContextNormal", MODEL, MODEL, context, Normal::new, Set.of());
        Normal peer = normal.create(new Model(2), DEFAULTS);
        assertEquals("default", peer.constructedWith);
        assertEquals("default", peer.theme.get());
        assertEquals(2, peer.initialState().count());
        assertSame(context, normal.contextType());
        assertFalse(normal.pure());
        assertThrows(IllegalStateException.class, () -> normal.create(new Model(0)));
        assertThrows(NullPointerException.class, () -> normal.create(new Model(0), null));

        Context<String> nullable = new Context<>(null);
        var pure = ClassComponentType.pureWithContext("ContextPure", MODEL, MODEL, nullable, Pure::new, Set.of());
        assertNull(pure.create(new Model(0), DEFAULTS).theme.get());
        assertTrue(pure.pure());
        assertSame(nullable, pure.contextType());
    }

    @Test void compositionDoesNotEagerlyReadContextOrConstructPeersAndLegacyFactoriesKeepWorking() {
        var contextual = ClassComponentType.normalWithContext("LazyContext", MODEL, MODEL, new Context<>("default"),
                (Model props, Supplier<String> context) -> { throw new AssertionError("Peer constructed before rendering"); }, Set.of());
        assertTrue(Views.isValidElement(Views.component(contextual, new Model(1), "stable", null)));
        var legacy = ClassComponentType.normal("Legacy", MODEL, MODEL, props -> new Normal(props, () -> "legacy"), Set.of());
        assertNull(legacy.contextType());
        assertEquals("legacy", legacy.create(new Model(0)).constructedWith);
        var custom = ClassComponentType.pureWithContext("CustomPure", MODEL, MODEL,
                new Context<>("default"), Pure::new, Set.of(ClassComponentType.Lifecycle.SHOULD_UPDATE));
        assertTrue(custom.pure());
        assertTrue(custom.lifecycle(ClassComponentType.Lifecycle.SHOULD_UPDATE));
    }
}
