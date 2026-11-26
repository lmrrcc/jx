package jx;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Descriptor/codec contracts only; committed browser capture needs its own runtime proof. */
class ClassCheckpointContractTest {
    private record Model(int count) {}
    private static final ClassSchema.Field<Model,Integer> COUNT =
            ClassSchema.field("count", ClassSchema.INTEGER, Model::count);
    private static final ClassSchema<Model> MODEL = schema();
    private static ClassSchema<Model> schema() {
        return new ClassSchema<>(List.of(COUNT), fields -> new Model(fields.get(COUNT)));
    }
    private static StateCodec<Model> codec(String schema) {
        return new StateCodec<>() {
            @Override public String schema() { return schema; }
            @Override public String encode(Model value) { return Integer.toString(value.count()); }
            @Override public Model decode(String payload) { return new Model(Integer.parseInt(payload)); }
        };
    }
    private static final StateCodec<Model> CODEC = codec("counter-v1");
    private static final ClassComponentType.ContextAccess DEFAULTS = new ClassComponentType.ContextAccess() {
        @Override public <T> Supplier<T> current(Context<T> context) { return context::defaultValue; }
    };
    private static final class Peer extends ClassComponent<Model,Model,Void> {
        private final String theme;
        Peer(Model props, Supplier<String> theme) { super(props, props); this.theme = theme.get(); }
        @Override public View render() { return null; }
    }
    private static final class PurePeer extends PureComponent<Model,Model,Void> {
        PurePeer(Model props) { super(props, props); }
        @Override public View render() { return null; }
    }
    private static ClassComponentType<Model,Model,Peer> plain() {
        return ClassComponentType.normal("Counter", MODEL, MODEL,
                props -> new Peer(props, () -> "plain"), Set.of());
    }

    @Test void invalidMetadataFailsBeforeCheckpointConfigurationIsPublished() {
        var base = plain();
        assertNull(base.checkpoint());
        assertThrows(NullPointerException.class, () -> base.withStateCheckpoint(null, CODEC));
        assertThrows(NullPointerException.class, () -> base.withStateCheckpoint("counter", null));
        for (String invalid : List.of("", " \t", "x".repeat(257))) {
            assertThrows(IllegalArgumentException.class, () -> base.withStateCheckpoint(invalid, CODEC));
            assertThrows(IllegalArgumentException.class, () -> base.withStateCheckpoint("counter", codec(invalid)));
        }
        assertThrows(NullPointerException.class, () -> base.withStateCheckpoint("counter", codec(null)));
        var longest = base.withStateCheckpoint("x".repeat(256), codec("s".repeat(256)));
        assertEquals(256, longest.checkpoint().signature().length());
        assertEquals(256, longest.checkpoint().codec().schema().length());
        assertNull(base.checkpoint());
    }

    @Test void configurationOrderPreservesDerivationsContextFactoryAndLifecycleWithoutEagerWork() {
        Context<String> theme = new Context<>("night");
        AtomicInteger constructions = new AtomicInteger();
        AtomicInteger calculations = new AtomicInteger();
        var base = ClassComponentType.normalWithContext("Counter", MODEL, MODEL, theme,
                (Model props, Supplier<String> current) -> {
                    constructions.incrementAndGet(); return new Peer(props, current);
                }, Set.of(ClassComponentType.Lifecycle.MOUNT, ClassComponentType.Lifecycle.CATCH));
        BiFunction<Model,Model,StatePatch<Model>> props = (next, pending) -> {
            calculations.incrementAndGet(); return MODEL.patch().set(COUNT, next.count());
        };
        Function<Throwable,StatePatch<Model>> error = failure -> {
            calculations.incrementAndGet(); return MODEL.patch().set(COUNT, -1);
        };
        var first = base.withStateCheckpoint("counter", CODEC)
                .withDerivedStateFromProps(props).withDerivedStateFromError(error)
                .withContextShouldUpdate(theme, (peer, next, pending, current) -> current.equals("night"));
        var last = base.withDerivedStateFromError(error).withDerivedStateFromProps(props)
                .withContextShouldUpdate(theme, (peer, next, pending, current) -> current.equals("night"))
                .withStateCheckpoint("counter", CODEC);
        assertNull(base.checkpoint());
        assertFalse(base.hasDerivedStateFromProps()); assertFalse(base.hasDerivedStateFromError());
        assertEquals(0, constructions.get()); assertEquals(0, calculations.get());
        for (var configured : List.of(first, last)) {
            assertNotSame(base, configured);
            assertSame(CODEC, configured.checkpoint().codec());
            assertSame(theme, configured.contextType());
            assertSame(MODEL, configured.propsSchema()); assertSame(MODEL, configured.stateSchema());
            assertTrue(configured.lifecycle(ClassComponentType.Lifecycle.MOUNT));
            assertTrue(configured.lifecycle(ClassComponentType.Lifecycle.CATCH));
            assertTrue(configured.hasContextShouldUpdate());
            Model value = new Model(7);
            Peer peer = configured.create(value, DEFAULTS);
            assertSame(value, peer.props()); assertSame(value, peer.initialState());
            assertEquals("night", peer.theme);
            assertTrue(configured.shouldUpdate(peer, value, value, DEFAULTS));
            assertEquals(7, configured.deriveStateFromProps(value, new Model(2)).entries().getFirst().value());
            assertEquals(-1, configured.deriveStateFromError(new IllegalStateException()).entries().getFirst().value());
        }
        assertEquals(2, constructions.get()); assertEquals(4, calculations.get());
    }

