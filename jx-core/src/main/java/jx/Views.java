package jx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Factories for Java-authored views. Strings are text, never HTML. As in React, a null or boolean
 * child renders nothing but keeps its position, and a collection child holds one position. */
public final class Views {
    private Views() {}
    public static View element(String tag, Attributes attributes, Object... children) {
        if (!Objects.requireNonNull(tag).matches("[A-Za-z][A-Za-z0-9:_-]*")) throw new IllegalArgumentException("Invalid element name: " + tag);
        return new View.Element(tag, attributes, children(children));
    }
    public static <P> View component(ComponentType<P> component, P props) { return component(component, props, null); }
    public static <P> View component(ComponentType<P> component, P props, String key) { return new View.ComponentView<>(component, props, key); }
    /** Typed counterpart of React's {@code cloneElement} for a DOM element: a copy with
     * {@code extra} merged over its attributes, including key and ref, and with its children
     * replaced when any are given. Component views carry a typed Props record; build a new one
     * instead of cloning.
     * @throws IllegalArgumentException when the view is not a DOM element */
    public static View cloneElement(View element, Attributes extra, Object... children) {
        if (!(element instanceof View.Element original))
            throw new IllegalArgumentException("Only a DOM element view can be cloned");
        return new View.Element(original.tag(), original.attributes().merge(extra),
                children.length == 0 ? original.children() : children(children));
    }
    /** React's {@code isValidElement}: true for an element, fragment, component or other view;
     * false for text, numbers, null and everything that is not a view. */
    public static boolean isValidElement(Object value) { return value instanceof View && !(value instanceof View.Text || value instanceof View.Empty || value instanceof View.Items); }
    public static View fragment(Object... children) { return new View.Fragment(children(children)); }
    static List<View> children(Object[] children) {
        var result = new ArrayList<View>();
        for (Object child : children) append(result, child);
        return List.copyOf(result);
    }
    private static View items(Iterable<?> items) {
        var result = new ArrayList<View>();
        for (Object item : items) append(result, item);
        return new View.Items(result);
    }
    private static void append(List<View> result, Object child) {
        if (child == null || child instanceof Boolean) result.add(View.Empty.INSTANCE);
        else if (child instanceof View view) result.add(view);
        else if (child instanceof String || child instanceof Character || child instanceof Number) result.add(new View.Text(child.toString()));
        else if (child instanceof Iterable<?> items) result.add(items(items));
        else if (child instanceof Object[] items) result.add(items(java.util.Arrays.asList(items)));
        else throw new IllegalArgumentException("Unsupported child type: " + child.getClass().getName());
    }
}
