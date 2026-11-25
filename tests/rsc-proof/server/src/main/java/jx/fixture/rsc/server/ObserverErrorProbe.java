package jx.fixture.rsc.server;

import java.util.Objects;
import java.util.function.Consumer;
import jx.Promise;
import jx.View;
import jx.react.React;
import jx.react.ReactServerComponents;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSExceptions;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSString;

/** Fixture-owned observers exercise the real Node error-reporting fallback. */
final class ObserverErrorProbe {
    private ObserverErrorProbe() {}
    @JSFunctor interface Action extends JSObject { void run(); }
    @JSFunctor interface Value extends JSObject { JSObject get(); }

    static JSObject create(Consumer<String> note) {
        Objects.requireNonNull(note);
        // Install the actual renderer before registering observers, so registration
        // exercises context-aware reactions rather than the pre-bootstrap fallback.
        ReactServerComponents.element(View.Empty.INSTANCE);
        Session session = new Session(note);
        return handle(session::resolve, React.thenable(session.source),
                () -> React.thenable(session.source),
                () -> JSExceptions.getJSException(session.failure), session.value);
    }

    private static final class Session {
        private final IllegalStateException failure = new IllegalStateException("node observer failed");
        private final JSObject value = JSString.valueOf("kept");
        private final Promise<JSObject> source;
        private Promise.Resolver<JSObject> resolver;

        Session(Consumer<String> note) {
            source = Promise.create(created -> resolver = created);
            source.whenSettled(result -> {
                note.accept("throwing");
                throw failure;
            }, error -> { throw new AssertionError("Observer source unexpectedly rejected", error); });
            source.whenSettled(result -> note.accept("sibling"),
                    error -> { throw new AssertionError("Sibling unexpectedly rejected", error); });
        }

        void resolve() { resolver.resolve(value); }
    }

    @JSBody(params = {"resolve", "thenable", "again", "failure", "value"},
            script = "return {resolve, thenable, again, failure, value};")
    private static native JSObject handle(Action resolve, JSObject thenable, Value again, Value failure, JSObject value);
}
