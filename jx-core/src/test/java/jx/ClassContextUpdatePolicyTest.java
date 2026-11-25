package jx;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassContextUpdatePolicyTest {
    private record Model(int count, String label) {}
    private static final ClassSchema.Field<Model,Integer> COUNT = ClassSchema.field("count", ClassSchema.INTEGER, Model::count);
    private static final ClassSchema.Field<Model,String> LABEL = ClassSchema.field("label", ClassSchema.STRING, Model::label);
    private static final ClassSchema<Model> MODEL = new ClassSchema<>(List.of(COUNT, LABEL),
            values -> new Model(values.get(COUNT), values.get(LABEL)));
    private static final ClassComponentType.ContextAccess DEFAULTS = new ClassComponentType.ContextAccess() {
        @Override public <T> Supplier<T> current(Context<T> context) { return context::defaultValue; }
    };

    private static final class Peer extends ClassComponent<Model,Model,Void> {
        private final Supplier<String> context;
        private int legacyCalls;
        Peer(Model props, Supplier<String> context) { super(props, props); this.context = context; }
        @Override protected boolean shouldComponentUpdate(Model props, Model state) { legacyCalls++; return false; }
        @Override public View render() { return null; }
    }
    private static final class PurePeer extends PureComponent<Model,Model,Void> {
        PurePeer(Model props, Supplier<String> context) { super(props, props); }
        @Override public View render() { return null; }
    }
    private static ClassComponentType<Model,Model,Peer> descriptor(Context<String> context) {
        return ClassComponentType.normalWithContext("ContextPolicy", MODEL, MODEL, context,
                Peer::new, Set.of(ClassComponentType.Lifecycle.MOUNT));
    }

    @Test void compositionPreservesBothDerivationsTheFactoryAndCurrentPeerValuesInEitherOrder() {
        Context<String> context = new Context<>("next-context");
        Model current = new Model(1, "current");
        Model nextProps = new Model(2, "next-props");
        Model nextState = new Model(3, "next-state");
        AtomicInteger constructions = new AtomicInteger();
        AtomicInteger policyCalls = new AtomicInteger();
        AtomicInteger derivations = new AtomicInteger();
        var base = ClassComponentType.normalWithContext("ContextPolicy", MODEL, MODEL, context,
                (Model props, Supplier<String> reader) -> {
                    constructions.incrementAndGet(); return new Peer(props, reader);
                }, Set.of(ClassComponentType.Lifecycle.MOUNT, ClassComponentType.Lifecycle.CATCH));
        StatePatch<Model> propsPatch = MODEL.patch().set(COUNT, 7);
        StatePatch<Model> errorPatch = MODEL.patch().set(COUNT, -1);
        Throwable childFailure = new IllegalStateException("child");
        BiFunction<Model,Model,StatePatch<Model>> deriveProps = (props, state) -> {
            assertSame(nextProps, props); assertSame(nextState, state); derivations.incrementAndGet(); return propsPatch;
        };
        Function<Throwable,StatePatch<Model>> deriveError = error -> {
            assertSame(childFailure, error); derivations.incrementAndGet(); return errorPatch;
        };
        ClassComponentType.ContextUpdatePolicy<Model,Model,Peer,String> policy = (peer, props, state, nextContext) -> {
            assertSame(current, peer.props()); assertSame(current, peer.state());
            assertSame(nextProps, props); assertSame(nextState, state);
            assertEquals("next-context", nextContext);
            policyCalls.incrementAndGet(); return true;
        };
        var derivedOnly = base.withDerivedStateFromProps(deriveProps).withDerivedStateFromError(deriveError);
        var policyOnly = base.withContextShouldUpdate(context, policy);
        var derivedFirst = derivedOnly.withContextShouldUpdate(context, policy);
        var policyFirst = policyOnly.withDerivedStateFromError(deriveError).withDerivedStateFromProps(deriveProps);
        assertFalse(base.hasContextShouldUpdate()); assertFalse(derivedOnly.hasContextShouldUpdate());
        assertFalse(base.lifecycle(ClassComponentType.Lifecycle.SHOULD_UPDATE));
        assertTrue(policyOnly.lifecycle(ClassComponentType.Lifecycle.SHOULD_UPDATE));
        assertFalse(policyOnly.hasDerivedStateFromProps()); assertFalse(policyOnly.hasDerivedStateFromError());
        assertEquals(0, constructions.get()); assertEquals(0, policyCalls.get()); assertEquals(0, derivations.get());

        for (var configured : List.of(derivedFirst, policyFirst)) {
            assertNotSame(base, configured); assertTrue(configured.hasContextShouldUpdate());
            assertSame(context, configured.contextType()); assertSame(MODEL, configured.propsSchema());
            assertSame(MODEL, configured.stateSchema()); assertEquals("ContextPolicy", configured.name());
            assertFalse(configured.pure()); assertTrue(configured.lifecycle(ClassComponentType.Lifecycle.MOUNT));
            assertTrue(configured.lifecycle(ClassComponentType.Lifecycle.CATCH));
            Peer peer = configured.create(current, DEFAULTS);
            assertEquals("next-context", peer.context.get());
            assertTrue(configured.shouldUpdate(peer, nextProps, nextState, DEFAULTS));
            assertEquals(0, peer.legacyCalls);
            assertSame(current, peer.props()); assertSame(current, peer.state());
            assertEquals(propsPatch.entries(), configured.deriveStateFromProps(nextProps, nextState).entries());
            assertEquals(errorPatch.entries(), configured.deriveStateFromError(childFailure).entries());
        }
        assertEquals(2, constructions.get()); assertEquals(2, policyCalls.get()); assertEquals(4, derivations.get());
    }

    @Test void policyReceivesTheActualPeerAndNullContextOverridesLegacyWithoutMutatingEarlierDescriptors() {
        Context<String> context = new Context<>(null);
        var base = descriptor(context);
        Model current = new Model(1, "current");
        Model next = new Model(2, "next");
        // The renderer passes a pending context separately from the peer's current reader.
        Peer peer = new Peer(current, () -> "current-context");
        assertFalse(base.shouldUpdate(peer, next, next, DEFAULTS));
        assertEquals(1, peer.legacyCalls);
        AtomicInteger calls = new AtomicInteger();
        var configured = base.withContextShouldUpdate(context, (instance, props, state, nextContext) -> {
            assertSame(peer, instance); assertSame(next, props); assertSame(next, state);
            assertEquals("current-context", instance.context.get());
            assertNull(nextContext); calls.incrementAndGet(); return true;
        });
        var replacement = configured.withContextShouldUpdate(context, (instance, props, state, nextContext) -> false);
        assertTrue(configured.shouldUpdate(peer, next, next, DEFAULTS));
        assertFalse(replacement.shouldUpdate(peer, next, next, DEFAULTS));
        assertTrue(configured.shouldUpdate(peer, next, next, DEFAULTS));
        assertEquals(2, calls.get()); assertEquals(1, peer.legacyCalls);
        assertEquals("current-context", peer.context.get());
        assertFalse(base.hasContextShouldUpdate());
    }

    @Test void policiesRequireTheDeclaredContextAndKeepNativePureIdentity() {
        Context<String> context = new Context<>("value");
        var contextual = descriptor(context);
        ClassComponentType.ContextUpdatePolicy<Model,Model,Peer,String> policy = (peer, props, state, nextContext) -> true;
        var plain = ClassComponentType.normal("Plain", MODEL, MODEL,
                props -> new Peer(props, () -> "plain"), Set.of());
        var pure = ClassComponentType.pureWithContext("Pure", MODEL, MODEL, context, PurePeer::new, Set.of());
        assertThrows(IllegalArgumentException.class, () -> plain.withContextShouldUpdate(context, policy));
        assertThrows(IllegalArgumentException.class, () -> contextual.withContextShouldUpdate(new Context<>("value"), policy));
        var customPure = pure.withContextShouldUpdate(context, (peer, props, state, nextContext) -> true);
        assertTrue(customPure.pure());
        assertTrue(customPure.hasContextShouldUpdate());
        assertThrows(NullPointerException.class, () -> contextual.withContextShouldUpdate(null, policy));
        assertThrows(NullPointerException.class, () -> contextual.withContextShouldUpdate(context, null));
        assertFalse(contextual.hasContextShouldUpdate()); assertFalse(plain.hasContextShouldUpdate());
        assertFalse(pure.hasContextShouldUpdate());
    }

    @Test void schemaAndRendererFailuresOccurBeforeThePolicyAndPolicyFailuresPropagateUnchanged() {
        Context<String> context = new Context<>("valid");
        Model valid = new Model(1, "valid");
        Model invalid = new Model(2, null);
        var base = descriptor(context);
        Peer peer = base.create(valid, DEFAULTS);
        AtomicInteger calls = new AtomicInteger();
        var configured = base.withContextShouldUpdate(context, (instance, props, state, nextContext) -> {
            calls.incrementAndGet(); return true;
        });
        assertThrows(IllegalArgumentException.class, () -> configured.shouldUpdate(peer, invalid, valid, DEFAULTS));
        assertThrows(IllegalArgumentException.class, () -> configured.shouldUpdate(peer, valid, invalid, DEFAULTS));
        assertThrows(NullPointerException.class, () -> configured.shouldUpdate(peer, null, valid, DEFAULTS));
        assertThrows(NullPointerException.class, () -> configured.shouldUpdate(peer, valid, null, DEFAULTS));
        assertThrows(NullPointerException.class, () -> configured.shouldUpdate(null, valid, valid, DEFAULTS));
        assertThrows(NullPointerException.class, () -> configured.shouldUpdate(peer, valid, valid, null));
        IllegalStateException accessFailure = new IllegalStateException("context reader failed");
        var failedAccess = new ClassComponentType.ContextAccess() {
            @Override public <T> Supplier<T> current(Context<T> requested) { throw accessFailure; }
        };
        assertSame(accessFailure, assertThrows(IllegalStateException.class,
                () -> configured.shouldUpdate(peer, valid, valid, failedAccess)));
        assertEquals(0, calls.get()); assertEquals(0, peer.legacyCalls);
        IllegalArgumentException policyFailure = new IllegalArgumentException("policy failed");
        var failedPolicy = base.withContextShouldUpdate(context, (instance, props, state, nextContext) -> {
            assertSame(peer, instance); throw policyFailure;
        });
        assertSame(policyFailure, assertThrows(IllegalArgumentException.class,
                () -> failedPolicy.shouldUpdate(peer, valid, valid, DEFAULTS)));
    }
}
