package jx.conformance;

import jx.Promise;
import jx.ComponentType;
import jx.Views;
import jx.conformance.cases.CombinedServerStream;
import jx.conformance.cases.NativePressurePage;
import jx.react.React;
import jx.react.ReactServer;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Framework-owned test boundary: the spec supplies a real Node Writable and releases data. */
final class CombinedServerExports {
    private CombinedServerExports() {}

    @JSFunctor interface Start extends JSObject { JSObject start(String request); }
    @JSFunctor interface Pipe extends JSObject { void pipe(JSObject writable); }
    @JSFunctor interface Release extends JSObject { void release(String result); }
    @JSFunctor interface Abort extends JSObject { void abort(); }
    @JSFunctor interface Errors extends JSObject { int count(); }
    @JSFunctor interface Fatal extends JSObject { JSObject start(); }

    static void install() {
        register(request -> new Request(request).export());
        registerPressure(() -> new Request("pressure", true).export());
        registerFatal(() -> new FatalRequest().export());
    }

    private static final ComponentType<Void> BROKEN_SHELL = new ComponentType<>("BrokenShell", ignored -> {
        throw new IllegalStateException("fatal shell failed");
    });

    private static final class FatalRequest {
        private int errors;
        private final ReactServer.PipeableStream stream = ReactServer.renderToPipeableStream(
                Views.component(BROKEN_SHELL, null),
                ReactServer.StreamOptions.defaults().withError(error -> errors++));

        JSObject export() {
            return terminalSession(React.thenable(stream.shellReady()), React.thenable(stream.allReady()),
                    stream::abort, () -> errors);
        }
    }

    private static final class Request {
        private Promise.Resolver<String> resolver;
        private final Promise<String> data = Promise.create(created -> resolver = created);
        private final ReactServer.PipeableStream stream;
        private int errors;

        Request(String request) { this(request, false); }

        Request(String request, boolean pressure) {
            stream = ReactServer.renderToPipeableStream(
                    pressure ? Views.component(NativePressurePage.JX, new NativePressurePage.Props(data))
                            : Views.component(CombinedServerStream.JX, new CombinedServerStream.Props(request, data)),
                    ReactServer.StreamOptions.defaults().withIdentifierPrefix(request + "-")
                            .withError(error -> errors++));
        }

        JSObject export() {
            return session(React.thenable(stream.shellReady()), React.thenable(stream.allReady()),
                    writable -> stream.pipe(writable), result -> resolver.resolve(result),
                    stream::abort, () -> errors);
        }
    }

    @JSBody(params = "start", script = "(globalThis.__jxBenchServer = globalThis.__jxBenchServer || {}).startCombinedStream = start;")
    private static native void register(Start start);

    @JSBody(params = "start", script = "globalThis.__jxBenchServer.startFatalStream = start;")
    private static native void registerFatal(Fatal start);

    @JSBody(params = "start", script = "globalThis.__jxBenchServer.startNativePressure = start;")
    private static native void registerPressure(Fatal start);

    @JSBody(params = {"shell", "all", "abort", "errors"},
            script = "return {shellReady: shell, allReady: all, abort: abort, errors: errors};")
    private static native JSObject terminalSession(JSObject shell, JSObject all, Abort abort, Errors errors);

    @JSBody(params = {"shellReady", "allReady", "pipe", "release", "abort", "errors"},
            script = "return {shellReady: shellReady, allReady: allReady, pipe: pipe, release: release, abort: abort, errors: errors};")
    private static native JSObject session(JSObject shellReady, JSObject allReady, Pipe pipe,
            Release release, Abort abort, Errors errors);
}
