package jx.conformance;

import jx.LazyComponent;
import jx.react.LazyModules;
import jx.react.React;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/** Separate TeaVM entry point. No reference to the registry or to any case belongs here. */
public final class LazyHost {
    private LazyHost() {}

    static final LazyComponent<Void> ROUTE = LazyComponent.of("BenchRoute", () ->
            LazyModules.load("./route.js").map(module ->
                    React.binding("BenchRoute", LazyModules.call(module, "route"), ignored -> emptyProps())));

    @JSBody(script = "return {};")
    private static native JSObject emptyProps();

    public static void main(String[] arguments) {
        if (Bench.caseId().equals("strict-nested")) React.mount("app", LazyHostPage.JX, null);
        else React.mountStrict("app", LazyHostPage.JX, null);
    }
}
