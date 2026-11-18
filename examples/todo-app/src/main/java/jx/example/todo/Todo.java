package jx.example.todo;

import java.util.Objects;

/** Browser-safe immutable JSON contract; IDs remain strings and versions are bounded Java ints. */
public record Todo(String id, String title, boolean completed, int version) {
    public Todo { Objects.requireNonNull(id); Objects.requireNonNull(title); if (version < 0) throw new IllegalArgumentException("Negative version"); }
}
