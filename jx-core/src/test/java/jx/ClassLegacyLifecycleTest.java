package jx;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static jx.ClassComponentType.LegacyVariant.*;
import static org.junit.jupiter.api.Assertions.*;

/** Descriptor dispatch only. Native scheduling and suppression require the browser consumer. */
class ClassLegacyLifecycleTest {
    private record Value(int count) {}
    private static final ClassSchema.Field<Value,Integer> COUNT = ClassSchema.field("count", ClassSchema.INTEGER, Value::count);
    private static final ClassSchema<Value> SCHEMA = new ClassSchema<>(List.of(COUNT), reader -> new Value(reader.get(COUNT)));
    private static final Context<String> THEME = new Context<>("day");
    private static final ClassComponentType.ContextAccess DEFAULT = new ClassComponentType.ContextAccess() {
        public <T> Supplier<T> current(Context<T> context) { return context::defaultValue; }
    };
    private static final StateCodec<Value> CODEC = new StateCodec<>() {
        public String schema() { return "legacy-model:1"; }
        public String encode(Value value) { return Integer.toString(value.count()); }
        public Value decode(String text) { return new Value(Integer.parseInt(text)); }
    };
    private static final class Peer extends ClassComponent<Value,Value,Void> {
        final List<String> calls = new ArrayList<>();
        Peer(Value props) { super(props, props); }
        public View render() { return null; }
        protected void componentWillMount() { calls.add("mount"); }
        protected void UNSAFE_componentWillMount() { calls.add("unsafe-mount"); }
        protected void componentWillReceiveProps(Value next) { calls.add("receive:" + next.count()); }
        protected void UNSAFE_componentWillReceiveProps(Value next) { calls.add("unsafe-receive:" + next.count()); }
        protected void componentWillUpdate(Value next, Value state) { calls.add("update:" + next.count() + ":" + state.count()); }
        protected void UNSAFE_componentWillUpdate(Value next, Value state) { calls.add("unsafe-update:" + next.count() + ":" + state.count()); }
    }
    private static ClassComponentType<Value,Value,Peer> plain() {
        return ClassComponentType.normalWithContext("Legacy", SCHEMA, SCHEMA, THEME,
                (props, theme) -> new Peer(props), Set.of());
    }

    @Test void eachSpellingDispatchesItsOwnPeerMethodWithoutInventingCapabilities() {
        var type = plain();
        Peer peer = type.create(new Value(1), DEFAULT);
        peer.beforeMount(DEPRECATED); peer.beforeMount(UNSAFE);
        type.beforeReceiveProps(peer, DEPRECATED, new Value(2), null);
        type.beforeReceiveProps(peer, UNSAFE, new Value(2), null);
        type.beforeUpdate(peer, DEPRECATED, new Value(2), new Value(3), null);
        type.beforeUpdate(peer, UNSAFE, new Value(2), new Value(3), null);
        assertEquals(List.of("mount", "unsafe-mount", "receive:2", "unsafe-receive:2", "update:2:3", "unsafe-update:2:3"), peer.calls);
        assertFalse(type.lifecycle(ClassComponentType.Lifecycle.WILL_MOUNT));
        assertFalse(type.lifecycle(ClassComponentType.Lifecycle.WILL_RECEIVE_PROPS));
        assertFalse(type.lifecycle(ClassComponentType.Lifecycle.WILL_UPDATE));
        assertEquals(new Value(1), peer.props()); assertEquals(new Value(1), peer.state());
    }

