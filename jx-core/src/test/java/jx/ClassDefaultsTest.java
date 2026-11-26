package jx;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassDefaultsTest {
    private record Props(String label) {}
    private record Model(int count) {}
    private static final ClassSchema.Field<Props,String> LABEL = ClassSchema.field("label", ClassSchema.STRING.nullable(), Props::label);
    private static final ClassSchema<Props> PROPS = new ClassSchema<>(List.of(LABEL), r -> new Props(r.get(LABEL)));
    private static final ClassSchema.Field<Model,Integer> COUNT = ClassSchema.field("count", ClassSchema.INTEGER, Model::count);
    private static final ClassSchema<Model> MODEL = new ClassSchema<>(List.of(COUNT), r -> new Model(r.get(COUNT)));

    @Test void defaultsAndRawCopiesPreserveSchemaIdentityAndNeverConstructAPeerEagerly() {
        var constructed = new AtomicInteger();
        var base = ClassComponentType.normal("Defaults", PROPS, MODEL, props -> {
            constructed.incrementAndGet(); return new Counter(props);
        }, Set.of());
        var defaults = ClassProps.empty(PROPS).set(LABEL, "default");
        var type = base.withDefaultProps(defaults);
        assertNull(base.defaultProps());
        assertSame(defaults, type.defaultProps());
        assertSame(defaults, type.withDerivedStateFromProps((p,s) -> null).withDerivedStateFromError(e -> null)
                .withNullableState().defaultProps());
        assertNull(type.withDefaultProps(null).defaultProps());
        var foreign = new ClassSchema<>(List.of(LABEL), r -> new Props(r.get(LABEL)));
        assertThrows(IllegalArgumentException.class, () -> base.withDefaultProps(ClassProps.empty(foreign)));
        var original = Views.classPropsView(type, ClassProps.empty(PROPS).undefined(LABEL), "stable", null);
        var unchanged = Views.cloneClassProps(original, null);
        var overridden = Views.cloneClassProps(unchanged, ClassProps.empty(PROPS).undefined(LABEL));
        var twice = Views.cloneClassProps(overridden, null);
        assertNull(original.overrides());
        assertTrue(unchanged.overrides().entries().isEmpty(), "No override is distinct from overriding with undefined");
        assertSame(original.input(), twice.input());
        assertEquals(Presence.undefined(), twice.overrides().find(LABEL));
        assertEquals(1, twice.overrides().entries().size());
        assertEquals("stable", Views.key(twice));
        assertTrue(Views.isValidElement(twice));
        assertEquals(0, constructed.get());
        assertThrows(IllegalArgumentException.class, () -> Views.classPropsView(type, ClassProps.empty(foreign)));
        assertThrows(IllegalArgumentException.class, () -> Views.cloneClassProps(original, ClassProps.empty(foreign)));
        assertEquals(0, constructed.get());
    }
    private static final class Counter extends ClassComponent<Props,Model,Void> {
        Counter(Props props) { super(props, new Model(0)); }
        @Override public View render() { return null; }
    }
}
