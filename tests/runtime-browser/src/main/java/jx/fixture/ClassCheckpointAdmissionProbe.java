package jx.fixture;

import java.util.List;
import java.util.Set;
import jx.ClassComponent;
import jx.ClassComponentType;
import jx.ClassSchema;
import jx.PureComponent;
import jx.StateCodec;
import jx.View;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Compiling consumer of descriptor admission, observed while the existing root stays live. */
final class ClassCheckpointAdmissionProbe {
    private ClassCheckpointAdmissionProbe() {}
    record Model(int count) {}
    private static final ClassSchema.Field<Model,Integer> COUNT =
            ClassSchema.field("count", ClassSchema.INTEGER, Model::count);
    private static final ClassSchema<Model> MODEL =
            new ClassSchema<>(List.of(COUNT), reader -> new Model(reader.get(COUNT)));
    private static final StateCodec<Model> CODEC = new StateCodec<>() {
        public String schema() { return "admission:model:1"; }
        public String encode(Model value) { return value == null ? "null" : Integer.toString(value.count()); }
        public Model decode(String payload) { return payload.equals("null") ? null : new Model(Integer.parseInt(payload)); }
    };
    private static final class NullablePeer extends ClassComponent<Model,Model,Void> {
        NullablePeer(Model props) { super(props, null); }
        @Override public View render() { return null; }
    }
    private static final class PurePeer extends PureComponent<Model,Model,Void> {
        PurePeer(Model props) { super(props, props); }
        @Override public View render() { return null; }
    }
    static void install() { install(ClassCheckpointAdmissionProbe::attempt); }
    private static String attempt(String operation) {
        var ordinary = ClassComponentType.normal("Admission", MODEL, MODEL, NullablePeer::new, Set.of());
        try {
            switch (operation) {
                case "strict-null" -> ordinary.withStateCheckpoint("admission", CODEC).validateState(null);
                case "invalid-metadata" -> ordinary.withStateCheckpoint("", CODEC);
                case "pure-state" -> ClassComponentType.pure("PureAdmission", MODEL, MODEL,
                        PurePeer::new, Set.of()).withNullableState().withStateCheckpoint("pure-admission", CODEC);
                default -> throw new IllegalArgumentException("Unknown admission operation");
            }
            return "accepted";
        } catch (IllegalArgumentException | NullPointerException failure) {
            return failure.getMessage();
        }
    }
    @JSFunctor interface Attempt extends JSObject { String attempt(String operation); }
    @JSBody(params = "attempt", script = "globalThis.hotClassAdmission = attempt;")
    private static native void install(Attempt attempt);
}
