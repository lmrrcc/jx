package jx.router;

import java.util.Objects;
import java.util.Optional;
import org.teavm.jso.JSBody;

/** Immutable browser location. path is encoded; query/hash omit their leading ?/#. */
public record Location(String path, String query, String hash) {
    public Location { Objects.requireNonNull(path); Objects.requireNonNull(query); Objects.requireNonNull(hash); }
    public Optional<String> queryParameter(String name) { return Optional.ofNullable(parameter(query, Objects.requireNonNull(name))); }
    private static String parameter(String query, String name) { return Query.parameter(query, name); }
    private static final class Query {
        @JSBody(params = {"query", "name"}, script = "return new URLSearchParams(query).get(name);") private static native String parameter(String query, String name);
    }
}
