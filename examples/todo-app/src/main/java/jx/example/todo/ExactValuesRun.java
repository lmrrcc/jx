package jx.example.todo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import jx.Cancellation;
import jx.browser.Http;
import jx.browser.Json;
import jx.example.contract.ExactValues;

/** One committed proof owner; cleanup cancels the outstanding real HTTP request. */
final class ExactValuesRun {
    private final Consumer<String> report;
    private final List<ExactValues> vectors = ExactValues.vectors();
    private final List<String> rows = new ArrayList<>();
    private Cancellation current = Cancellation.NONE;
    private boolean closed;
    ExactValuesRun(Consumer<String> report) { this.report = report; }
    void start() {
        // Native JSON types are strict even for small values. Missing required nullable
        // fields and present null must never collapse into the same decode.
        for (String invalid : List.of("{\"id\":1,\"amount\":\"1.0\",\"note\":null}",
                "{\"id\":\"1\",\"amount\":1,\"note\":null}", "{\"id\":\"1\",\"amount\":\"1.0\"}")) {
            boolean rejected = false;
            try { ExactValuesJson.decode(Json.parse(invalid)); }
            catch (IllegalArgumentException expected) { rejected = true; }
            if (!rejected) { report.accept("FAIL: invalid browser input accepted"); return; }
        }
        next(0);
    }
    private void next(int index) {
        if (closed) return;
        if (index == vectors.size()) { report.accept("PASS " + rows.size() + "\n" + String.join("\n", rows)); return; }
        ExactValues expected = vectors.get(index);
        current = Http.send(Http.Request.post(ExactValues.PATH, ExactValuesJson.encode(expected)), ExactValuesJson::decode, result -> {
            if (closed) return;
            switch (result) {
                case Http.Success<ExactValues> success -> {
                    ExactValues actual = success.value();
                    if (!expected.equals(actual)) { report.accept("FAIL: round trip " + index); return; }
                    // These computations execute in Java bytecode compiled by TeaVM, beyond
                    // comparing the two textual payloads. BigDecimal equality includes scale.
                    rows.add(actual.idText() + "|" + actual.amount().unscaledValue() + "|" + actual.amount().scale()
                            + "|" + (actual.extra().present() ? actual.extra().value() == null ? "null" : "value" : "absent"));
                    next(index + 1);
                }
                case Http.Failure<ExactValues> failure -> report.accept("FAIL: " + failure.kind() + " " + failure.message());
            }
        });
    }
    void close() { closed = true; current.cancel(); }
}
