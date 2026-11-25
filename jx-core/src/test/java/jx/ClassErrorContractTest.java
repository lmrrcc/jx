package jx;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassErrorContractTest {
    private record Model(int count, String note) {}
    private static final ClassSchema.Field<Model,Integer> COUNT = ClassSchema.field("count", ClassSchema.INTEGER, Model::count);
    private static final ClassSchema.Field<Model,String> NOTE = ClassSchema.field("note", ClassSchema.STRING.nullable(), Model::note);
    private static final ClassSchema<Model> MODEL = schema();
    private static ClassSchema<Model> schema() {
        return new ClassSchema<>(List.of(COUNT, NOTE), values -> new Model(values.get(COUNT), values.get(NOTE)));
    }
    private static final ClassComponentType.ContextAccess DEFAULTS = new ClassComponentType.ContextAccess() {
        @Override public <T> Supplier<T> current(Context<T> context) { return context::defaultValue; }
    };
    private static final class Boundary extends ClassComponent<Model,Model,Void> {
        private final String theme;
        private Throwable reportedError;
        private ComponentErrorInfo reportedInfo;
        private RuntimeException reportingFailure;
        private int reports;
        Boundary(Model props, Supplier<String> theme) { super(props, props); this.theme = theme.get(); }
        @Override public View render() { return null; }
        @Override protected void componentDidCatch(Throwable error, ComponentErrorInfo info) {
            reports++; reportedError = error; reportedInfo = info;
            if (reportingFailure != null) throw reportingFailure;
        }
    }
    private static final class PureBoundary extends PureComponent<Model,Model,Void> {
        private final String theme;
        PureBoundary(Model props, Supplier<String> theme) { super(props, props); this.theme = theme.get(); }
        @Override public View render() { return null; }
    }
    private static ClassComponentType<Model,Model,Boundary> plain() {
        return ClassComponentType.normal("Boundary", MODEL, MODEL, props -> new Boundary(props, () -> "plain"), Set.of());
    }

    @Test void eitherConfigurationOrderPreservesBothCallbacksAndTheContextualFactory() {
        Context<String> context = new Context<>("scoped");
        AtomicInteger constructions = new AtomicInteger();
        AtomicInteger derivations = new AtomicInteger();
        AtomicReference<Throwable> observed = new AtomicReference<>();
        var base = ClassComponentType.normalWithContext("Boundary", MODEL, MODEL, context,
                (Model props, Supplier<String> theme) -> {
                    constructions.incrementAndGet(); return new Boundary(props, theme);
                }, Set.of(ClassComponentType.Lifecycle.MOUNT, ClassComponentType.Lifecycle.CATCH));
        Model props = new Model(5, "props");
        Model pending = new Model(2, "pending");
        StatePatch<Model> fromProps = MODEL.patch().set(COUNT, 5);
        StatePatch<Model> fromError = MODEL.patch().set(NOTE, null);
        BiFunction<Model,Model,StatePatch<Model>> propsCallback = (next, state) -> {
            assertSame(props, next); assertSame(pending, state); derivations.incrementAndGet(); return fromProps;
        };
        Function<Throwable,StatePatch<Model>> errorCallback = error -> {
            observed.set(error); derivations.incrementAndGet(); return fromError;
        };
        var propsOnly = base.withDerivedStateFromProps(propsCallback);
        var errorOnly = base.withDerivedStateFromError(errorCallback);
        var propsFirst = propsOnly.withDerivedStateFromError(errorCallback);
        var errorFirst = errorOnly.withDerivedStateFromProps(propsCallback);
        assertFalse(base.hasDerivedStateFromProps()); assertFalse(base.hasDerivedStateFromError());
        assertFalse(propsOnly.hasDerivedStateFromError()); assertFalse(errorOnly.hasDerivedStateFromProps());
        assertEquals(0, constructions.get()); assertEquals(0, derivations.get());

        Throwable failure = new IllegalStateException("descendant");
        for (var descriptor : List.of(propsFirst, errorFirst)) {
            assertNotSame(base, descriptor);
            assertSame(context, descriptor.contextType());
            assertSame(MODEL, descriptor.propsSchema()); assertSame(MODEL, descriptor.stateSchema());
            assertEquals("Boundary", descriptor.name()); assertFalse(descriptor.pure());
            assertTrue(descriptor.lifecycle(ClassComponentType.Lifecycle.MOUNT));
            assertTrue(descriptor.lifecycle(ClassComponentType.Lifecycle.CATCH));
            assertEquals(fromProps.entries(), descriptor.deriveStateFromProps(props, pending).entries());
            assertEquals(fromError.entries(), descriptor.deriveStateFromError(failure).entries());
            assertSame(failure, observed.get());
            Boundary peer = descriptor.create(props, DEFAULTS);
            assertSame(props, peer.props()); assertSame(props, peer.initialState());
            assertEquals("scoped", peer.theme);
        }
        assertEquals(2, constructions.get()); assertEquals(4, derivations.get());
    }

    @Test void errorConfigurationKeepsPureIdentityAndDoesNotReplacePropsDerivation() {
        Context<String> context = new Context<>(null);
        StatePatch<Model> propsPatch = MODEL.patch().set(COUNT, 4);
        var base = ClassComponentType.pureWithContext("PureBoundary", MODEL, MODEL, context,
                PureBoundary::new, Set.of(ClassComponentType.Lifecycle.CATCH));
        var configured = base.withDerivedStateFromProps((props, state) -> propsPatch)
                .withDerivedStateFromError(error -> MODEL.patch());
        assertTrue(configured.pure()); assertSame(context, configured.contextType());
        assertTrue(configured.lifecycle(ClassComponentType.Lifecycle.CATCH));
        Model value = new Model(1, "initial");
        assertNull(configured.create(value, DEFAULTS).theme);
        assertEquals(propsPatch.entries(), configured.deriveStateFromProps(value, value).entries());
        assertFalse(base.hasDerivedStateFromError());
    }

    @Test void replacingErrorDerivationPreservesNullEmptyAndPartialPatchSemantics() {
        var base = plain();
        Throwable error = new IllegalArgumentException("render");
        StatePatch<Model> empty = MODEL.patch();
        StatePatch<Model> partial = MODEL.patch().set(NOTE, null);
        var noChange = base.withDerivedStateFromError(failure -> null);
        var emptyChange = noChange.withDerivedStateFromError(failure -> empty);
        var partialChange = emptyChange.withDerivedStateFromError(failure -> partial);
        assertNull(noChange.deriveStateFromError(error));
        StatePatch<Model> actualEmpty = emptyChange.deriveStateFromError(error);
        assertNotNull(actualEmpty); assertSame(MODEL, actualEmpty.schema());
        assertTrue(actualEmpty.entries().isEmpty());
        StatePatch<Model> actualPartial = partialChange.deriveStateFromError(error);
        assertSame(MODEL, actualPartial.schema()); assertEquals(1, actualPartial.entries().size());
        assertSame(NOTE, actualPartial.entries().getFirst().field());
        assertNull(actualPartial.entries().getFirst().value());
        assertThrows(IllegalStateException.class, () -> base.deriveStateFromError(error));
        assertThrows(NullPointerException.class, () -> base.withDerivedStateFromError(null));
        assertThrows(NullPointerException.class, () -> noChange.deriveStateFromError(null));
    }

    @Test void foreignPatchesAndThrowingDerivationsRemainExplicitFailures() {
        var base = plain();
        Throwable childFailure = new IllegalStateException("child");
        var foreign = base.withDerivedStateFromError(error -> schema().patch().set(COUNT, 1));
        assertThrows(IllegalArgumentException.class, () -> foreign.deriveStateFromError(childFailure));
        IllegalArgumentException callbackFailure = new IllegalArgumentException("fallback failed");
        var failed = base.withDerivedStateFromError(error -> {
            assertSame(childFailure, error); throw callbackFailure;
        });
        assertSame(callbackFailure, assertThrows(IllegalArgumentException.class,
                () -> failed.deriveStateFromError(childFailure)));
    }

    @Test void committedCatchDispatchPreservesIdentityAndPropagatesReportingFailures() {
        Boundary peer = plain().create(new Model(0, "ready"));
        Throwable error = new IllegalStateException("child");
        String stack = "\n    at Child (application.js:12:3)\n    at Boundary\n";
        ComponentErrorInfo info = new ComponentErrorInfo(stack);
        assertEquals(stack, info.componentStack());
        assertEquals("", new ComponentErrorInfo("").componentStack());
        assertThrows(NullPointerException.class, () -> new ComponentErrorInfo(null));
        peer.caught(error, info);
        assertEquals(1, peer.reports);
        assertSame(error, peer.reportedError); assertSame(info, peer.reportedInfo);
        assertThrows(NullPointerException.class, () -> peer.caught(null, info));
        assertThrows(NullPointerException.class, () -> peer.caught(error, null));
        assertEquals(1, peer.reports);
        IllegalArgumentException reportingFailure = new IllegalArgumentException("reporting failed");
        peer.reportingFailure = reportingFailure;
        assertSame(reportingFailure, assertThrows(IllegalArgumentException.class, () -> peer.caught(error, info)));
        assertEquals(2, peer.reports);
    }
}
