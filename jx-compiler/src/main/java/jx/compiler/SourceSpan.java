package jx.compiler;

/** Half-open UTF-16 offsets in the original source snapshot. */
public record SourceSpan(int start, int end) {
    public SourceSpan {
        if (start < 0 || end < start) throw new IllegalArgumentException("Invalid source span");
    }
    public boolean contains(int offset) { return start <= offset && offset < end; }
}
