package jx.example.extension;

import java.util.Objects;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/** Example-only typed binding to the pinned Rust brotli-wasm package.
 * Input is bounded trusted demonstration text, not an untrusted compressed stream. */
public final class BrotliProof {
    private BrotliProof() {}

    public record Result(String text, int inputBytes, int compressedBytes) {
        public Result {
            Objects.requireNonNull(text);
            if (inputBytes < 1 || inputBytes > 16384 || compressedBytes < 1 || compressedBytes > 32768)
                throw new IllegalArgumentException("Invalid bounded compression result");
        }
    }

    /** Synchronous pure computation after bootstrap; no I/O, secrets or retained Java objects. */
    public static Result roundTrip(String text) {
        Objects.requireNonNull(text);
        if (text.isEmpty() || text.length() > 4096) throw new IllegalArgumentException("Text must contain 1..4096 UTF-16 units");
        NativeResult result = roundTripNative(text);
        Result value = new Result(result.text(), result.inputBytes(), result.compressedBytes());
        if (!text.equals(value.text())) throw new IllegalStateException("WASM round trip changed the text");
        return value;
    }

    private interface NativeResult extends JSObject {
        @JSProperty String text();
        @JSProperty int inputBytes();
        @JSProperty int compressedBytes();
    }

    @JSBody(params = "text", script = "var input = new TextEncoder().encode(text);"
            + "var compressed = globalThis.__JX_BROTLI__.compress(input, {quality: 3});"
            + "var restored = globalThis.__JX_BROTLI__.decompress(compressed);"
            + "return {text: new TextDecoder('utf-8', {fatal: true}).decode(restored),"
            + "inputBytes: input.length, compressedBytes: compressed.length};")
    private static native NativeResult roundTripNative(String text);
}
