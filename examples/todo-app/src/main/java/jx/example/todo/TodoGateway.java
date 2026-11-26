package jx.example.todo;

import jx.browser.Http;
import jx.Cancellation;
import jx.browser.Json;
import jx.elm.Command;
import jx.example.contract.Todo;
import jx.example.contract.TodoApi;
import jx.example.contract.TodoJson;
import jx.example.contract.SessionApi;
import jx.example.contract.SessionInfo;
import jx.example.contract.SessionInfoJson;
import jx.elm.Dispatch;
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
        return new Command<>(key, reply -> new RequestOwner(request, decoder, reply).start());
    }
    /** Owns both stages. Refresh the session token before a mutation; never retry the mutation.
     * Native form login/logout navigates to a fresh application realm and disposes private UI state. */
    private static final class RequestOwner implements Cancellation {
        private final Http.Request request;
        private final Function<Json, TodoProgram.Message> decoder;
        private final Dispatch<TodoProgram.Message> reply;
        private Cancellation pending = Cancellation.NONE;
        private boolean closed;
        RequestOwner(Http.Request request, Function<Json, TodoProgram.Message> decoder, Dispatch<TodoProgram.Message> reply) {
            this.request = request; this.decoder = decoder; this.reply = reply;
        }
        Cancellation start() {
            if (request.method() == Http.Method.GET) send(request);
            else pending = Http.get(SessionApi.CURRENT, SessionInfoJson::decode, result -> {
                if (closed) return;
                switch (result) {
                    case Http.Success<SessionInfo> success -> {
                        if (success.value().username().isEmpty()) reply.send(new TodoProgram.SignedOut());
                        else send(request.withHeader(SessionApi.CSRF_HEADER, success.value().csrfToken()));
                    }
                    case Http.Failure<SessionInfo> failure -> fail(failure);
                }
            });
            return this;
        }
        private void send(Http.Request authorized) {
            pending = Http.send(authorized, decoder, result -> {
                if (closed) return;
                switch (result) {
                    case Http.Success<TodoProgram.Message> success -> reply.send(success.value());
                    case Http.Failure<TodoProgram.Message> failure -> fail(failure);
                }
            });
        }
        private void fail(Http.Failure<?> failure) {
            if (failure.status() == 401) { reply.send(new TodoProgram.SignedOut()); return; }
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
        @Override public void cancel() { if (!closed) { closed = true; pending.cancel(); } }
    }
}
