package jx;

/** Mutable mounted-instance cell. Writes do not schedule rendering; avoid render-time mutation. */
public final class Ref<T> {
    private T value;
    public Ref(T initial) { value = initial; }
    public T current() { return value; }
    public void set(T value) { this.value = value; }
}
