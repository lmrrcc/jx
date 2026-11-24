package jx.fixture.lazy.host;

import jx.react.React;

/** The application the page starts. It loads the other one on demand. */
public final class Host {
    private Host() {}

    public static void main(String[] arguments) {
        React.mountStrict("app", HostPage.JX, null);
    }
}
