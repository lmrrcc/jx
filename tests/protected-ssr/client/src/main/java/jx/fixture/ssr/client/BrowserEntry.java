package jx.fixture.ssr.client;

import jx.Promise;
import jx.fixture.ssr.SnapshotJson;
import jx.fixture.ssr.TodoPage;
import jx.fixture.ssr.TodoRoot;
import jx.react.React;
import org.teavm.jso.JSBody;

/** Hydrates exactly the public data sent in this response. No network load or second owner. */
public final class BrowserEntry {
    private BrowserEntry() {}
    public static void main(String[] arguments) {
        var snapshot = SnapshotJson.decode(bootstrap());
        React.hydrateStrict("app", TodoRoot.JX, new TodoPage.Props(snapshot, Promise.resolved(null)),
                React.RootOptions.defaults().withIdentifierPrefix("protected-")
                        .withRecoverableError(error -> report(error.error().getMessage()))
                        .withUncaughtError(error -> report(error.error().getMessage())));
    }

    @JSBody(script = "var element = document.getElementById('jx-snapshot'); if (!element) throw new Error('Missing SSR bootstrap'); return element.textContent;")
    private static native String bootstrap();

    @JSBody(params = "message", script = "console.error('Protected SSR hydration: ' + message);")
    private static native void report(String message);
}
