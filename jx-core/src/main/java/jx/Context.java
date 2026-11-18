package jx;

/** A stable scoped dependency descriptor; create once outside component rendering. */
public final class Context<T> {
    private final T defaultValue;
    public Context(T defaultValue) { this.defaultValue = defaultValue; }
    public T defaultValue() { return defaultValue; }
    public View provide(T value, Object... children) { return new View.Provider<>(this, value, Views.children(children)); }
}
