package jx.example.todo;

import jx.browser.Http;
import jx.browser.Json;
import jx.elm.Command;
import java.util.function.Function;

/** Explicit JSON schema at the browser/server boundary; no reflective serialization or JVM networking. */
public final class TodoGateway implements TodoProgram.Gateway {
    private static final String URL = "/api/todos";
    @Override public Command<TodoProgram.Message> load() {
        return request("load", Http.Request.get(URL), json -> new TodoProgram.Loaded(json.items().stream().map(TodoGateway::todo).toList()));
    }
    @Override public Command<TodoProgram.Message> create(String title) {
        return request("mutation", Http.Request.post(URL, Json.object().put("title", Json.string(title))), json -> new TodoProgram.Saved(todo(json), true));
    }
    @Override public Command<TodoProgram.Message> update(Todo todo) {
        Json body = Json.object().put("title", Json.string(todo.title())).put("completed", Json.bool(todo.completed())).put("version", Json.number(todo.version()));
        return request("mutation", Http.Request.put(URL + "/" + todo.id(), body), json -> new TodoProgram.Saved(todo(json), false));
    }
    @Override public Command<TodoProgram.Message> remove(Todo todo) {
        return request("mutation", Http.Request.delete(URL + "/" + todo.id() + "?version=" + todo.version()), ignored -> new TodoProgram.Removed(todo.id()));
    }
    private static Command<TodoProgram.Message> request(String key, Http.Request request, Function<Json, TodoProgram.Message> decoder) {
        return new Command<>(key, reply -> {
            var cancellation = Http.send(request, decoder, result -> {
                switch (result) {
                    case Http.Success<TodoProgram.Message> success -> reply.send(success.value());
                    case Http.Failure<TodoProgram.Message> failure -> {
                        String message = failure.message();
                        try {
                            if (!failure.body().isNull() && failure.body().has("detail")) {
                                String detail = failure.body().get("detail").stringValue();
                                if (!detail.isBlank()) message = detail;
                            }
                        } catch (IllegalArgumentException invalidProblemDetail) {
                            // An optional server explanation must not prevent the HTTP failure transition.
                        }
                        reply.send(new TodoProgram.Failed(message));
                    }
                }
            });
            return cancellation::cancel;
        });
    }
    private static Todo todo(Json json) {
        return new Todo(json.get("id").stringValue(), json.get("title").stringValue(), json.get("completed").booleanValue(), json.get("version").intValue());
    }
}
