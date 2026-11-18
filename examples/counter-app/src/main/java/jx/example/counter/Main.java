package jx.example.counter;

import jx.react.React;

/** The application's Java-only browser entry point, invoked by the generated framework bootstrap. */
public final class Main {
    private Main() {}
    public static void main(String[] arguments) { React.mountStrict("app", App.JX, null); }
}
