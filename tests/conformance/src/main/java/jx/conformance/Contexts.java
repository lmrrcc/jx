package jx.conformance;

import jx.Context;
import jx.Hooks;

/** Contexts shared by bench cases. */
public final class Contexts {
    private Contexts() {}

    /** Theme name; "light" when no provider is present. */
    public static final Context<String> THEME = Hooks.createContext("light");
}
