package jx.react;

import jx.StateCodec;
import jx.StateTransfer;
import java.util.Objects;
import java.util.function.Supplier;
import org.teavm.jso.*;

/** Development-only portable checkpoints across independent TeaVM runtimes. Enable through the dev
 * loader before main. Effects/refs/memos restart; only compatible scalar or explicitly coded state
 * transfers. No Java object, callback, DOM handle, or pending command crosses runtime generations. */
public final class HotReload {
    private static final boolean ENABLED = setup();
    private HotReload() {}
    @JSFunctor interface Encoder extends JSObject { String encode(); }
    @JSFunctor interface Stop extends JSObject { void stop(); }

    /** Track an application-owned store before mounting. A compatible checkpoint restores the fresh
     * owner before it starts. Cleanup unregisters this registration; replacement also stops the owner.
     * Use a stable id and change signature when the model/workflow contract becomes incompatible. */
    public static <T> Runnable trackStore(String id, String signature, StateTransfer<T> store, StateCodec<T> codec) {
        Objects.requireNonNull(store); validate(id); validate(signature); validate(codec.schema());
        if (!ENABLED) return () -> {};
        String payload = storePayload(id, signature, codec.schema());
        if (payload != null) {
            try { store.restore(codec.decode(payload)); }
            catch (RuntimeException invalid) { notice("Store checkpoint rejected: " + id); }
        }
        JSObject token = trackStoreNative(id, signature, codec.schema(), () -> codec.encode(store.snapshot()), store::stop);
        return () -> untrackStore(id, token);
    }
    private static void validate(String value) {
        if (Objects.requireNonNull(value).isBlank() || value.length() > 1024) throw new IllegalArgumentException("Invalid hot-state identifier/schema");
    }
    static boolean enabled() { return ENABLED; }
    static void classStateReset(String path) { if (ENABLED) classStateResetNative(path); }
    static JSObject begin(String path, String signature, boolean root) { return ENABLED ? beginNative(path, signature, root) : null; }
    static void end(JSObject frame) { if (frame != null) endNative(frame); }
    static void commit(JSObject frame) { if (frame != null) commitNative(frame); }
    static JSObject slot(String kind) { return ENABLED ? slotNative(kind) : null; }
    static String viewPath() { return ENABLED ? viewPathNative() : "v0"; }
    static String childPath(String name, String key, String position, String container) { return ENABLED ? childPathNative(name, key, position, container) : ""; }
    static String keyedPosition(String container, String key) { return ENABLED ? keyedPositionNative(container, key) : ""; }
    static String rootPath(String root, String name) { return ENABLED ? rootPathNative(root, name) : ""; }
    static void root(String id, React.Root root) { if (ENABLED) rootNative(id, root::close); }
    static void forgetRoot(String id) { if (ENABLED) forgetRootNative(id); }
    static <T> T restore(JSObject slot, StateCodec<T> codec, T initial) {
        if (slot == null || codec == null) return initial;
        validate(codec.schema());
        String payload = slotPayload(slot, codec.schema());
        if (payload == null) return initial;
        try { return codec.decode(payload); }
        catch (RuntimeException invalid) { notice("Component checkpoint rejected for schema " + codec.schema()); return initial; }
    }
    static <T> void record(JSObject slot, StateCodec<T> codec, Supplier<T> value) {
        if (slot == null) return;
        if (codec == null) { recordNative(slot, "", null); return; }
        validate(codec.schema());
        recordNative(slot, codec.schema(), () -> codec.encode(value.get()));
    }

