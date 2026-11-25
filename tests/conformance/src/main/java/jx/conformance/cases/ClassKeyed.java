package jx.conformance.cases;

import java.util.List;
import java.util.Set;
import jx.*;
import jx.conformance.Bench;

/** Compiling consumer: keyed native PureComponent peers own their state and instance refs. */
public final class ClassKeyed {
    private ClassKeyed() {}
    public record Props(String id) {}
    public record Model(int count) {}
    private record Instances(Counter a, Counter b) {}
    private static final ClassSchema.Field<Props,String> ID = ClassSchema.field("id", ClassSchema.STRING, Props::id);
    private static final ClassSchema<Props> PROPS = new ClassSchema<>(List.of(ID), reader -> new Props(reader.get(ID)));
    private static final ClassSchema.Field<Model,Integer> COUNT = ClassSchema.field("count", ClassSchema.INTEGER, Model::count);
    private static final ClassSchema<Model> MODEL = new ClassSchema<>(List.of(COUNT), reader -> new Model(reader.get(COUNT)));
    private static final ClassComponentType<Props,Model,Counter> COUNTER = ClassComponentType.pure(
            "KeyedJavaClassCounter", PROPS, MODEL, Counter::new,
            Set.of(ClassComponentType.Lifecycle.MOUNT, ClassComponentType.Lifecycle.UNMOUNT));
    public static final ComponentType<Void> JX = new ComponentType<>("ClassKeyed", ignored -> render());

    public static final class Counter extends PureComponent<Props,Model,Void> {
        public Counter(Props props) { super(props, new Model(0)); }
        public void addOne() { setState((pending, nextProps) -> MODEL.patch().set(COUNT, pending.count() + 1)); }
        @Override protected void componentDidMount() { Bench.log(props().id() + ":mount"); }
        @Override protected void componentWillUnmount() { Bench.log(props().id() + ":unmount"); }
        @Override public View render() {
            Bench.log(props().id() + ":render");
            return Views.element("output", Attributes.create().attr("data-testid", "class-" + props().id().toLowerCase()),
                    props().id() + ":" + state().count());
        }
    }

    private static ClassRef<Counter> ref() {
        return new ClassRef<>(value -> {
            if (value instanceof Counter counter) return counter;
            throw new IllegalArgumentException("Expected keyed Counter instance");
        });
    }
    private static View render() {
        ClassRef<Counter> a = Hooks.useMemo(ClassKeyed::ref, Dependencies.none());
        ClassRef<Counter> b = Hooks.useMemo(ClassKeyed::ref, Dependencies.none());
        Ref<Instances> originals = Hooks.useRef(null);
        State<Boolean> reversed = Hooks.useState(false);
        State<String> inspection = Hooks.useState("uninspected");
        Hooks.useEffect(() -> {
            if (originals.current() == null) originals.set(new Instances(a.current(), b.current()));
            return null;
        }, Dependencies.none());
        View first = Views.component(COUNTER, new Props("A"), "a", a);
        View second = Views.component(COUNTER, new Props("B"), "b", b);
        return Views.element("main", Attributes.create(),
                button("Reverse classes", () -> reversed.update(previous -> !previous)),
                button("Increment A", () -> a.current().addOne()),
                button("Increment B", () -> b.current().addOne()),
                button("Inspect class refs", () -> {
                    Instances initial = originals.current();
                    inspection.set("A=" + (initial != null && a.current() == initial.a() ? "same" : "different")
                            + ";B=" + (initial != null && b.current() == initial.b() ? "same" : "different"));
                }),
                Views.element("output", Attributes.create().attr("data-testid", "class-refs"), inspection.value()),
                Views.element("section", Attributes.create().attr("data-testid", "class-order"),
                        reversed.value() ? List.of(second, first) : List.of(first, second)));
    }
    private static View button(String name, Runnable action) {
        return Views.element("button", Attributes.create().on("onClick", event -> action.run()), name);
    }
}
