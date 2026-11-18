package jx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Factories for Java-authored views. Strings are text, never HTML. Null/boolean children are absent. */
public final class Views {
    private Views() {}
    public static View element(String tag, Attributes attributes, Object... children) {
        if (!Objects.requireNonNull(tag).matches("[A-Za-z][A-Za-z0-9:_-]*")) throw new IllegalArgumentException("Invalid element name: " + tag);
        return new View.Element(tag, attributes, children(children));
    }
    public static <P> View component(Component<P> component, P props) { return component(component, props, null); }
    public static <P> View component(Component<P> component, P props, String key) { return new View.ComponentView<>(component, props, key); }
    public static View fragment(Object... children) { return new View.Fragment(children(children)); }
    static List<View> children(Object[] children) {
        var result = new ArrayList<View>();
        for (Object child : children) append(result, child);
        return List.copyOf(result);
    }
    private static void append(List<View> result, Object child) {
        if (child == null || child instanceof Boolean) return;
        if (child instanceof View view) result.add(view);
        else if (child instanceof String || child instanceof Character || child instanceof Number) result.add(new View.Text(child.toString()));
        else if (child instanceof Iterable<?> items) for (Object item : items) append(result, item);
        else if (child instanceof Object[] items) for (Object item : items) append(result, item);
        else throw new IllegalArgumentException("Unsupported child type: " + child.getClass().getName());
    }
}