    @Test void replacingCodecIsImmutableAndDecodingCreatesIndependentValues() {
        var first = plain().withStateCheckpoint("counter-v1", CODEC);
        StateCodec<Model> nextCodec = codec("counter-v2");
        var replacement = first.withStateCheckpoint("counter-v2", nextCodec);
        assertEquals("counter-v1", first.checkpoint().signature());
        assertSame(CODEC, first.checkpoint().codec());
        assertEquals("counter-v2", replacement.checkpoint().signature());
        assertSame(nextCodec, replacement.checkpoint().codec());
        Model oldValue = new Model(42);
        String payload = first.checkpoint().codec().encode(oldValue);
        Model decoded = replacement.checkpoint().codec().decode(payload);
        Model decodedAgain = replacement.checkpoint().codec().decode(payload);
        assertEquals(oldValue, decoded); assertEquals(decoded, decodedAgain);
        assertNotSame(oldValue, decoded); assertNotSame(decoded, decodedAgain);
        MODEL.validate(decoded);
        assertThrows(NumberFormatException.class, () -> replacement.checkpoint().codec().decode("invalid"));
    }

    @Test void checkpointDoesNotRelaxPatchOwnershipOrAdmitUnqualifiedPureClasses() {
        var configured = plain().withStateCheckpoint("counter", CODEC);
        ClassSchema<Model> foreign = schema();
        var props = configured.withDerivedStateFromProps((next, pending) -> foreign.patch());
        var error = configured.withDerivedStateFromError(failure -> foreign.patch());
        assertThrows(IllegalArgumentException.class,
                () -> props.deriveStateFromProps(new Model(1), new Model(0)));
        assertThrows(IllegalArgumentException.class,
                () -> error.deriveStateFromError(new IllegalStateException()));
        var pure = ClassComponentType.pure("Pure", MODEL, MODEL, PurePeer::new, Set.of());
        assertThrows(IllegalArgumentException.class, () -> pure.withStateCheckpoint("pure", CODEC));
        assertNull(pure.checkpoint()); assertTrue(pure.pure());
    }

    @Test void nullableCheckpointCombinationsPreserveOptionsInBothOrders() {
        var strict = plain();
        var nullable = strict.withNullableState();
        var checkpointed = strict.withStateCheckpoint("counter", CODEC);
        var first = nullable.withStateCheckpoint("counter", CODEC);
        var last = checkpointed.withNullableState();
        assertTrue(first.nullableState()); assertTrue(last.nullableState());
        assertSame(CODEC, first.checkpoint().codec()); assertSame(CODEC, last.checkpoint().codec());
        assertTrue(nullable.nullableState()); assertNull(nullable.checkpoint());
        assertFalse(checkpointed.nullableState()); assertSame(CODEC, checkpointed.checkpoint().codec());
        assertEquals(new Model(3), checkpointed.create(new Model(3)).initialState());
    }

    private record Sparse(Presence<Integer> count) {}
    private static final class SparsePeer extends ClassComponent<Model,Sparse,Void> {
        SparsePeer(Model props) { super(props, new Sparse(Presence.present(props.count()))); }
        @Override public View render() { return null; }
    }

    @Test void optionalStateSchemaAcceptsExplicitCodecWithoutExecutingItAtConfigurationTime() {
        var count = ClassSchema.optionalField("count", ClassSchema.INTEGER, Sparse::count);
        var sparse = new ClassSchema<Sparse>(List.of(count), reader -> new Sparse(reader.find(count)));
        AtomicInteger calls = new AtomicInteger();
        var type = ClassComponentType.normal("Sparse", MODEL, sparse, (Model props) -> {
            calls.incrementAndGet(); return new SparsePeer(props);
        }, Set.of());
        StateCodec<Sparse> codec = new StateCodec<>() {
            public String schema() { return "sparse-v1"; }
            public String encode(Sparse value) { calls.incrementAndGet(); return ""; }
            public Sparse decode(String value) { calls.incrementAndGet(); return new Sparse(Presence.absent()); }
        };
        var configured = type.withStateCheckpoint("sparse", codec);
        assertSame(codec, configured.checkpoint().codec());
        assertSame(sparse, configured.stateSchema());
        assertEquals(0, calls.get());
        assertNull(type.checkpoint());
        assertEquals(new Sparse(Presence.present(3)), type.create(new Model(3)).initialState());
    }
}
