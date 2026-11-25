package jx.react;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable browser import-map configuration for a server-rendered document. Keys and targets
 * are copied without URL normalization: relative addresses use the document's base URL and the
 * browser owns resolution, scope matching and integrity enforcement. Supply trusted build
 * configuration, never unvalidated request data that could redirect executable module imports.
 * Null top-level maps omit their field; empty maps retain an empty object. A null import target
 * explicitly blocks that specifier, rather than falling through to an enclosing scope.
 * At most 4096 entries (including scopes) and 1 Mi UTF-16 code units are retained per map. */
public record ImportMap(Map<String, String> imports, Map<String, Map<String, String>> scopes,
                        Map<String, String> integrity) {
    public ImportMap {
        Budget budget = new Budget();
        imports = copy(imports, true, budget);
        if (scopes != null) {
            var result = new LinkedHashMap<String, Map<String, String>>();
            for (var entry : scopes.entrySet()) {
                String key = budget.key(entry.getKey());
                result.put(key, copy(Objects.requireNonNull(entry.getValue(), "Null import-map scope"), true, budget));
            }
            scopes = Collections.unmodifiableMap(result);
        }
        integrity = copy(integrity, false, budget);
    }

    /** Imports and scopes without integrity metadata. */
    public ImportMap(Map<String, String> imports, Map<String, Map<String, String>> scopes) {
        this(imports, scopes, null);
    }

    private static Map<String, String> copy(Map<String, String> source, boolean nullable, Budget budget) {
        if (source == null) return null;
        var result = new LinkedHashMap<String, String>();
        for (var entry : source.entrySet()) {
            String key = budget.key(entry.getKey());
            String value = entry.getValue();
            if (!nullable) Objects.requireNonNull(value, "Null integrity metadata");
            if (value != null) budget.text(value);
            result.put(key, value);
        }
        return Collections.unmodifiableMap(result);
    }

    private static final class Budget {
        int entries;
        int units;
        String key(String key) {
            Objects.requireNonNull(key, "Null import-map key");
            if (key.isEmpty()) throw new IllegalArgumentException("Empty import-map key");
            if (++entries > 4096) throw new IllegalArgumentException("Import map exceeds 4096 entries");
            text(key);
            return key;
        }
        void text(String value) {
            if (value.length() > 1_048_576 - units)
                throw new IllegalArgumentException("Import map exceeds 1 Mi UTF-16 code units");
            units += value.length();
        }
    }
}
