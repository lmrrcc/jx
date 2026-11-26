package jx.fixture;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import jx.*;
import jx.react.React;
import org.teavm.jso.JSBody;

/** Explicit application codecs; no serializer, state queue or renderer is substituted by this fixture. */
public final class HotClassPresenceApplication {
    private HotClassPresenceApplication() {}
    public record Props(boolean skip, String derived) {}
    public record Model(Presence<String> note) {}
    public record Empty() {}
    private static final int GENERATION = nextGeneration();
    private static final String DECODER = decoderMode();
    private static final ClassSchema.Field<Props,Boolean> SKIP = ClassSchema.field("skip", ClassSchema.BOOLEAN, Props::skip);
    private static final ClassSchema.Field<Props,String> DERIVED = ClassSchema.field("derived", ClassSchema.STRING, Props::derived);
    private static final ClassSchema<Props> PROPS = new ClassSchema<>(List.of(SKIP, DERIVED),
            reader -> new Props(reader.get(SKIP), reader.get(DERIVED)));
    private static final ClassSchema.Field<Model,String> NOTE =
            ClassSchema.optionalField("note", ClassSchema.STRING.nullable(), Model::note);
    private static final ClassSchema<Model> MODEL = new ClassSchema<>(List.of(NOTE), reader -> new Model(reader.find(NOTE)));
    private static final ClassSchema<Empty> EMPTY = new ClassSchema<>(List.of(), reader -> new Empty());
    private static final StateCodec<Model> CODEC = new StateCodec<>() {
        public String schema() { return "fixture:class-presence:1"; }
        public String encode(Model value) {
            if (value == null) return "null";
            return switch (value.note()) {
                case Presence.Absent<String> ignored -> "absent";
                case Presence.Undefined<String> ignored -> "undefined";
                case Presence.Present<String> present -> present.value() == null ? "present-null" : "value:" + present.value();
            };
        }
        public Model decode(String payload) {
            if (DECODER.equals("throw")) throw new IllegalArgumentException("Candidate presence decoder rejected payload");
            if (DECODER.equals("invalid")) return new Model(null);
            return switch (payload) {
                case "null" -> null;
                case "absent" -> new Model(Presence.absent());
                case "undefined" -> new Model(Presence.undefined());
                case "present-null" -> new Model(Presence.present(null));
                default -> {
                    if (!payload.startsWith("value:") || payload.length() > 128)
                        throw new IllegalArgumentException("Malformed presence checkpoint");
                    yield new Model(Presence.present(payload.substring(6)));
                }
            };
        }
    };
    private static final StateCodec<Empty> EMPTY_CODEC = new StateCodec<>() {
        public String schema() { return "fixture:empty-record:1"; }
        public String encode(Empty value) { Objects.requireNonNull(value); return "empty-record"; }
        public Empty decode(String payload) {
            if (!payload.equals("empty-record")) throw new IllegalArgumentException("Malformed empty record");
            return new Empty();
        }
    };
    public static final class Peer extends ClassComponent<Props,Model,Void> {
        Peer(Props props) { super(props, null); }
        void update(String operation) {
            switch (operation) {
                case "empty" -> setState(MODEL.patch(), () -> event("callback", describe(state())));
                case "undefined" -> setState(MODEL.patch().undefined(NOTE), () -> event("callback", describe(state())));
                case "null" -> setState(MODEL.patch().set(NOTE, null), () -> event("callback", describe(state())));
                case "value" -> setState(MODEL.patch().set(NOTE, "edited:π"), () -> event("callback", describe(state())));
                default -> throw new IllegalArgumentException("Unknown presence update");
            }
        }
        @Override protected boolean shouldComponentUpdate(Props next, Model pending) {
            event("should", describe(pending)); return !next.skip();
        }
        @Override protected void componentDidMount() { event("mount", describe(state())); }
        @Override protected void componentDidUpdate(Props previous, Model state, Void snapshot) {
            event("commit", describe(state()));
        }
        @Override public View render() {
            event("render", describe(state()));
            return Views.element("output", Attributes.create().attr("id", "presence-value"), describe(state()));
        }
    }
    public static final class EmptyPeer extends ClassComponent<Props,Empty,Void> {
        EmptyPeer(Props props) { super(props, new Empty()); }
        @Override public View render() {
            return Views.element("output", Attributes.create().attr("id", "empty-record"), state() == null ? "null" : "empty-record");
        }
    }
    private static final ClassRef<Peer> REF = new ClassRef<>(value -> {
        if (value instanceof Peer peer) return peer;
        throw new IllegalArgumentException("Expected current-generation presence peer");
    });
    private static ClassComponentType<Props,Model,Peer> type;
    private static final ClassComponentType<Props,Empty,EmptyPeer> EMPTY_TYPE = ClassComponentType.normal(
            "hot.EmptyRecord", PROPS, EMPTY, EmptyPeer::new, Set.of()).withStateCheckpoint("empty-record:1", EMPTY_CODEC);
    private static final ComponentType<Void> APP = new ComponentType<>("hot.ClassPresence", "presence:state,state:1", ignored -> {
        State<String> derived = Hooks.useState("");
        State<String> inspected = Hooks.useState("uninspected");
        return Views.element("main", Attributes.create(),
                Views.element("output", Attributes.create().attr("id", "presence-status"), "ready"),
                Views.element("output", Attributes.create().attr("id", "presence-generation"), GENERATION),
                button("presence-empty", "Empty patch", () -> REF.current().update("empty")),
                button("presence-undefined", "Undefined field", () -> REF.current().update("undefined")),
                button("presence-null", "Null field", () -> REF.current().update("null")),
                button("presence-edit", "Edit field", () -> REF.current().update("value")),
                button("presence-derived", "Derive field", () -> derived.set("derived")),
                button("presence-inspect", "Inspect current state", () -> inspected.set(describe(REF.current().state()))),
                Views.element("output", Attributes.create().attr("id", "presence-inspected"), inspected.value()),
                Views.component(type, new Props(skip(), derived.value()), "presence", REF),
                Views.component(EMPTY_TYPE, new Props(false, ""), "empty", null));
    });
    private static final ComponentType<String> ADMISSION_ERROR = new ComponentType<>("hot.PresenceAdmission", message ->
            Views.element("output", Attributes.create().attr("id", "presence-status"), "rejected: " + message));
    public static void main() {
        try {
            type = ClassComponentType.normal("hot.ClassPresencePeer", PROPS, MODEL, Peer::new,
                            Set.of(ClassComponentType.Lifecycle.MOUNT, ClassComponentType.Lifecycle.UPDATE,
                                    ClassComponentType.Lifecycle.SHOULD_UPDATE))
                    .withNullableState()
                    .withDerivedStateFromProps((props, state) -> props.derived().isEmpty() ? null : MODEL.patch().set(NOTE, props.derived()))
                    .withStateCheckpoint("class-presence:1", CODEC);
        } catch (IllegalArgumentException failure) {
            React.mountStrict("root", ADMISSION_ERROR, failure.getMessage());
            return;
        }
        React.mountStrict("root", APP, null);
    }
    private static String describe(Model value) { return CODEC.encode(value); }
    private static View button(String id, String label, Runnable action) {
        return Views.element("button", Attributes.create().attr("id", id).on("onClick", event -> action.run()), label);
    }
    @JSBody(script = "return globalThis.presenceDecoderMode || 'valid';") private static native String decoderMode();
    @JSBody(script = "return new URLSearchParams(location.search).get('skip') === 'true';") private static native boolean skip();
    @JSBody(script = "globalThis.presenceGeneration = (globalThis.presenceGeneration || 0) + 1; return globalThis.presenceGeneration;")
    private static native int nextGeneration();
    @JSBody(params = {"kind", "value"}, script = """
        if (!globalThis.presenceEvents) globalThis.presenceEvents = [];
        globalThis.presenceEvents.push({kind: kind, value: value});
        """) private static native void event(String kind, String value);
}
