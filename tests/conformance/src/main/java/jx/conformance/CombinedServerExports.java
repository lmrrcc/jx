package jx.conformance;

import jx.Promise;
import jx.Views;
import jx.conformance.cases.CombinedServerStream;
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

    static void install() { register(request -> new Request(request).export()); }

    private static final class Request {
        private Promise.Resolver<String> resolver;
        private final Promise<String> data = Promise.create(created -> resolver = created);
        private final ReactServer.PipeableStream stream;
        private int errors;

        Request(String request) {
            stream = ReactServer.renderToPipeableStream(
                    Views.component(CombinedServerStream.JX, new CombinedServerStream.Props(request, data)),
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

    @JSBody(params = {"shellReady", "allReady", "pipe", "release", "abort", "errors"},
            script = "return {shellReady: shellReady, allReady: allReady, pipe: pipe, release: release, abort: abort, errors: errors};")
    private static native JSObject session(JSObject shellReady, JSObject allReady, Pipe pipe,
            Release release, Abort abort, Errors errors);
}
