package jx.conformance;

import jx.Promise;
import jx.Views;
import jx.conformance.cases.CombinedServerStream;
import jx.react.React;
import jx.react.ReactServer;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Java consumer of streamed HTML; the reference uses a real Node Writable. */
final class SinkCompletionExports {
    private SinkCompletionExports() {}
    @JSFunctor interface Start extends JSObject { JSObject start(String mode); }
    @JSFunctor interface Pipe extends JSObject { JSObject pipe(); }
    @JSFunctor interface Run extends JSObject { void run(); }
    @JSFunctor interface Text extends JSObject { String get(); }
    @JSFunctor interface Count extends JSObject { int get(); }
    @JSFunctor interface Flag extends JSObject { boolean get(); }

    static void install() { register(mode -> new Request(mode).export()); }

    private static final class Request {
        private final String mode;
        private final IllegalStateException failure = new IllegalStateException("sink failed");
        private final StringBuilder html = new StringBuilder();
        private Promise.Resolver<String> release;
        private final ReactServer.PipeableStream stream;
        private int writes;
        private int ends;
        private boolean originalFailure;

        Request(String mode) {
            this.mode = mode;
            Promise<String> data = Promise.create(resolver -> release = resolver);
            stream = ReactServer.renderToPipeableStream(
                    Views.component(CombinedServerStream.JX, new CombinedServerStream.Props("sink", data)),
                    ReactServer.StreamOptions.defaults().withError(error -> {}));
        }

        private JSObject pipe() {
            Promise<Void> delivered = stream.pipe(new ReactServer.HtmlSink() {
                @Override public void write(String chunk) {
                    writes++;
                    if (mode.equals("first") || mode.equals("late") && chunk.contains("data-testid=\"arrived\"")) {
                        throw failure;
                    }
                    html.append(chunk);
                }
                @Override public void end() {
                    ends++;
                    if (mode.equals("end")) throw failure;
                }
            });
            delivered.whenSettled(ignored -> {}, error -> originalFailure = error == failure);
            return React.thenable(delivered);
        }

        JSObject export() {
            return session(React.thenable(stream.shellReady()), this::pipe,
                    () -> release.resolve("arrived-\u00e8-\ud83d\ude00" + "\u00e8\ud83d\ude00".repeat(900)), stream::abort,
                    html::toString, () -> writes, () -> ends, () -> originalFailure);
        }
    }

    @JSBody(params = "start", script = "(globalThis.__jxBenchServer = globalThis.__jxBenchServer || {}).startSinkCompletion = start;")
    private static native void register(Start start);

    @JSBody(params = {"shell", "pipe", "release", "abort", "html", "writes", "ends", "originalFailure"},
            script = "return {shellReady:shell, pipe:pipe, release:release, abort:abort, html:html, writes:writes, ends:ends, originalFailure:originalFailure};")
    private static native JSObject session(JSObject shell, Pipe pipe, Run release, Run abort,
            Text html, Count writes, Count ends, Flag originalFailure);
}
