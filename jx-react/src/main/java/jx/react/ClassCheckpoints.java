package jx.react;

import jx.ClassComponentType;
import jx.ClassSchema;
import java.util.function.Supplier;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBodyImport;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Conservative development journal. Candidates can reject capture; only native own commits
 * publish strings. Neither candidates nor native/Java instance identities cross generations. */
final class ClassCheckpoints<S> {
    private static final JSObject OWNER = ownerType();
    @JSFunctor interface Encoder extends JSObject { String encode(); }
    private final JSObject journal;
    ClassCheckpoints(JSObject instance, ClassComponentType.Checkpoint<S> checkpoint, Supplier<S> state) {
        journal = journal(instance, checkpoint.signature(), checkpoint.codec().schema(),
                () -> checkpoint.codec().encode(state.get()));
    }
    void candidate(JSObject props, JSObject state) { candidate(journal, props, state); }
    void pending() { pending(journal); }
    void completed() { completed(journal); }
    void commit() { commit(journal); }
    void mounted() { connected(journal, true); if (firstMount(journal)) commit(); }
    void disconnected() { connected(journal, false); }

    static <S> S restore(JSObject props, ClassComponentType.Checkpoint<S> checkpoint, ClassSchema<S> schema, S initial) {
        String encoded = payload(props, checkpoint.signature(), checkpoint.codec().schema());
        if (encoded == null) return initial;
        S restored = checkpoint.codec().decode(encoded);
        schema.validate(restored);
        return restored;
    }

    static JSObject element(JSObject constructor, JSObject props, String key, String path, JSObject ref, JSObject overrides) {
        return element(OWNER, constructor, props, key, path, ref, overrides);
    }
    @JSBody(script = """
        return function ClassCheckpointOwner(input) {
            var hot = globalThis.__JX_HOT__;
            if (!hot.classOwners || !hot.classRootContext) throw new Error('Class checkpoint protocol requires a fresh development page');
            var root = React.useContext(hot.classRootContext);
            var saved = React.useRef(null);
            if (saved.current === null) saved.current = {path: input.path, root: root, journal: null};
            var owner = saved.current;
            React.useInsertionEffect(function() {
                if (owner.path !== input.path) throw new Error('Class checkpoint owner path changed');
                if (owner.root !== root) throw new Error('Class checkpoint root ownership changed');
                if (hot.classOwners.size >= 4096 && !hot.classOwners.has(owner.path))
                    throw new Error('Class checkpoint owner capacity exceeded');
                var existing = hot.classOwners.get(owner.path);
                if (existing && existing !== owner) throw new Error('Duplicate class checkpoint owner');
                hot.classOwners.set(owner.path, owner);
                return function() {
                    if (hot.classOwners.get(owner.path) === owner) {
                        hot.classOwners.delete(owner.path);
                        hot.incomingClasses.delete(owner.path);
                    }
                    owner.journal = null;
                };
            }, [owner, input.path, root]);
            var childProps = Object.assign({}, input.values, {__jxPath: input.path, __jxCheckpointOwner: owner});
            if (input.forwardedRef !== null) childProps.ref = input.forwardedRef;
            var element = React.createElement(input.type, childProps);
            return input.overrides === null ? element : React.cloneElement(element, input.overrides);
        };
        """, imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native JSObject ownerType();
    @JSBody(params = {"owner", "constructor", "props", "key", "path", "ref", "overrides"}, script = """
        return React.createElement(owner, {type: constructor, values: props,
            key: key === null ? undefined : key, path: path, forwardedRef: ref, overrides: overrides});
        """, imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native JSObject element(JSObject owner, JSObject constructor, JSObject props, String key, String path, JSObject ref, JSObject overrides);

    @JSBody(params = {"props", "signature", "schema"}, script = """
        var hot = globalThis.__JX_HOT__, saved = hot.incomingClasses.get(props.__jxPath);
        if (!saved) return null;
        if (hot.classRestoreClosed) throw new Error('Class checkpoint restore unqualified: owner did not commit in the initial tree');
        if (saved.signature !== signature || saved.schema !== schema) {
            hot.notice('Class state reset: checkpoint signature/schema changed for ' + props.__jxPath);
            return null;
        }
        return saved.payload;
        """)
    private static native String payload(JSObject props, String signature, String schema);

    @JSBody(params = {"instance", "signature", "schema", "encode"}, script = """
        return {instance: instance, owner: instance.props.__jxCheckpointOwner,
            signature: signature, schema: schema, encode: encode,
            pending: 0, candidates: [], payload: null, failure: null, connected: false};
        """) private static native JSObject journal(JSObject instance, String signature, String schema, Encoder encode);

    @JSBody(params = {"journal", "props", "state"}, script = """
        if (journal.failure) return;
        if (journal.candidates.some(function(value) { return value.props === props && value.state === state; })) return;
        if (journal.candidates.length >= 16) {
            journal.failure = 'Unmatched class render candidate capacity exceeded';
            journal.candidates = [];
            return;
        }
        journal.candidates.push({props: props, state: state});
        """) private static native void candidate(JSObject journal, JSObject props, JSObject state);
    @JSBody(params = "journal", script = "if (++journal.pending > 256) journal.failure = 'Pending class update capacity exceeded';")
    private static native void pending(JSObject journal);
    @JSBody(params = "journal", script = "if (journal.pending > 0) journal.pending--;")
    private static native void completed(JSObject journal);
    @JSBody(params = "journal", script = "return journal.owner.journal !== journal;")
    private static native boolean firstMount(JSObject journal);
    @JSBody(params = {"journal", "connected"}, script = "journal.connected = connected;")
    private static native void connected(JSObject journal, boolean connected);
    @JSBody(params = "journal", script = """
        var hot = globalThis.__JX_HOT__, instance = journal.instance;
        // This function is called only by this native class's actual commit lifecycle/callback.
        if (hot.classOwners.get(journal.owner.path) !== journal.owner) return;
        journal.owner.journal = journal;
        journal.candidates = journal.candidates.filter(function(value) {
            return value.props !== instance.props || value.state !== instance.state;
        });
        if (journal.failure) return;
        try {
            var value = journal.encode();
            if (typeof value !== 'string' || value.length > 1048576) throw new Error('Invalid class checkpoint payload');
            journal.payload = value;
            hot.incomingClasses.delete(journal.owner.path);
        } catch (error) { journal.failure = 'Class checkpoint encoding failed'; }
        """) private static native void commit(JSObject journal);
}
