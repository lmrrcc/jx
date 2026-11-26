package jx;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassCheckpointPresenceTest {
    private record Props() {}
    private record Model(Presence<String> note) {}
    private static final ClassSchema<Props> PROPS = new ClassSchema<>(List.of(), reader -> new Props());
    private static final ClassSchema.Field<Model,String> NOTE =
            ClassSchema.optionalField("note", ClassSchema.STRING.nullable(), Model::note);
    private static final ClassSchema<Model> MODEL =
            new ClassSchema<>(List.of(NOTE), reader -> new Model(reader.find(NOTE)));
    private static final StateCodec<Model> CODEC = new StateCodec<>() {
        public String schema() { return "test:presence:1"; }
        public String encode(Model value) {
            if (value == null) return "null";
            String payload = switch (value.note()) {
                case Presence.Absent<String> ignored -> "absent";
                case Presence.Undefined<String> ignored -> "undefined";
                case Presence.Present<String> present -> present.value() == null ? "present-null" : "value:" + present.value();
            };
            if (payload.length() > 128) throw new IllegalArgumentException("Presence payload exceeds test bound");
            return payload;
        }
        public Model decode(String payload) {
            return switch (payload) {
                case "null" -> null;
                case "absent" -> new Model(Presence.absent());
                case "undefined" -> new Model(Presence.undefined());
                case "present-null" -> new Model(Presence.present(null));
                default -> {
                    if (!payload.startsWith("value:") || payload.length() > 128)
                        throw new IllegalArgumentException("Invalid presence payload");
                    yield new Model(Presence.present(payload.substring(6)));
                }
            };
        }
    };
    private static final class Peer extends ClassComponent<Props,Model,Void> {
        Peer(Props props) { super(props, null); }
        public View render() { return null; }
    }
    private static ClassComponentType<Props,Model,Peer> type() {
        return ClassComponentType.normal("Presence", PROPS, MODEL, Peer::new, Set.of());
    }

    @Test void explicitNullablePresenceCodecWorksInBothModifierOrdersWithoutCollapsingValues() {
        var first = type().withNullableState().withStateCheckpoint("presence:1", CODEC);
        var last = type().withStateCheckpoint("presence:1", CODEC).withNullableState();
        for (var configured : List.of(first, last)) {
            assertSame(MODEL, configured.stateSchema());
            assertSame(CODEC, configured.checkpoint().codec());
            assertNull(configured.create(new Props()).initialState());
            assertNull(CODEC.decode(CODEC.encode(null)));
            configured.validateState(null);
            for (Model value : List.of(new Model(Presence.absent()), new Model(Presence.undefined()),
                    new Model(Presence.present(null)), new Model(Presence.present("")),
                    new Model(Presence.present("null:undefined:π")))) {
                Model restored = CODEC.decode(CODEC.encode(value));
                configured.validateState(restored);
                assertEquals(value, restored);
                assertNotSame(value, restored);
            }
        }
        assertNull(type().checkpoint());
        assertFalse(type().nullableState());
    }

    @Test void explicitCodecDoesNotRelaxStateSchemaOrAdmitPure() {
        var strict = type().withStateCheckpoint("presence:1", CODEC);
        assertThrows(NullPointerException.class, () -> strict.validateState(CODEC.decode("null")));
        assertThrows(NullPointerException.class, () -> strict.validateState(new Model(null)));
        assertThrows(IllegalArgumentException.class, () -> CODEC.decode("unknown"));
        assertThrows(IllegalArgumentException.class, () -> CODEC.decode("value:" + "x".repeat(129)));
        assertThrows(IllegalArgumentException.class, () -> CODEC.encode(new Model(Presence.present("x".repeat(129)))));
        class PurePeer extends PureComponent<Props,Model,Void> {
            PurePeer(Props props) { super(props, null); }
            public View render() { return null; }
        }
        var pure = ClassComponentType.pure("PurePresence", PROPS, MODEL, PurePeer::new, Set.of()).withNullableState();
        assertThrows(IllegalArgumentException.class, () -> pure.withStateCheckpoint("presence:1", CODEC));
    }
}
