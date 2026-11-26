package jx.example.contract;

import java.util.Objects;

/** The demo's single path authority. GET/POST use COLLECTION, PUT/DELETE use an
 * item, and DELETE requires the current version. These paths grant no authority;
 * validation, conflict checks and persistence remain server responsibilities. */
public final class TodoApi {
    public static final String COLLECTION = "/api/todos";
    public static final String ITEM_TEMPLATE = "/{id}";
    public static final String ID_PARAMETER = "id";
    public static final String VERSION_PARAMETER = "version";

    private TodoApi() {}

    /** Returns one bounded, unreserved path segment. No encoding or path traversal
     * interpretation is applied to opaque identifiers. */
    public static String item(String id) {
        Objects.requireNonNull(id, "id");
        if (id.isEmpty() || id.length() > 128 || id.equals(".") || id.equals("..")) {
            throw new IllegalArgumentException("Invalid Todo identifier");
        }
        for (int index = 0; index < id.length(); index++) {
            char value = id.charAt(index);
            if (!(value >= 'a' && value <= 'z' || value >= 'A' && value <= 'Z'
                    || value >= '0' && value <= '9' || value == '-' || value == '_'
                    || value == '.' || value == '~')) {
                throw new IllegalArgumentException("Todo identifier must be an unreserved path segment");
            }
        }
        return COLLECTION + "/" + id;
    }

    /** DELETE URL carrying the optimistic concurrency token, never a silent retry. */
    public static String deletion(String id, int version) {
        if (version < 0) throw new IllegalArgumentException("Negative version");
        return item(id) + "?" + VERSION_PARAMETER + "=" + version;
    }
}
