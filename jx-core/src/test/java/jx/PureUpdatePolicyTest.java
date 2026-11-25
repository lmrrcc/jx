package jx;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PureUpdatePolicyTest {
    record Model(int count) {}
    static final ClassSchema.Field<Model,Integer> COUNT = ClassSchema.field("count", ClassSchema.INTEGER, Model::count);
    static final ClassSchema<Model> MODEL = new ClassSchema<>(List.of(COUNT), r -> new Model(r.get(COUNT)));
    static final ClassComponentType.ContextAccess DEFAULTS = new ClassComponentType.ContextAccess() {
        public <T> Supplier<T> current(Context<T> context) { return context::defaultValue; }
    };
    static final class Peer extends PureComponent<Model,Model,Void> {
        Peer(Model props) { super(props, new Model(0)); }
        @Override public View render() { return null; }
        @Override protected boolean shouldComponentUpdate(Model props, Model state) { return props.count() > 0; }
    }
    @Test void explicitPolicyKeepsNativePureIdentityAndDispatchesTheDeclaredOverride() {
        var type = ClassComponentType.pure("PurePolicy", MODEL, MODEL, Peer::new,
                Set.of(ClassComponentType.Lifecycle.SHOULD_UPDATE));
        var peer = type.create(new Model(0));
        assertTrue(type.pure());
        assertTrue(type.shouldUpdate(peer, new Model(1), new Model(0), null));
        assertFalse(type.shouldUpdate(peer, new Model(0), new Model(2), null));
    }
    @Test void contextPolicyIsImmutableAndTakesPrecedenceWithoutLosingThePureMarker() {
        Context<String> context = new Context<>("day");
        var original = ClassComponentType.pureWithContext("PureContextPolicy", MODEL, MODEL, context,
                (Model props, Supplier<String> ignored) -> new Peer(props), Set.of(ClassComponentType.Lifecycle.SHOULD_UPDATE));
        var configured = original.withContextShouldUpdate(context, (peer, props, state, pending) -> pending.equals("day"));
        var replacement = configured.withContextShouldUpdate(context, (peer, props, state, pending) -> false);
        Peer peer = original.create(new Model(0), DEFAULTS);
        assertTrue(configured.pure());
        assertFalse(original.hasContextShouldUpdate());
        assertFalse(original.shouldUpdate(peer, new Model(0), new Model(0), DEFAULTS));
        assertTrue(configured.shouldUpdate(peer, new Model(0), new Model(0), DEFAULTS));
        assertFalse(replacement.shouldUpdate(peer, new Model(0), new Model(0), DEFAULTS));
        assertTrue(configured.shouldUpdate(peer, new Model(0), new Model(0), DEFAULTS));
    }
}
