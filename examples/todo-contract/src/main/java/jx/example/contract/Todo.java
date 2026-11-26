package jx.example.contract;

import java.util.Objects;

/** Immutable public payload shared by Spring and the browser. IDs are opaque strings;
 * versions are nonnegative signed 32-bit optimistic concurrency tokens. */
public record Todo(String id, String title, boolean completed, int version) {
    public Todo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        if (version < 0) throw new IllegalArgumentException("Negative version");
    }
}
