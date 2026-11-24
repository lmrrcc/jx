package jx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** Typed counterpart of React's {@code Children} for a {@code View children} prop.
 * Collection children ({@link View.Items}) are traversed recursively; elements, components,
 * providers, written fragments and renderer extensions are opaque leaves and never rendered.
 * An empty position ({@link View.Empty}, from a null or boolean inside a collection) is counted
 * and visited, but omitted from {@link #toList}. A null children prop has no positions. */
public final class Children {
    private Children() {}

    /** An immutable flat list without empty positions. Elements receive keys composed from their
     * original keys, collection nesting and positions so flattening preserves identity. Empty for null. */
    public static List<View> toList(View children) {
        View mapped = map(children, Function.identity());
        return mapped == null ? List.of() : ((View.Items) mapped).children();
    }

    /** The number of leaf positions, empty positions included; zero for a null children prop. */
    public static int count(View children) {
        if (children == null) return 0;
        if (!(children instanceof View.Items items)) return 1;
        int count = 0;
        for (View child : items.children()) count += count(child);
        return count;
    }

    /** Maps each position, passing {@link View.Empty} for an empty position. The result is a flat
     * {@link View.Items}, or null when the children prop is null. A null or Empty mapping result
     * is omitted. A returned Items is flattened, whereas a returned fragment stays one element.
     * Result element keys combine the original position/key with the mapper's key; keys in a
     * returned collection need only be unique within that collection. Inputs are never mutated. */
    public static View map(View children, Function<View, View> mapper) {
        Objects.requireNonNull(mapper);
        if (children == null) return null;
        var result = new ArrayList<View>();
        mapInto(children, result, "", "", mapper);
        return new View.Items(result);
    }

    private static void mapInto(View child, List<View> result, String prefix, String path, Function<View, View> mapper) {
        if (child instanceof View.Items items) {
            String next = path.isEmpty() ? "." : path + ":";
            for (int index = 0; index < items.children().size(); index++) {
                View item = items.children().get(index);
                mapInto(item, result, prefix, next + segment(item, index), mapper);
            }
            return;
        }
        View mapped = mapper.apply(child);
        String sourcePath = path.isEmpty() ? "." + segment(child, 0) : path;
        if (mapped instanceof View.Items items) {
            mapInto(items, result, escapeSlashes(sourcePath) + "/", "", Function.identity());
        } else if (mapped != null && !(mapped instanceof View.Empty)) {
            if (Views.isValidElement(mapped)) {
                String mappedKey = Views.key(mapped);
                String extra = mappedKey != null && !Objects.equals(mappedKey, Views.key(child))
                        ? escapeSlashes(mappedKey) + "/" : "";
                mapped = new View.Keyed(mapped, prefix + extra + sourcePath);
            }
            result.add(mapped);
        }
    }

    private static String segment(View child, int index) {
        String key = Views.key(child);
        return key == null ? Integer.toString(index, 36) : "$" + key.replace("=", "=0").replace(":", "=2");
    }

    private static String escapeSlashes(String value) { return value.replaceAll("/+", "$0/"); }

    /** Visits original leaf positions in order without changing keys or rendering components.
     * Empty positions are passed as {@link View.Empty}; a null children prop invokes nothing. */
    public static void forEach(View children, Consumer<View> action) {
        Objects.requireNonNull(action);
        if (children instanceof View.Items items) items.children().forEach(child -> forEach(child, action));
        else if (children != null) action.accept(children);
    }

    /** Returns the supplied single valid element unchanged. An array with one element is still
     * an array and is rejected, as are text, empty positions, portals and null.
     * @throws IllegalArgumentException when children is not itself a valid element */
    public static View only(View children) {
        if (!Views.isValidElement(children)) throw new IllegalArgumentException("Expected a single element");
        return children;
    }
}
