package jx.fixture.lazy;

import jx.Context;
import jx.Hooks;

/** A context both applications declare from the same source. */
public final class Themes {
    private Themes() {}

    /** "light" when no provider of the same application is present. */
    public static final Context<String> THEME = Hooks.createContext("light");
}
