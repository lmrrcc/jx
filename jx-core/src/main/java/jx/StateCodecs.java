package jx;

import java.util.Objects;
import java.util.function.Function;

/** Nullable scalar codecs. Explicit codecs are required for custom Java values and null initial state
 * whose eventual type cannot be inferred. String payloads are in-memory development checkpoints only. */
public final class StateCodecs {
    private StateCodecs() {}
    public static StateCodec<String> strings() { return codec("jx:string:1", value -> value, value -> value); }
    public static StateCodec<Integer> integers() { return codec("jx:int:1", Object::toString, Integer::valueOf); }
    public static StateCodec<Boolean> booleans() { return codec("jx:boolean:1", Object::toString, value -> {
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Invalid boolean checkpoint");
        return Boolean.valueOf(value);
    }); }
    public static StateCodec<Long> longs() { return codec("jx:long:1", Object::toString, Long::valueOf); }
    public static StateCodec<Double> doubles() { return codec("jx:double:1", Object::toString, Double::valueOf); }
    public static StateCodec<Float> floats() { return codec("jx:float:1", Object::toString, Float::valueOf); }
    public static StateCodec<Short> shorts() { return codec("jx:short:1", Object::toString, Short::valueOf); }
    public static StateCodec<Byte> bytes() { return codec("jx:byte:1", Object::toString, Byte::valueOf); }
    public static StateCodec<Character> characters() { return codec("jx:char:1", Object::toString, value -> {
        if (value.length() != 1) throw new IllegalArgumentException("Invalid character checkpoint"); return value.charAt(0);
    }); }
    private static <T> StateCodec<T> codec(String schema, Function<T, String> encoder, Function<String, T> decoder) {
        return new StateCodec<>() {
            @Override public String schema() { return schema; }
            @Override public String encode(T value) { return value == null ? "0" : "1" + encoder.apply(value); }
            @Override public T decode(String payload) {
                Objects.requireNonNull(payload);
                if (payload.equals("0")) return null;
                if (!payload.startsWith("1")) throw new IllegalArgumentException("Invalid scalar checkpoint");
                return decoder.apply(payload.substring(1));
            }
        };
    }
    /** Renderer inference, restricted to exact scalar types. Null/unsupported values return no codec.
     * The cast is confined to runtime type inference; no value from an older Java runtime is inspected. */
    @SuppressWarnings("unchecked")
    public static <T> StateCodec<T> forInitialValue(T value) {
        StateCodec<?> codec = switch (value) {
            case String ignored -> strings();
            case Integer ignored -> integers();
            case Boolean ignored -> booleans();
            case Long ignored -> longs();
            case Double ignored -> doubles();
            case Float ignored -> floats();
            case Short ignored -> shorts();
            case Byte ignored -> bytes();
            case Character ignored -> characters();
            case null, default -> null;
        };
        return (StateCodec<T>) codec;
    }
}
