package jx.example.consumer;

import jx.react.React;

public final class Main {
    private Main() {}
    public static void main(String[] arguments) { React.mountStrict("app", App.JX, null); }
}
