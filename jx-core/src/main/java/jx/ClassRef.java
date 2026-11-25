package jx;

import java.util.Objects;
import java.util.function.Function;

/** Ref to a concrete Java class peer. Null before native attachment and after detachment.
 * The explicit decoder validates the concrete type without reflective/unchecked casts; it
 * must return the same instance. Declare the ref once, outside the component's render loop. */
public final class ClassRef<C extends ClassComponent<?,?,?>> {
    private final ClassSchema.ValueType<C> type;
    private C current;
    public ClassRef(Function<Object,C> checkedDecoder) { type = ClassSchema.reference(Objects.requireNonNull(checkedDecoder)); }
    public C current() { return current; }
    /** Renderer attachment callback; applications should not mutate the attachment. */
    public void attach(Object peer) { current = peer == null ? null : type.decode(peer); }
}
