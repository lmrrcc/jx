package jx.example.todo;

import jx.StateCodec;
import jx.browser.Json;

/** Explicit development checkpoint schema; decoding creates values belonging to the new Java bundle. */
public final class TodoStateCodec implements StateCodec<TodoProgram.Model> {
    @Override public String schema() { return "todo-model-v1"; }
    @Override public String encode(TodoProgram.Model model) {
        return Json.object().put("todos", Json.array(model.todos().stream().map(todo -> Json.object()
                .put("id", Json.string(todo.id())).put("title", Json.string(todo.title()))
                .put("completed", Json.bool(todo.completed())).put("version", Json.number(todo.version()))).toList()))
                .put("phase", Json.string(model.phase().name())).put("error", Json.string(model.error()))
                .put("createdCount", Json.number(model.createdCount())).stringify();
    }
    @Override public TodoProgram.Model decode(String payload) {
        Json json = Json.parse(payload);
        var rows = json.get("todos").items();
        if (rows.size() > 500) throw new IllegalArgumentException("Too many todos in development checkpoint");
        var todos = rows.stream().map(row -> new Todo(row.get("id").stringValue(), row.get("title").stringValue(),
                row.get("completed").booleanValue(), row.get("version").intValue())).toList();
        TodoProgram.Phase phase = TodoProgram.Phase.valueOf(json.get("phase").stringValue());
        String error = json.get("error").stringValue();
        if (phase == TodoProgram.Phase.LOADING || phase == TodoProgram.Phase.SAVING) {
            phase = TodoProgram.Phase.ERROR;
            error = "Development update interrupted a request. Reload the list to check the server result.";
        }
        return new TodoProgram.Model(todos, phase, error, json.get("createdCount").intValue());
    }
}
