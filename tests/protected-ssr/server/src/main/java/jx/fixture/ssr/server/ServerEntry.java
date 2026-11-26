package jx.fixture.ssr.server;

import jx.Attributes;
import jx.Promise;
import jx.View;
import jx.Views;
import jx.example.contract.SessionApi;
import jx.example.contract.TodoApi;
import jx.fixture.ssr.SnapshotJson;
import jx.fixture.ssr.TodoPage;
import jx.fixture.ssr.TodoRoot;
import jx.fixture.ssr.TodoSnapshot;
import jx.react.React;
import jx.react.ReactServer;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Node-side compiled Java renderer. Host I/O/session authority stay outside rendering. */
public final class ServerEntry {
    private ServerEntry() {}
    public static void main(String[] arguments) {
        register((session, todos) -> new Request(SnapshotJson.fromResponses(session, todos)).export(),
                SessionApi.CURRENT, TodoApi.COLLECTION, SessionApi.COOKIE);
    }

    private static final class Request {
        private Promise.Resolver<Void> resolver;
        private final Promise<Void> ready = Promise.create(value -> resolver = value);
        private final ReactServer.PipeableStream stream;
        private boolean retired;
        private int errors;

        Request(TodoSnapshot snapshot) {
            stream = ReactServer.renderToPipeableStream(document(snapshot, ready),
                    ReactServer.StreamOptions.defaults().withIdentifierPrefix("protected-")
                            .withError(error -> errors++));
        }

        private void release() {
            if (!retired && resolver != null) { resolver.resolve(null); resolver = null; }
        }

        private void abort() {
            if (retired) return;
            retired = true;
            resolver = null;
            stream.abort();
        }

        private void pipe(JSObject destination) {
            if (retired) throw new IllegalStateException("SSR request is retired");
            stream.pipe(destination);
        }

        JSObject export() {
            return session(React.thenable(stream.shellReady()), React.thenable(stream.allReady()),
                    this::pipe, this::release, this::abort, () -> errors);
        }
    }

    private static View document(TodoSnapshot snapshot, Promise<Void> ready) {
        return Views.element("html", Attributes.create().attr("lang", "en"),
                Views.element("head", Attributes.create(),
                        Views.element("meta", Attributes.create().attr("charset", "UTF-8")),
                        Views.element("title", Attributes.create(), "Protected JX todos"),
                        Views.element("link", Attributes.create().attr("rel", "icon").attr("href", "data:,"))),
                Views.element("body", Attributes.create(),
                        Views.element("div", Attributes.create().attr("id", "app"),
                                React.strictMode(Views.component(TodoRoot.JX, new TodoPage.Props(snapshot, ready)))),
                        Views.element("script", Attributes.create().attr("id", "jx-snapshot").attr("type", "application/json"),
                                SnapshotJson.scriptData(snapshot)),
                        Views.element("script", Attributes.create().attr("type", "module").attr("src", "/assets/application.js"))));
    }

    @JSFunctor interface Open extends JSObject { JSObject start(String session, String todos); }
    @JSFunctor interface Pipe extends JSObject { void pipe(JSObject writable); }
    @JSFunctor interface Action extends JSObject { void run(); }
    @JSFunctor interface Errors extends JSObject { int count(); }

    @JSBody(params = {"open", "sessionPath", "todosPath", "cookie"}, script =
            "globalThis.__jxProtectedSSR = Object.freeze({open: open, sessionPath: sessionPath, todosPath: todosPath, cookie: cookie});")
    private static native void register(Open open, String sessionPath, String todosPath, String cookie);

    @JSBody(params = {"shell", "all", "pipe", "release", "abort", "errors"}, script =
            "return Object.freeze({shellReady: shell, allReady: all, pipe: pipe, release: release, abort: abort, errors: errors});")
    private static native JSObject session(JSObject shell, JSObject all, Pipe pipe, Action release, Action abort, Errors errors);
}
