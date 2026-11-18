package jx.router;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.teavm.jso.JSBody;

/** Exact path template with named segments, for example /todos/:id. No implicit wildcard matching. */
public final class Route {
    private final String[] segments;
    public Route(String template) {
        Objects.requireNonNull(template);
        if (!template.startsWith("/") || template.length() > 2048) throw new IllegalArgumentException("Invalid route template");
        segments = template.split("/", -1);
        var names = new LinkedHashSet<String>();
        for (String segment : segments) {
            if (segment.startsWith(":")) {
                String name = segment.substring(1);
                if (!name.matches("[a-zA-Z][a-zA-Z0-9_]*") || !names.add(name)) throw new IllegalArgumentException("Invalid/duplicate route parameter");
            } else if (!segment.matches("[a-zA-Z0-9._~-]*")) throw new IllegalArgumentException("Invalid literal route segment");
        }
    }
    public Optional<Match> match(Location location) {
        String[] values = location.path().split("/", -1);
        if (values.length != segments.length) return Optional.empty();
        var params = new LinkedHashMap<String, String>();
        for (int i = 0; i < segments.length; i++) {
            String decoded = decode(values[i]);
            if (decoded == null) return Optional.empty();
            if (segments[i].startsWith(":")) {
                if (decoded.isEmpty() || decoded.contains("/")) return Optional.empty();
                params.put(segments[i].substring(1), decoded);
            } else if (!segments[i].equals(decoded)) return Optional.empty();
        }
        return Optional.of(new Match(params));
    }
    public record Match(Map<String, String> parameters) {
        public Match { parameters = Map.copyOf(parameters); }
        public String parameter(String name) {
            String value = parameters.get(name);
            if (value == null) throw new IllegalArgumentException("Unknown route parameter: " + name);
            return value;
        }
    }
    @JSBody(params = "segment", script = "try { return decodeURIComponent(segment); } catch (error) { return null; }") private static native String decode(String segment);
}
