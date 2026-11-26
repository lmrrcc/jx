package jx.example.todo;

import jx.browser.Http;
import jx.browser.Json;
import jx.elm.Command;
import jx.example.contract.Todo;
import jx.example.contract.TodoApi;
import jx.example.contract.TodoJson;
import java.util.function.Function;

/** The generated record codec shares the contract's fields; transport failures remain explicit. */
public final class TodoGateway implements TodoProgram.Gateway {
    @Override public Command<TodoProgram.Message> load() {
        return request("load", Http.Request.get(TodoApi.COLLECTION), json -> new TodoProgram.Loaded(json.items().stream().map(TodoJson::decode).toList()));
    }
    @Override public Command<TodoProgram.Message> create(String title) {
        return request("mutation", Http.Request.post(TodoApi.COLLECTION, Json.object().put("title", Json.string(title))), json -> new TodoProgram.Saved(TodoJson.decode(json), true));
    }
    @Override public Command<TodoProgram.Message> update(Todo todo) {
        Json body = Json.object().put("title", Json.string(todo.title())).put("completed", Json.bool(todo.completed())).put("version", Json.number(todo.version()));
        return request("mutation", Http.Request.put(TodoApi.item(todo.id()), body), json -> new TodoProgram.Saved(TodoJson.decode(json), false));
    }
    @Override public Command<TodoProgram.Message> remove(Todo todo) {
        return request("mutation", Http.Request.delete(TodoApi.deletion(todo.id(), todo.version())), ignored -> new TodoProgram.Removed(todo.id()));
    }
    private static Command<TodoProgram.Message> request(String key, Http.Request request, Function<Json, TodoProgram.Message> decoder) {
        return new Command<>(key, reply -> {
            return Http.send(request, decoder, result -> {
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
        });
    }
}
