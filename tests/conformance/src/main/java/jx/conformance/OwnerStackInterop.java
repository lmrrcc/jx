package jx.conformance;

import jx.ComponentType;
import jx.conformance.cases.DiagnosticOwner;
import jx.react.React;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBodyImport;
import org.teavm.jso.JSObject;

/** Explicit React interoperability fixture: a real native owner creates the Java consumer.
 * React's ten-frame element creation stack can truncate deeply nested TeaVM call chains. */
public final class OwnerStackInterop {
    private OwnerStackInterop() {}
    public static final ComponentType<Void> COMPONENT = React.binding("OwnerStackInterop",
            owner(React.exportComponent(DiagnosticOwner.JX, ignored -> new DiagnosticOwner.Props("interop-owner-stack"))),
            ignored -> props());

    @JSBody(params = "child", script = "return function NativeOwner() { return React.createElement(child); };",
            imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native JSObject owner(JSObject child);
    @JSBody(script = "return {};") private static native JSObject props();
}
