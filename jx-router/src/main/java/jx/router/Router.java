package jx.router;

import jx.ExternalStore;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.teavm.jso.*;

/** Browser-thread history store. Observe via Hooks.useSyncExternalStore. Listening is reference-counted:
 * the last unsubscribe detaches browser handlers; close permanently releases all listeners. */
public final class Router implements ExternalStore<Location>, AutoCloseable {
    private Location current = read();
    private final Set<Runnable> listeners = new LinkedHashSet<>();
    private JSObject cleanup;
    private boolean closed;
    @Override public Location snapshot() {
        Location latest = read();
        if (!current.equals(latest)) current = latest;
        return current;
    }
    @Override public Runnable subscribe(Runnable listener) {
        if (closed) throw new IllegalStateException("Router is closed");
        Objects.requireNonNull(listener);
        if (listeners.size() >= 1024) throw new IllegalStateException("Router listener capacity exceeded");
        Runnable registration = listener::run;
        listeners.add(registration);
        if (cleanup == null) cleanup = listen(this::changed);
        return () -> { listeners.remove(registration); if (listeners.isEmpty() && cleanup != null) { unlisten(cleanup); cleanup = null; } };
    }
    public void navigate(String path) { navigate(path, false); }
    public void replace(String path) { navigate(path, true); }
    private void navigate(String path, boolean replace) {
        if (closed) throw new IllegalStateException("Router is closed");
        if (!Objects.requireNonNull(path).startsWith("/") || path.startsWith("//") || path.contains("\\") || path.length() > 8192) throw new IllegalArgumentException("Route must be a bounded same-origin absolute path");
        push(path, replace); changed();
    }
    private void changed() {
        Location previous = current;
        if (snapshot() == previous) return;
        for (Runnable listener : List.copyOf(listeners)) if (listeners.contains(listener)) listener.run();
    }
    @Override public void close() { if (cleanup != null) unlisten(cleanup); cleanup = null; listeners.clear(); closed = true; }
    private static Location read() { return new Location(path(), query(), hash()); }
    @JSFunctor interface Changed extends JSObject { void changed(); }
    @JSBody(script = "return location.pathname;") private static native String path();
    @JSBody(script = "return location.search.substring(1);") private static native String query();
    @JSBody(script = "return location.hash.substring(1);") private static native String hash();
    @JSBody(params = {"path", "replace"}, script = "if (replace) history.replaceState(null, '', path); else history.pushState(null, '', path);") private static native void push(String path, boolean replace);
    @JSBody(params = "changed", script = "window.addEventListener('popstate', changed); window.addEventListener('hashchange', changed); return changed;") private static native JSObject listen(Changed changed);
    @JSBody(params = "changed", script = "window.removeEventListener('popstate', changed); window.removeEventListener('hashchange', changed);") private static native void unlisten(JSObject changed);
}
