package jx.conformance;

import java.util.function.Supplier;
import jx.ComponentType;
import jx.Promise;
import jx.conformance.cases.FormNativeSubmit;
import jx.react.React;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBodyImport;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Explicit interoperability fixture: a native React form contains a Java status reader. */
public final class NativeFormStatus {
    private NativeFormStatus() {}
    public static final ComponentType<JSObject> TYPE = React.binding("NativeFormStatus",
            component(React.exportComponent(FormNativeSubmit.JX, ignored -> null)), props -> props);
    public static JSObject props(Supplier<Promise<Void>> submit) {
        Submit nativeSubmit = () -> React.thenable(submit.get());
        return props(nativeSubmit);
    }
    @JSFunctor interface Submit extends JSObject { JSObject run(); }
    @JSBody(params = "submit", script = "return {submit: submit};")
    private static native JSObject props(Submit submit);
    @JSBody(params = "child", script = """
        return function NativeFormStatus(props) {
            var action = React.useMemo(function() {
                return function(data) { return props.submit(); };
            }, [props.submit]);
            return React.createElement('form', {action: action},
                React.createElement('input', {name: 'payload', defaultValue: 'native'}),
                React.createElement(child));
        };
        """, imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native JSObject component(JSObject child);
}