    @Test void typedPoliciesPreserveCurrentValuesAndAllModifierMetadata() {
        var original = plain();
        var configured = original
                .withContextWillReceiveProps(THEME, DEPRECATED, (peer, next, context) ->
                        peer.calls.add("typed-receive:" + peer.props().count() + ":" + next.count() + ":" + context))
                .withContextWillUpdate(THEME, UNSAFE, (peer, next, state, context) ->
                        peer.calls.add("typed-update:" + peer.state().count() + ":" + state.count() + ":" + context))
                .withContextShouldUpdate(THEME, (peer, next, state, context) -> true)
                .withDerivedStateFromProps((props, state) -> null)
                .withDerivedStateFromError(error -> null)
                .withStateCheckpoint("legacy", CODEC);
        assertFalse(original.hasContextWillReceiveProps(DEPRECATED));
        assertTrue(configured.lifecycle(ClassComponentType.Lifecycle.WILL_RECEIVE_PROPS));
        assertTrue(configured.lifecycle(ClassComponentType.Lifecycle.UNSAFE_WILL_UPDATE));
        assertFalse(configured.lifecycle(ClassComponentType.Lifecycle.UNSAFE_WILL_RECEIVE_PROPS));
        assertFalse(configured.lifecycle(ClassComponentType.Lifecycle.WILL_UPDATE));
        assertTrue(configured.hasContextShouldUpdate());
        assertTrue(configured.hasDerivedStateFromProps()); assertTrue(configured.hasDerivedStateFromError());
        assertSame(CODEC, configured.checkpoint().codec());
        Peer peer = configured.create(new Value(1), DEFAULT);
        assertEquals(List.of(), peer.calls);
        configured.beforeReceiveProps(peer, DEPRECATED, new Value(2), DEFAULT);
        configured.beforeReceiveProps(peer, UNSAFE, new Value(2), null);
        configured.beforeUpdate(peer, DEPRECATED, new Value(2), new Value(3), null);
        configured.beforeUpdate(peer, UNSAFE, new Value(2), new Value(3), DEFAULT);
        assertEquals(List.of("typed-receive:1:2:day", "unsafe-receive:2", "update:2:3", "typed-update:1:3:day"), peer.calls);
        var replaced = configured.withContextWillReceiveProps(THEME, DEPRECATED,
                (value, next, theme) -> value.calls.add("replacement"));
        assertTrue(replaced.hasContextWillUpdate(UNSAFE)); assertSame(CODEC, replaced.checkpoint().codec());
        replaced.beforeReceiveProps(peer, DEPRECATED, new Value(4), DEFAULT);
        assertEquals("replacement", peer.calls.getLast());
        assertTrue(original.withContextWillUpdate(THEME, UNSAFE, (p, next, state, theme) -> {})
                .withNullableState().hasContextWillUpdate(UNSAFE));
    }

    @Test void foreignContextInvalidInputAndOriginalCallbackFailuresRemainVisible() {
        var type = plain();
        assertThrows(IllegalArgumentException.class, () -> type.withContextWillReceiveProps(
                new Context<>("day"), DEPRECATED, (peer, props, context) -> {}));
        assertThrows(NullPointerException.class, () -> type.withContextWillUpdate(THEME, null, (peer, props, state, context) -> {}));
        assertThrows(NullPointerException.class, () -> type.withContextWillReceiveProps(THEME, UNSAFE, null));
        var failure = new IllegalStateException("original failure");
        var configured = type.withContextWillUpdate(THEME, UNSAFE, (peer, props, state, context) -> { throw failure; });
        Peer peer = configured.create(new Value(1), DEFAULT);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> configured.beforeUpdate(peer, UNSAFE, new Value(2), new Value(3), DEFAULT)));
        assertThrows(NullPointerException.class, () -> configured.beforeUpdate(peer, UNSAFE, new Value(2), null, DEFAULT));
        assertThrows(NullPointerException.class, () -> configured.beforeUpdate(peer, UNSAFE, new Value(2), new Value(3), null));
    }

    @Test void nullableCheckpointAndDefaultsSurviveLegacyPoliciesInEitherOrder() {
        var defaults = ClassProps.empty(SCHEMA).set(COUNT, 7);
        StateCodec<Value> nullable = new StateCodec<>() {
            public String schema() { return "nullable-legacy:1"; }
            public String encode(Value value) { return value == null ? "null" : Integer.toString(value.count()); }
            public Value decode(String text) { return text.equals("null") ? null : new Value(Integer.parseInt(text)); }
        };
        var original = plain();
        var before = original.withNullableState().withStateCheckpoint("legacy-null", nullable)
                .withDefaultProps(defaults)
                .withContextWillReceiveProps(THEME, DEPRECATED, (peer, props, context) -> peer.calls.add("receive:" + context))
                .withContextWillUpdate(THEME, UNSAFE, (peer, props, state, context) -> peer.calls.add("update:" + state + ":" + context));
        var after = original.withContextWillReceiveProps(THEME, DEPRECATED, (peer, props, context) -> peer.calls.add("receive:" + context))
                .withContextWillUpdate(THEME, UNSAFE, (peer, props, state, context) -> peer.calls.add("update:" + state + ":" + context))
                .withDefaultProps(defaults).withStateCheckpoint("legacy-null", nullable).withNullableState();
        for (var type : List.of(before, after)) {
            assertTrue(type.nullableState());
            assertSame(defaults, type.defaultProps());
            assertSame(nullable, type.checkpoint().codec());
            assertSame(SCHEMA, type.stateSchema());
            assertDoesNotThrow(() -> type.validateState(type.checkpoint().codec().decode("null")));
            Peer peer = type.create(new Value(1), DEFAULT);
            type.beforeReceiveProps(peer, DEPRECATED, new Value(2), DEFAULT);
            type.beforeUpdate(peer, UNSAFE, new Value(2), null, DEFAULT);
            assertEquals(List.of("receive:day", "update:null:day"), peer.calls);
            assertEquals(new Value(1), peer.state());
        }
        assertFalse(original.nullableState()); assertNull(original.checkpoint());
        assertFalse(original.hasContextWillUpdate(UNSAFE));
    }
}
