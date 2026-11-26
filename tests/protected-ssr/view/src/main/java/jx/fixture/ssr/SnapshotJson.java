package jx.fixture.ssr;

import jx.browser.Json;
import jx.example.contract.Todo;
import jx.example.contract.TodoJson;
import java.util.List;

/** Explicit bootstrap schema shared by both independently compiled entry points. */
public final class SnapshotJson {
    private static final int LIMIT = 1_048_576;
    private SnapshotJson() {}

    public static TodoSnapshot fromResponses(String session, String todos) {
        Json identity = bounded(session);
        return new TodoSnapshot(identity.get("username").stringValue(),
                todos(bounded(todos)));
    }

    public static TodoSnapshot decode(String encoded) {
        Json json = bounded(encoded);
        if (json.get("schema").intValue() != 1) throw new IllegalArgumentException("Unsupported SSR snapshot schema");
        return new TodoSnapshot(json.get("username").stringValue(),
                todos(json.get("todos")));
    }

    public static String encode(TodoSnapshot snapshot) {
        String value = Json.object().put("schema", Json.number(1))
                .put("username", Json.string(snapshot.username()))
                .put("todos", Json.array(snapshot.todos().stream().map(TodoJson::encode).toList())).stringify();
        if (value.length() > LIMIT) throw new IllegalArgumentException("SSR snapshot exceeds bootstrap limit");
        return value;
    }

    /** A JSON data script must never contain a literal HTML closing-script delimiter. */
    public static String scriptData(TodoSnapshot snapshot) {
        return encode(snapshot).replace("<", "\\u003c").replace(">", "\\u003e")
                .replace("&", "\\u0026").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }

    private static Json bounded(String value) {
        if (value.length() > LIMIT) throw new IllegalArgumentException("SSR JSON exceeds input limit");
        return Json.parse(value);
    }

    private static List<Todo> todos(Json array) {
        var values = array.items();
        if (values.size() > 500) throw new IllegalArgumentException("Too many todos in SSR snapshot");
        return values.stream().map(TodoJson::decode).toList();
    }
}
