package jx;

/** Renderer-managed DOM reference. current() is null before commit and after unmount. */
public final class DomRef {
    private DomElement element;
    public DomElement current() { return element; }
    /** Renderer attachment callback. Applications should not mutate the attachment. */
    public void attach(DomElement element) { this.element = element; }
}
