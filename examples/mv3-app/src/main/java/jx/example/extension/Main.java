package jx.example.extension;

import jx.react.React;

/** Invoked by the framework bootstrap after the packaged WASM binding is ready. */
public final class Main {
    private Main() {}
    public static void main(String[] arguments) { React.mountStrict("app", App.JX, null); }
}
