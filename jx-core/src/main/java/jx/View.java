package jx;

import java.util.List;
import java.util.Objects;

/** An immutable description of elements. Descriptions do not invoke child components. */
public sealed interface View permits View.Element, View.Text, View.Fragment, View.ComponentView, View.Provider, View.Empty, View.Items, View.Extension {
    /** Renderer-specific, explicitly typed extension boundary; unsupported implementations fail explicitly. */
    non-sealed interface Extension extends View {}
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
    record ComponentView<P>(ComponentType<P> component, P props, String key) implements View {
        public ComponentView { Objects.requireNonNull(component); }
    }
    /** A child position that renders nothing: what a null or boolean child becomes. It keeps its
     * place among its siblings, so a sibling after it keeps its identity when it is replaced. */
    record Empty() implements View {
        public static final Empty INSTANCE = new Empty();
    }
    /** A collection given as one child, like an array child in React. It holds one position among
     * its siblings however many items it has; its items are matched by key, then by order. */
    record Items(List<View> children) implements View {
        public Items { children = List.copyOf(children); }
    }
    record Provider<T>(Context<T> context, T value, List<View> children) implements View {
        public Provider { Objects.requireNonNull(context); children = List.copyOf(children); }
    }
}
