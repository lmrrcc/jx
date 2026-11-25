package jx.conformance;

import java.util.ArrayList;
import java.util.List;
import jx.ExternalStore;
import jx.Promise;
import jx.conformance.cases.ActConsumer;
import jx.react.React;
import jx.react.ReactDiagnostics;
import jx.react.ReactTesting;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Java consumer of the test API. The framework fixture exports only explicit test operations. */
public final class UtilitiesExports {
    private UtilitiesExports() {}
    @JSFunctor interface Later extends JSObject { JSObject run(); }
    @JSFunctor interface Release extends JSObject { void run(); }
    @JSFunctor interface Count extends JSObject { int get(); }

    public static final class Store implements ExternalStore<Integer> {
        private int value;
        private final List<Runnable> listeners = new ArrayList<>();
        @Override public Integer snapshot() { return value; }
        @Override public Runnable subscribe(Runnable listener) {
            listeners.add(listener);
            return () -> listeners.remove(listener);
        }
        public void publish(int next) {
            value = next;
            for (Runnable listener : List.copyOf(listeners)) listener.run();
        }
    }

    static void start() {
        Bench.seedMarkup("<div id=\"act-root\"></div>");
        Session session = new Session();
        install(session::mount, session::start, () -> session.release.resolve(null), session::nested,
                session::fail, session::throwing, session::unmount, () -> session.calls,
                ReactDiagnostics.captureOwnerStack());
    }

    private static final class Session {
        private record Receipt(String label) {}
        private final Store store = new Store();
        private Promise.Resolver<Void> release;
        private final Promise<Void> gate = Promise.create(resolver -> release = resolver);
        private React.Root root;
        private int calls;

        JSObject mount() {
            Receipt receipt = new Receipt("mounted");
            return report(ReactTesting.act(() -> {
                calls++;
                root = React.mount("act-root", ActConsumer.JX, new ActConsumer.Props(store));
                return Promise.resolved(receipt);
            }).map(returned -> {
                if (returned != receipt) throw new IllegalStateException("Act changed Java result identity");
                return returned.label();
            }));
        }
        JSObject start() {
            return report(ReactTesting.act(() -> {
                calls++;
                return gate.map(ignored -> { store.publish(1); return "arrived"; });
            }));
        }
        JSObject nested() {
            return report(ReactTesting.act(() -> ReactTesting.act(() -> {
                store.publish(3);
                return Promise.resolved("nested");
            })));
        }
        JSObject fail() { return report(ReactTesting.act(() -> Promise.rejected(new IllegalStateException("act rejected")))); }
        JSObject throwing() { return report(ReactTesting.act(() -> { throw new IllegalStateException("act threw"); })); }
        JSObject unmount() {
            return report(ReactTesting.act(() -> {
                root.close();
                root = null;
                return Promise.resolved("unmounted");
            }));
        }
    }

    private static JSObject report(Promise<String> completion) { return React.thenable(completion.map(UtilitiesExports::snapshot)); }
    // The snapshot is captured in the act completion callback, before a Playwright polling
    // assertion could accidentally hide an implementation that resolves before React flushes.
    @JSBody(params = "value", script = "return {value: value, count: document.getElementById('act-count')?.textContent ?? null, log: [...(globalThis.__benchLog || [])]};")
    private static native JSObject snapshot(String value);
    @JSBody(params = {"mount", "start", "release", "nested", "fail", "throwing", "unmount", "calls", "outside"},
            script = "document.getElementById('app').dataset.bench = 'act-utilities';"
                    + " globalThis.__benchUtilities = {mount, start, release, nested, fail, throwing, unmount, calls, outside};")
    private static native void install(Later mount, Later start, Release release, Later nested, Later fail,
                                      Later throwing, Later unmount, Count calls, String outside);
}
