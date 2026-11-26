package jx.fixture.ssr;

import org.teavm.jso.JSBody;

/** Test observation after a real React commit; never consulted by application rendering. */
public final class Hydration {
    private Hydration() {}
    @JSBody(script = "document.documentElement.setAttribute('data-jx-hydrated', 'true');")
    public static native void committed();
}
