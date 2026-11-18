package jx.elm;

import jx.*;

/** React hook integration. Program descriptors must be stable for the component's mounted lifetime. */
public final class Programs {
    private Programs() {}
    public static <S, M> View useProgram(Program<S, M> program) {
        Store<S, M> store = Hooks.useMemo(() -> new Store<>(program), Dependencies.of(program));
        S model = Hooks.useSyncExternalStore(store);
        Hooks.useEffect(() -> { store.start(); return store::stop; }, Dependencies.of(store));
        return program.view(model, store::dispatch);
    }
}
