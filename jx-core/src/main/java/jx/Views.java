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
    public static <P,S,C extends ClassComponent<P,S,?>> View component(ClassComponentType<P,S,C> component, P props) {
        return component(component, props, null, null);
    }
    public static <P,S,C extends ClassComponent<P,S,?>> View component(ClassComponentType<P,S,C> component,
            P props, String key, ClassRef<C> ref) { return new View.ClassComponentView<>(component, props, key, ref); }
    /** Describes a class element with raw input properties. Missing/undefined fields remain
     * raw in this Java description; React applies native defaults before the class's strict
     * props schema is decoded. Invalid unresolved props fail before Java peer construction.
     * This method does not call the component or construct its resolved record. */
    public static <P,S,C extends ClassComponent<P,S,?>> View.ClassPropsView<P,S,C> classPropsView(
            ClassComponentType<P,S,C> component, ClassProps<P> props) {
        return classPropsView(component, props, null, null);
    }
    /** Raw class input with nullable key/ref metadata; React owns reconciliation and refs. */
    public static <P,S,C extends ClassComponent<P,S,?>> View.ClassPropsView<P,S,C> classPropsView(
            ClassComponentType<P,S,C> component, ClassProps<P> props, String key, ClassRef<C> ref) {
        return new View.ClassPropsView<>(component, props, key, ref);
    }
    /** Copies raw class input, retaining descriptor, key and ref. A null override retains
     * all input properties. Explicit undefined overrides remain own-undefined on the native
     * cloned element, then receive native defaults when the class actually renders.
     * This is distinct from replacing a field with null. Neither input is modified. */
    public static <P,S,C extends ClassComponent<P,S,?>> View.ClassPropsView<P,S,C> cloneClassProps(
            View.ClassPropsView<P,S,C> element, ClassProps<P> overrides) {
        Objects.requireNonNull(element, "element");
        return cloneClassProps(element, overrides, null, element.ref());
    }
    /** Copies raw class input with explicit ref replacement: null removes the ref.
     * Null replacementKey retains the original; a non-null key changes native identity.
     * Input omissions retain the original fields; use an undefined override to request
     * native defaults. No lifecycle or ref callbacks run during this operation. */
    public static <P,S,C extends ClassComponent<P,S,?>> View.ClassPropsView<P,S,C> cloneClassProps(
            View.ClassPropsView<P,S,C> element, ClassProps<P> overrides, String replacementKey, ClassRef<C> ref) {
        Objects.requireNonNull(element, "element");
        ClassProps<P> combined = element.overrides() == null ? ClassProps.empty(element.component().propsSchema()) : element.overrides();
        if (overrides != null) combined = combined.overlay(overrides);
        return new View.ClassPropsView<>(element.component(), element.input(),
                replacementKey == null ? element.key() : replacementKey, ref, combined);
    }
    /** Typed counterpart of React's {@code cloneElement} for a DOM element: a copy with
     * {@code extra} merged over its attributes, including key and ref, and with its children
     * replaced when any are given. Component views carry a typed Props record; build a new one
     * instead of cloning.
     * @throws IllegalArgumentException when the view is not a DOM element */
    public static View cloneElement(View element, Attributes extra, Object... children) {
        String replacement = element instanceof View.Keyed keyed ? keyed.key() : null;
        View underlying = element instanceof View.Keyed keyed ? keyed.element() : element;
        if (!(underlying instanceof View.Element original))
            throw new IllegalArgumentException("Only a DOM element view can be cloned");
        View copy = new View.Element(original.tag(), original.attributes().merge(extra),
                children.length == 0 ? original.children() : children(children));
        boolean explicitKey = extra != null && extra.entries().stream().anyMatch(entry -> entry.name().equals("key"));
        return replacement == null || explicitKey ? copy : new View.Keyed(copy, replacement);
    }
    /** React's {@code isValidElement}: true for a DOM element, fragment, component, provider or
     * element extension; false for text, empty positions, collections, portals and null. */
    public static boolean isValidElement(Object value) {
        return value instanceof View.Element || value instanceof View.Fragment || value instanceof View.ComponentView<?>
                || value instanceof View.ClassComponentView<?,?,?> || value instanceof View.ClassPropsView<?,?,?>
                || value instanceof View.Provider<?> || value instanceof View.Keyed || value instanceof View.ElementExtension;
    }
    /** A written fragment, which child utilities treat as a single element without visiting its contents. */
    public static View fragment(Object... children) { return new View.Fragment(children(children)); }

    /** The value of a component's children prop: null for no children or a single null, the child
     * itself for one child, and an array-like {@link View.Items} for several fixed sibling positions.
     * Multiple arguments retain static-child validation metadata; an iterable or array argument
     * retains dynamic list-key validation. Collections remain
     * collections even when they contain one item. A boolean becomes {@link View.Empty}.
     * Pass a single Java array as {@code (Object) array} so Java varargs does not unpack it.
     * Written fragments should be constructed with {@link #fragment}, not this method. */
    public static View childrenValue(Object... values) {
        if (values == null || values.length == 0 || values.length == 1 && values[0] == null) return null;
        List<View> normalized = children(values);
        return values.length == 1 ? normalized.getFirst() : new View.Items(normalized, true);
    }

    /** The element or native node's effective key, or null when no explicit key was supplied.
     * DOM keys use JavaScript scalar spelling, including {@code key=null} as the string
     * {@code "null"}; this differs from an absent key. Numeric keys follow the browser scalar
     * boundary, so a long outside JavaScript's exact integer range is rejected.
     * Keys are identity metadata and are not component props or rendered DOM attributes. */
    public static String key(View view) {
        return switch (view) {
            case null -> null;
            case View.Keyed keyed -> keyed.key();
            case View.ComponentView<?> component -> component.key();
            case View.ClassComponentView<?,?,?> component -> component.key();
            case View.ClassPropsView<?,?,?> component -> component.key();
            case View.Element element -> {
                String key = null;
                for (Attributes.Entry entry : element.attributes().entries())
                    if (entry.name().equals("key")) { key = ChildKeys.scalar(entry.value()); break; }
                yield key;
            }
            case View.Extension extension -> extension.key();
            default -> null;
        };
    }
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
