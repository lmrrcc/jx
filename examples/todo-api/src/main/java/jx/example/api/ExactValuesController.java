package jx.example.api;

import jx.example.contract.ExactValues;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** Explicit wire adapter for the opt-in contract proof. This is not an inference of
 * arbitrary Jackson record serialization or an application-owned numeric ID policy. */
@RestController
@Profile("contract-proof")
public final class ExactValuesController {
    @PostMapping(value = ExactValues.PATH, consumes = "application/json", produces = "application/json")
    public JsonNode echo(@RequestBody JsonNode json) {
        try { return encode(decode(json)); }
        catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid exact-value contract", invalid);
        }
    }

    static ExactValues decode(JsonNode json) {
        if (!json.isObject() || json.size() < 3 || json.size() > 4
                || !json.has(ExactValues.NOTE)) throw new IllegalArgumentException("Invalid contract object");
        for (String name : json.propertyNames()) {
            if (!name.equals(ExactValues.ID) && !name.equals(ExactValues.AMOUNT)
                    && !name.equals(ExactValues.NOTE) && !name.equals(ExactValues.EXTRA)) {
                throw new IllegalArgumentException("Unknown contract field");
            }
        }
        return ExactValues.fromWire(requiredString(json.get(ExactValues.ID)), requiredString(json.get(ExactValues.AMOUNT)),
                nullableString(json.get(ExactValues.NOTE)), json.has(ExactValues.EXTRA)
                        ? ExactValues.TextField.present(nullableString(json.get(ExactValues.EXTRA))) : ExactValues.TextField.absent());
    }
    static ObjectNode encode(ExactValues value) {
        ObjectNode json = JsonNodeFactory.instance.objectNode()
                .put(ExactValues.ID, value.idText()).put(ExactValues.AMOUNT, value.amountText());
        json.set(ExactValues.NOTE, value.note() == null ? JsonNodeFactory.instance.nullNode() : JsonNodeFactory.instance.stringNode(value.note()));
        if (value.extra().present()) {
            json.set(ExactValues.EXTRA, value.extra().value() == null ? JsonNodeFactory.instance.nullNode()
                    : JsonNodeFactory.instance.stringNode(value.extra().value()));
        }
        return json;
    }
    private static String requiredString(JsonNode value) {
        if (value == null || !value.isString()) throw new IllegalArgumentException("Expected required string");
        return value.stringValue();
    }
    private static String nullableString(JsonNode value) {
        if (value == null) throw new IllegalArgumentException("Missing required nullable field");
        return value.isNull() ? null : requiredString(value);
    }
}
