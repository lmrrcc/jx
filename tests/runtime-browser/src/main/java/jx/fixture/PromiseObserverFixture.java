package jx.fixture;

import jx.Attributes;
import jx.ComponentType;
import jx.Hooks;
import jx.Promise;
import jx.State;
import jx.View;
import jx.Views;
import jx.react.React;

/** A Java Promise observer must not starve React's independent native-thenable subscription. */
final class PromiseObserverFixture {
    static final ComponentType<Void> COMPONENT = new ComponentType<>("PromiseObserverFixture", ignored -> render());
    private static final ComponentType<Promise<String>> READER = new ComponentType<>("PromiseObserverReader", promise ->
            Views.element("output", Attributes.create().attr("id", "promise-observer-result"), Hooks.use(promise)));

    private PromiseObserverFixture() {}

    /** Owned by the committed parent above Suspense, not by the child that initially suspends. */
    private static final class Pending {
        final Promise<String> promise;
        private Promise.Resolver<String> resolver;

        Pending() {
            promise = Promise.create(value -> resolver = value);
            // Register first, before READER reaches Hooks.use and AsyncBridge.thenable subscribes.
            promise.whenSettled(value -> { throw new IllegalStateException("promise observer failed"); },
                    failure -> { throw new AssertionError("Unexpected observer-source rejection", failure); });
        }

        void resolve() { resolver.resolve("resolved value"); }
    }

    private static View render() {
        State<Pending> pending = Hooks.useStateLazy(Pending::new);
        State<Integer> pulse = Hooks.useState(0);
        return Views.element("section", Attributes.create().attr("id", "promise-observer-fixture"),
                Views.element("button", Attributes.create().attr("id", "promise-observer-resolve")
                        .on("onClick", event -> pending.value().resolve()), "Resolve observed promise"),
                Views.element("button", Attributes.create().attr("id", "promise-observer-pulse")
                        .on("onClick", event -> pulse.update(previous -> previous + 1)), "Pulse observer fixture"),
                Views.element("output", Attributes.create().attr("id", "promise-observer-pulse-count"), pulse.value()),
                React.suspense(
                        Views.element("p", Attributes.create().attr("id", "promise-observer-fallback"), "Waiting for observed promise"),
                        Views.component(READER, pending.value().promise)));
    }
}
