package jx;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassNullableStateTest {
    private record Props(String label) {}
    private record Sparse(Presence<Integer> count, Presence<String> note) {}
    private static final ClassSchema.Field<Props,String> LABEL =
            ClassSchema.field("label", ClassSchema.STRING, Props::label);
    private static final ClassSchema<Props> PROPS =
            new ClassSchema<>(List.of(LABEL), reader -> new Props(reader.get(LABEL)));
    private static final ClassSchema.Field<Sparse,Integer> COUNT =
            ClassSchema.optionalField("count", ClassSchema.INTEGER, Sparse::count);
    private static final ClassSchema.Field<Sparse,String> NOTE =
            ClassSchema.optionalField("note", ClassSchema.STRING.nullable(), Sparse::note);
    private static final ClassSchema<Sparse> STATE = stateSchema();
    private static ClassSchema<Sparse> stateSchema() {
        return new ClassSchema<>(List.of(COUNT, NOTE), reader -> new Sparse(reader.find(COUNT), reader.find(NOTE)));
    }
    private static final ClassComponentType.ContextAccess DEFAULTS = new ClassComponentType.ContextAccess() {
        @Override public <T> Supplier<T> current(Context<T> context) { return context::defaultValue; }
    };
    private static final class Peer extends ClassComponent<Props,Sparse,Void> {
        private final Supplier<String> context;
        Peer(Props props) { this(props, () -> "plain"); }
        Peer(Props props, Supplier<String> context) { super(props, null); this.context = context; }
        @Override public View render() { return null; }
    }
    private static ClassComponentType<Props,Sparse,Peer> descriptor() {
        return ClassComponentType.normal("Nullable", PROPS, STATE, Peer::new, Set.of());
    }

    @Test void sparseRecordsKeepAbsenceUndefinedAndExplicitNullDistinctWithoutDefaults() {
        Sparse missing = STATE.readPresence(field -> Presence.absent());
        Sparse undefined = STATE.readPresence(field -> Presence.undefined());
        Sparse mixed = STATE.readPresence(field -> field == COUNT ? Presence.absent() : Presence.present(null));
        assertEquals(new Sparse(Presence.absent(), Presence.absent()), missing);
        assertEquals(new Sparse(Presence.undefined(), Presence.undefined()), undefined);
        assertEquals(new Sparse(Presence.absent(), Presence.present(null)), mixed);
        assertNotEquals(missing, undefined);
        assertNotEquals(missing, mixed);
        assertNotEquals(Presence.undefined(), Presence.present(null));
        assertTrue(COUNT.optional());
        assertEquals(Presence.absent(), COUNT.presence(missing));
        assertEquals(Presence.undefined(), COUNT.presence(undefined));
        assertNull(NOTE.value(mixed));
        assertThrows(IllegalArgumentException.class, () -> COUNT.value(missing));
        assertThrows(IllegalArgumentException.class, () -> COUNT.value(undefined));

        // The original read API supplies defined values, including an explicit nullable null.
        Sparse legacy = STATE.read(field -> field == COUNT ? Integer.valueOf(4) : null);
        assertEquals(new Sparse(Presence.present(4), Presence.present(null)), legacy);
        STATE.validate(missing);
        STATE.validate(undefined);
        assertThrows(NullPointerException.class, () -> STATE.validate(null));
        assertThrows(NullPointerException.class,
                () -> STATE.validate(new Sparse(null, Presence.absent())));
        assertThrows(IllegalArgumentException.class,
                () -> STATE.validate(new Sparse(Presence.present(null), Presence.absent())));
    }

    @Test void requiredValuesAndReaderOwnershipRemainStrictWhileReferencesKeepIdentity() {
        assertFalse(LABEL.optional());
        assertThrows(IllegalArgumentException.class, () -> PROPS.readPresence(field -> Presence.absent()));
        assertThrows(IllegalArgumentException.class, () -> PROPS.readPresence(field -> Presence.undefined()));
        assertThrows(IllegalArgumentException.class, () -> PROPS.readPresence(field -> Presence.present(null)));
        var definedOnly = new ClassSchema<Sparse>(List.of(COUNT, NOTE),
                reader -> new Sparse(Presence.present(reader.get(COUNT)), reader.find(NOTE)));
        assertThrows(IllegalArgumentException.class, () -> definedOnly.readPresence(field -> Presence.absent()));
        assertThrows(IllegalArgumentException.class, () -> definedOnly.readPresence(field -> Presence.undefined()));

        ClassSchema.Field<Sparse,Integer> foreign =
                ClassSchema.optionalField("count", ClassSchema.INTEGER, Sparse::count);
        var wrongReader = new ClassSchema<Sparse>(List.of(COUNT, NOTE),
                reader -> new Sparse(reader.find(foreign), reader.find(NOTE)));
        assertThrows(IllegalArgumentException.class, () -> wrongReader.readPresence(field -> Presence.present(1)));
        assertThrows(IllegalArgumentException.class, () -> STATE.readPresence(field -> Presence.present("not an int")));

        record Token(String value) {}
        record Holder(Presence<Token> token) {}
        ClassSchema.ValueType<Token> tokenType = ClassSchema.reference(value -> {
            if (value instanceof Token token) return token;
            throw new IllegalArgumentException("Expected Token");
        });
        ClassSchema.Field<Holder,Token> tokenField = ClassSchema.optionalField("token", tokenType, Holder::token);
        var holderSchema = new ClassSchema<Holder>(List.of(tokenField), reader -> new Holder(reader.find(tokenField)));
        Token token = new Token("shared");
        Holder decoded = holderSchema.readPresence(field -> Presence.present(token));
        assertSame(token, tokenField.value(decoded));
        assertEquals(new Holder(Presence.absent()), holderSchema.readPresence(field -> Presence.absent()));
    }

    @Test void patchesDistinguishOmissionUndefinedAndNullAndRejectDuplicatesOrForeignFields() {
        StatePatch<Sparse> empty = STATE.patch();
        StatePatch<Sparse> patch = empty.undefined(COUNT).set(NOTE, null);
        assertTrue(empty.entries().isEmpty());
        assertSame(STATE, patch.schema());
        assertEquals(List.of(COUNT, NOTE), patch.entries().stream().map(StatePatch.Entry::field).toList());
        assertTrue(patch.entries().getFirst().undefined());
        assertNull(patch.entries().getFirst().value());
        assertFalse(patch.entries().getLast().undefined());
        assertNull(patch.entries().getLast().value());
        assertThrows(UnsupportedOperationException.class, () -> patch.entries().clear());
        assertFalse(new StatePatch.Entry<>(NOTE, null).undefined());
        assertThrows(IllegalArgumentException.class, () -> new StatePatch.Entry<>(COUNT, 1, true));
        assertThrows(IllegalArgumentException.class, () -> new StatePatch.Entry<>(LABEL, null, true));
        assertThrows(IllegalArgumentException.class, () -> PROPS.patch().undefined(LABEL));
        assertThrows(IllegalArgumentException.class, () -> STATE.patch().undefined(COUNT).set(COUNT, 1));
        assertThrows(IllegalArgumentException.class, () -> STATE.patch().set(COUNT, 1).undefined(COUNT));
        assertThrows(IllegalArgumentException.class, () -> STATE.patch().undefined(COUNT).undefined(COUNT));
        ClassSchema.Field<Sparse,Integer> foreign =
                ClassSchema.optionalField("count", ClassSchema.INTEGER, Sparse::count);
        assertThrows(IllegalArgumentException.class, () -> STATE.patch().undefined(foreign));
        assertThrows(IllegalArgumentException.class, () -> STATE.patch().set(foreign, 1));
    }

    @Test void nullAggregateStateRequiresDescriptorOptInAndNeverWeakensPropsOrFieldValidation() {
        Props props = new Props("current");
        var strict = descriptor();
        var nullable = strict.withNullableState();
        assertNotSame(strict, nullable);
        assertFalse(strict.nullableState());
        assertTrue(nullable.nullableState());
        assertThrows(NullPointerException.class, () -> strict.validateState(null));
        assertThrows(NullPointerException.class, () -> strict.create(props));
        assertDoesNotThrow(() -> nullable.validateState(null));
        Peer peer = nullable.create(props);
        assertSame(props, peer.props());
        assertNull(peer.initialState());
        assertNull(peer.state());
        assertThrows(NullPointerException.class, () -> nullable.create(null));
        assertThrows(NullPointerException.class, () -> nullable.propsSchema().validate(null));
        assertThrows(NullPointerException.class, () -> nullable.stateSchema().validate(null));
        assertThrows(IllegalArgumentException.class,
                () -> nullable.validateState(new Sparse(Presence.present(null), Presence.absent())));
        assertThrows(IllegalArgumentException.class, () -> nullable.create(new Props(null)));
    }

    @Test void nullableOptInComposesWithBothDerivationsAndPendingContextWithoutEagerCallsOrMutation() {
        Context<String> context = new Context<>("pending");
        Props current = new Props("current"), next = new Props("next");
        AtomicInteger factories = new AtomicInteger(), callbacks = new AtomicInteger();
        var base = ClassComponentType.normalWithContext("NullableContext", PROPS, STATE, context,
                (Props props, Supplier<String> reader) -> { factories.incrementAndGet(); return new Peer(props, reader); },
                Set.of(ClassComponentType.Lifecycle.MOUNT, ClassComponentType.Lifecycle.CATCH));
        StatePatch<Sparse> derivedPatch = STATE.patch().set(COUNT, 2);
        Throwable failure = new IllegalStateException("child");
        BiFunction<Props,Sparse,StatePatch<Sparse>> derive = (props, pending) -> {
            assertSame(next, props); assertNull(pending); callbacks.incrementAndGet(); return derivedPatch;
        };
        Function<Throwable,StatePatch<Sparse>> recover = error -> {
            assertSame(failure, error); callbacks.incrementAndGet(); return STATE.patch().set(NOTE, null);
        };
        ClassComponentType.ContextUpdatePolicy<Props,Sparse,Peer,String> policy = (peer, props, pending, nextContext) -> {
            assertSame(current, peer.props()); assertNull(peer.state());
            assertSame(next, props); assertNull(pending); assertEquals("pending", nextContext);
            assertEquals("pending", peer.context.get()); callbacks.incrementAndGet(); return false;
        };
        var first = base.withNullableState().withDerivedStateFromProps(derive)
                .withDerivedStateFromError(recover).withContextShouldUpdate(context, policy);
        var last = base.withContextShouldUpdate(context, policy).withDerivedStateFromError(recover)
                .withDerivedStateFromProps(derive).withNullableState();
        assertEquals(0, factories.get()); assertEquals(0, callbacks.get());
        assertFalse(base.nullableState()); assertFalse(base.hasDerivedStateFromProps());
        for (var configured : List.of(first, last)) {
            assertTrue(configured.nullableState());
            assertSame(context, configured.contextType());
            assertSame(PROPS, configured.propsSchema()); assertSame(STATE, configured.stateSchema());
            assertTrue(configured.lifecycle(ClassComponentType.Lifecycle.MOUNT));
            assertTrue(configured.lifecycle(ClassComponentType.Lifecycle.CATCH));
            assertTrue(configured.lifecycle(ClassComponentType.Lifecycle.SHOULD_UPDATE));
            Peer peer = configured.create(current, DEFAULTS);
            assertSame(derivedPatch, configured.deriveStateFromProps(next, null));
            assertFalse(configured.deriveStateFromError(failure).entries().getFirst().undefined());
            assertFalse(configured.shouldUpdate(peer, next, null, DEFAULTS));
            assertSame(current, peer.props()); assertNull(peer.state());
        }
        assertEquals(2, factories.get()); assertEquals(6, callbacks.get());
    }

    @Test void nullablePendingStateDoesNotBypassDerivedPatchOwnershipOrHideCallbackFailures() {
        Props props = new Props("props");
        var nullable = descriptor().withNullableState();
        ClassSchema<Sparse> other = stateSchema();
        var foreignProps = nullable.withDerivedStateFromProps((next, state) -> other.patch());
        var foreignError = nullable.withDerivedStateFromError(error -> other.patch());
        assertThrows(IllegalArgumentException.class, () -> foreignProps.deriveStateFromProps(props, null));
        assertThrows(IllegalArgumentException.class,
                () -> foreignError.deriveStateFromError(new IllegalStateException("child")));
        IllegalStateException failure = new IllegalStateException("derive");
        var failed = nullable.withDerivedStateFromProps((next, state) -> { assertNull(state); throw failure; });
        assertSame(failure, assertThrows(IllegalStateException.class, () -> failed.deriveStateFromProps(props, null)));
        var unchanged = nullable.withDerivedStateFromProps((next, state) -> null);
        assertNull(unchanged.deriveStateFromProps(props, null));
        var empty = nullable.withDerivedStateFromProps((next, state) -> STATE.patch());
        assertTrue(empty.deriveStateFromProps(props, null).entries().isEmpty());
    }
}
