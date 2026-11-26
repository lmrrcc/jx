package jx.fixture.ssr;

import java.util.List;
import java.util.Objects;
import jx.example.contract.Todo;

/** One authenticated request's immutable public data. No credentials or CSRF token. */
public record TodoSnapshot(String username, List<Todo> todos) {
    public TodoSnapshot {
        Objects.requireNonNull(username);
        if (username.isBlank() || username.length() > 128) throw new IllegalArgumentException("Invalid authenticated username");
        if (todos.size() > 500) throw new IllegalArgumentException("Too many todos in SSR snapshot");
        todos = List.copyOf(todos);
        for (Todo todo : todos) {
            if (todo.id().isEmpty() || todo.id().length() > 128 || todo.title().length() > 2000)
                throw new IllegalArgumentException("Invalid bounded Todo payload");
        }
    }
}
