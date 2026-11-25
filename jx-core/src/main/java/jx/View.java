package jx;

import java.util.List;
import java.util.Objects;

/** An immutable description of elements. Descriptions do not invoke child components. */
public sealed interface View permits View.Element, View.Text, View.Fragment, View.ComponentView, View.ClassComponentView, View.Provider, View.Empty, View.Items, View.Keyed, View.Extension {
    /** Renderer-specific, explicitly typed extension boundary; unsupported implementations fail explicitly. */
    non-sealed interface Extension extends View {
        /** The native node's explicit reconciliation key, or null when absent. An extension is
         * opaque to child traversal; being a node alone does not make it a valid element. */
        default String key() { return null; }
    }
    /** A renderer extension representing a valid element, as distinct from an opaque node such
     * as a portal. A renderer must support replacing its key without adding a wrapper element. */
    interface ElementExtension extends Extension {}
    record Element(String tag, Attributes attributes, List<View> children) implements View {
        public Element {
            if (!Objects.requireNonNull(tag).matches("[A-Za-z][A-Za-z0-9:_-]*")) throw new IllegalArgumentException("Invalid element name: " + tag);
            Objects.requireNonNull(attributes); children = List.copyOf(children);
        }
    }
    record Text(String value) implements View {
        public Text { Objects.requireNonNull(value); }
    }
    record Fragment(List<View> children) implements View {
        public Fragment { children = List.copyOf(children); }
    }
    /** A replacement reconciliation key on an existing element. This is metadata, not another
     * element: renderers must preserve the underlying type, props, refs and children. Nested
     * replacements collapse to the original description and the newest key.
     * @throws IllegalArgumentException if element is text, empty, a collection or an opaque node */
    record Keyed(View element, String key) implements View {
        public Keyed {
            Objects.requireNonNull(element); Objects.requireNonNull(key);
            if (!Views.isValidElement(element)) throw new IllegalArgumentException("Only an element can receive a replacement key");
            while (element instanceof Keyed previous) element = previous.element();
        }
    }
    record ComponentView<P>(ComponentType<P> component, P props, String key) implements View {
        public ComponentView { Objects.requireNonNull(component); }
    }
    record ClassComponentView<P,S,C extends ClassComponent<P,S,?>>(ClassComponentType<P,S,C> component,
            P props, String key, ClassRef<C> ref) implements View {
        public ClassComponentView { Objects.requireNonNull(component).propsSchema().validate(props); }
    }
    /** A child position that renders nothing: what a null or boolean child becomes. It keeps its
     * place among its siblings, so a sibling after it keeps its identity when it is replaced. */
    record Empty() implements View {
        public static final Empty INSTANCE = new Empty();
    }
    /** A collection given as one child, like an array child in React. It holds one position among
     * its siblings however many items it has; its items are matched by key, then by order.
     * {@code staticChildren} identifies individually authored sibling positions, whose implicit
     * keys React can validate as fixed. It must remain false for dynamic collections and mapped
     * results, which still require the usual list-key validation. This flag changes validation
     * metadata only; it does not add a fragment or change reconciliation keys. */
    record Items(List<View> children, boolean staticChildren) implements View {
        public Items { children = List.copyOf(children); }
        /** A dynamic collection, preserving React's validation of explicit list keys. */
        public Items(List<View> children) { this(children, false); }
    }
    record Provider<T>(Context<T> context, T value, List<View> children) implements View {
        public Provider { Objects.requireNonNull(context); children = List.copyOf(children); }
    }
}
