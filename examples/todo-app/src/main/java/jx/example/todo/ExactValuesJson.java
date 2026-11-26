package jx.example.todo;

import jx.browser.Json;
import jx.example.contract.ExactValues;

/** Explicit proof adapter; this richer schema is not advertised as generated support. */
final class ExactValuesJson {
    private ExactValuesJson() {}
    static Json encode(ExactValues value) {
        Json json = Json.object().put(ExactValues.ID, Json.string(value.idText()))
                .put(ExactValues.AMOUNT, Json.string(value.amountText()))
                .put(ExactValues.NOTE, nullable(value.note()));
        return value.extra().present() ? json.put(ExactValues.EXTRA, nullable(value.extra().value())) : json;
    }
    static ExactValues decode(Json json) {
        Json note = json.get(ExactValues.NOTE);
        var extra = json.has(ExactValues.EXTRA) ? ExactValues.TextField.present(nullable(json.get(ExactValues.EXTRA)))
                : ExactValues.TextField.absent();
        return ExactValues.fromWire(json.get(ExactValues.ID).stringValue(), json.get(ExactValues.AMOUNT).stringValue(), nullable(note), extra);
    }
    private static Json nullable(String value) { return value == null ? Json.nil() : Json.string(value); }
    private static String nullable(Json value) { return value.isNull() ? null : value.stringValue(); }
}