    @JSBody(script = """
        var hot = globalThis.__JX_HOT__;
        if (!hot || !hot.enabled) return false;
        if (hot.version && hot.version !== 2) throw new Error('Incompatible JX hot runtime; load a fresh development page');
        if (hot.version) {
            if (!(hot.classOwners instanceof Map) || !(hot.incomingClasses instanceof Map))
                throw new Error('Incomplete JX hot runtime; load a fresh development page');
            return true;
        }
        hot.version = 2;
        hot.components = new Map(); hot.stores = new Map(); hot.roots = new Map();
        hot.classOwners = new Map(); hot.incomingClasses = new Map(); hot.classRestoreClosed = false;
        hot.incomingComponents = new Map(); hot.incomingStores = new Map(); hot.current = null;
        hot.notices = [];
        hot.notice = function(message) { hot.notices.push(message); if (hot.notices.length > 100) hot.notices.shift(); };
        hot.resetReady = function() { hot.failure = null; hot.readyPromise = new Promise(function(resolve, reject) { hot.resolveReady = resolve; hot.rejectReady = reject; }); };
        hot.fail = function(error) { hot.failure = String(error); hot.rejectReady(new Error(hot.failure)); };
        hot.resetReady(); hot.ready = function() { return hot.readyPromise; };
        function encoded(encode) {
            var value = encode();
            if (typeof value !== 'string' || value.length > 1048576) throw new Error('Invalid or oversized state checkpoint');
            return value;
        }
        hot.capture = function() {
            var checkpoint = {version: 1, components: [], stores: [], classes: []};
            if (hot.classOwners.size && hot.roots.size !== 1) throw new Error('Class checkpoint capture unqualified: this slice requires one owned root');
            if (hot.incomingClasses.size) throw new Error('Class checkpoint capture unqualified: incoming class owners have not committed');
            if (hot.classOwners.size + hot.components.size > 4096) throw new Error('JX checkpoint component capacity exceeded');
            hot.classOwners.forEach(function(owner) {
                if (!owner.path || owner.path.length > 8192) throw new Error('Invalid class checkpoint owner path');
                var journal = owner.journal;
                if (!journal || journal.failure || journal.pending || journal.candidates.length || journal.payload === null || !journal.connected)
                    throw new Error('Class checkpoint capture unqualified for ' + owner.path + ': '
                        + (!journal ? 'no native class commit' : journal.failure || (journal.pending ? 'pending native updates'
                            : journal.candidates.length ? 'unmatched render candidates' : 'class owner is disconnected')));
                checkpoint.classes.push({path: owner.path, signature: journal.signature,
                    schema: journal.schema, payload: journal.payload});
            });
            hot.components.forEach(function(frame) {
                if (!frame.signature) { hot.notice('State reset: missing component signature for ' + frame.path); return; }
                try {
                    var hooks = frame.hooks.map(function(slot) {
                        if (!slot.encode) {
                            if (slot.kind === 'state' || slot.kind === 'reducer') hot.notice('State reset: unsupported ' + slot.kind + ' in ' + frame.path);
                            return {kind: slot.kind, schema: '', payload: null};
                        }
                        return {kind: slot.kind, schema: slot.schema, payload: encoded(slot.encode)};
                    });
                    checkpoint.components.push({path: frame.path, signature: frame.signature, hooks: hooks});
                } catch (error) { hot.notice('State reset: unsupported checkpoint in ' + frame.path); }
            });
            hot.stores.forEach(function(store, id) {
                try { checkpoint.stores.push({id: id, signature: store.signature, schema: store.schema, payload: encoded(store.encode)}); }
                catch (error) { hot.notice('Store reset: unsupported checkpoint for ' + id); }
            });
            if (new TextEncoder().encode(JSON.stringify(checkpoint)).length > 8388608) throw new Error('JX checkpoint exceeds eight MiB');
            return checkpoint;
        };
        hot.dispose = function() {
            Array.from(hot.roots.values()).forEach(function(stop) { stop(); });
            Array.from(hot.stores.values()).forEach(function(store) { store.stop(); });
            hot.components.clear(); hot.stores.clear(); hot.roots.clear(); hot.current = null;
            hot.classOwners.clear();
            hot.resetReady();
        };
        hot.restore = function(checkpoint) {
            var text = JSON.stringify(checkpoint);
            if (new TextEncoder().encode(text).length > 8388608) throw new Error('JX checkpoint exceeds eight MiB');
            var copy = JSON.parse(text);
            if (copy.version !== 1 || !Array.isArray(copy.components) || !Array.isArray(copy.stores) || copy.components.length > 4096 || copy.stores.length > 128) throw new Error('Invalid JX checkpoint');
            var classes = copy.classes === undefined ? [] : copy.classes;
            if (!Array.isArray(classes) || classes.length + copy.components.length > 4096) throw new Error('Invalid JX class checkpoint count');
            var paths = new Set();
            classes.forEach(function(value) {
                if (!value || typeof value.path !== 'string' || !value.path || value.path.length > 8192
                    || typeof value.signature !== 'string' || !value.signature || value.signature.length > 256
                    || typeof value.schema !== 'string' || !value.schema || value.schema.length > 256
                    || typeof value.payload !== 'string' || value.payload.length > 1048576 || paths.has(value.path))
                    throw new Error('Invalid JX class checkpoint');
                paths.add(value.path);
            });
            hot.incomingComponents.clear(); hot.incomingStores.clear();
            hot.incomingClasses.clear();
            hot.classRestoreClosed = false;
            copy.components.forEach(function(frame) { hot.incomingComponents.set(frame.path, frame); });
            copy.stores.forEach(function(store) { hot.incomingStores.set(store.id, store); });
            classes.forEach(function(value) { hot.incomingClasses.set(value.path, value); });
            hot.resetReady();
        };
        return true;
        """) private static native boolean setup();
    @JSBody(params = {"path", "signature", "root"}, script = """
        var hot = globalThis.__JX_HOT__;
        var previous = hot.current;
        var saved = hot.incomingComponents.get(path);
        if (saved && (!signature || saved.signature !== signature)) { hot.notice('State reset: component signature changed for ' + path); saved = null; }
        var frame = {path: path, signature: signature, root: root, previous: previous, hooks: [], views: 0, saved: saved};
        hot.current = frame; return frame;
        """) private static native JSObject beginNative(String path, String signature, boolean root);
    @JSBody(params = "frame", script = "globalThis.__JX_HOT__.current = frame.previous;") private static native void endNative(JSObject frame);
    @JSBody(params = "frame", script = """
        React.useLayoutEffect(function() {
            var hot = globalThis.__JX_HOT__;
            if (hot.components.size >= 4096 && !hot.components.has(frame.path)) throw new Error('JX hot component capacity exceeded');
            hot.components.set(frame.path, frame); hot.incomingComponents.delete(frame.path);
            if (frame.root) {
                hot.classRestoreClosed = true;
                Array.from(hot.incomingComponents.keys()).forEach(function(path) { if (path.indexOf(frame.path + '/') === 0) hot.incomingComponents.delete(path); });
                var ready = hot.resolveReady;
                if (hot.incomingClasses.size) hot.fail(new Error('Class checkpoint restore unqualified: incoming owners did not commit in the initial tree'));
                else requestAnimationFrame(function() { requestAnimationFrame(function() { ready(); }); });
            }
            return function() { if (hot.components.get(frame.path) === frame) hot.components.delete(frame.path); };
        });
        """, imports = @JSBodyImport(alias = "React", fromModule = "react")) private static native void commitNative(JSObject frame);
    @JSBody(params = "kind", script = """
        var frame = globalThis.__JX_HOT__.current;
        if (!frame) return null;
        if (frame.hooks.length >= 256) throw new Error('JX hot hook capacity exceeded');
        var saved = frame.saved && frame.saved.hooks[frame.hooks.length];
        var slot = {kind: kind, schema: '', encode: null, saved: saved && saved.kind === kind ? saved : null};
        frame.hooks.push(slot); return slot;
        """) private static native JSObject slotNative(String kind);
    @JSBody(params = {"slot", "schema"}, script = """
        var saved = slot.saved;
        if (!saved) return null;
        if (saved.schema !== schema || typeof saved.payload !== 'string') {
            globalThis.__JX_HOT__.notice('State reset: hook schema changed or unsupported'); return null;
        }
        return saved.payload;
        """) private static native String slotPayload(JSObject slot, String schema);
    @JSBody(params = {"slot", "schema", "encode"}, script = "slot.schema = schema; slot.encode = encode;") private static native void recordNative(JSObject slot, String schema, Encoder encode);
    @JSBody(script = "var frame = globalThis.__JX_HOT__.current; return 'v' + (frame ? frame.views++ : 0);") private static native String viewPathNative();
    @JSBody(params = {"name", "key", "position", "container"}, script = "var frame = globalThis.__JX_HOT__.current; return (frame ? frame.path : 'external') + '/' + (key === null ? position : container + '/key:' + encodeURIComponent(key)) + '/component:' + encodeURIComponent(name);") private static native String childPathNative(String name, String key, String position, String container);
    @JSBody(params = {"container", "key"}, script = "return container + '/key:' + encodeURIComponent(key);") private static native String keyedPositionNative(String container, String key);
    @JSBody(params = {"root", "name"}, script = "return 'root:' + encodeURIComponent(root) + '/component:' + encodeURIComponent(name);") private static native String rootPathNative(String root, String name);
    @JSBody(params = {"id", "stop"}, script = "var hot = globalThis.__JX_HOT__; if (hot.roots.has(id)) throw new Error('Duplicate JX hot root: ' + id); hot.roots.set(id, stop);") private static native void rootNative(String id, Stop stop);
    @JSBody(params = "id", script = "globalThis.__JX_HOT__.roots.delete(id);") private static native void forgetRootNative(String id);
    @JSBody(params = {"id", "signature", "schema"}, script = "var hot = globalThis.__JX_HOT__; var value = hot.incomingStores.get(id); hot.incomingStores.delete(id); if (!value) return null; if (value.signature !== signature || value.schema !== schema || typeof value.payload !== 'string') { hot.notice('Store reset: schema/signature changed for ' + id); return null; } return value.payload;") private static native String storePayload(String id, String signature, String schema);
    @JSBody(params = {"id", "signature", "schema", "encode", "stop"}, script = "var hot = globalThis.__JX_HOT__; if (hot.stores.size >= 128 || hot.stores.has(id)) throw new Error('Duplicate or excessive JX hot stores'); var token = {signature: signature, schema: schema, encode: encode, stop: stop}; hot.stores.set(id, token); return token;") private static native JSObject trackStoreNative(String id, String signature, String schema, Encoder encode, Stop stop);
    @JSBody(params = {"id", "token"}, script = "var stores = globalThis.__JX_HOT__.stores; if (stores.get(id) === token) stores.delete(id);") private static native void untrackStore(String id, JSObject token);
    @JSBody(params = "path", script = "var hot = globalThis.__JX_HOT__; var message = 'Class state resets on hot replacement: ' + path; if (hot.notices.indexOf(message) < 0) hot.notice(message);") private static native void classStateResetNative(String path);
    @JSBody(params = "message", script = "globalThis.__JX_HOT__.notice(message);") private static native void notice(String message);
}
