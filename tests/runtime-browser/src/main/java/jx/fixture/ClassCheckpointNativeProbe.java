package jx.fixture;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSBodyImport;
import org.teavm.jso.JSObject;

/** Test-owned native React control for hidden class callback behavior. */
final class ClassCheckpointNativeProbe {
    private ClassCheckpointNativeProbe() {}
    static void install() { install(react(), rootFactory()); }
    @JSBody(script = "return Runtime;", imports = @JSBodyImport(alias = "Runtime", fromModule = "react"))
    private static native JSObject react();
    @JSBody(script = "return Client.createRoot;", imports = @JSBodyImport(alias = "Client", fromModule = "react-dom/client"))
    private static native JSObject rootFactory();
    @JSBody(params = {"React", "createRoot"}, script = """
        globalThis.hotClassNativeProbe = async function() {
            var previous = globalThis.IS_REACT_ACT_ENVIRONMENT;
            globalThis.IS_REACT_ACT_ENVIRONMENT = true;
            var container = document.createElement('div'); document.body.append(container);
            var root = createRoot(container), peer, setVisible, log = [];
            class Counter extends React.Component {
                constructor(props) { super(props); this.state = {count: 0}; }
                shouldComponentUpdate() { return false; }
                componentDidMount() { log.push({kind: 'mount', value: this.state.count}); }
                render() { return React.createElement('output', null, this.state.count); }
            }
            function App() {
                var pair = React.useState(true); setVisible = pair[1];
                return React.createElement(React.Activity, {mode: pair[0] ? 'visible' : 'hidden'},
                    React.createElement(Counter, {ref: value => { if (value) peer = value; }}));
            }
            try {
                await React.act(async function() { root.render(React.createElement(React.StrictMode, null, React.createElement(App))); });
                await React.act(async function() { setVisible(false); });
                await React.act(async function() { peer.setState({count: 1}, function() { log.push({kind: 'callback', value: this.state.count}); }); });
                var hidden = log.slice();
                await React.act(async function() { setVisible(true); });
                return {hidden: hidden, shown: log.slice()};
            } finally {
                await React.act(async function() { root.unmount(); });
                container.remove(); globalThis.IS_REACT_ACT_ENVIRONMENT = previous;
            }
        };
        """)
    private static native void install(JSObject react, JSObject createRoot);
}
