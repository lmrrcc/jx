package jx.compiler;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** Piecewise UTF-16 map. Copied Java is mapped character-for-character; synthetic code maps to its owner. */
public record SourceMap(List<Segment> segments, int sourceLength) {
    public SourceMap { segments = List.copyOf(segments); }
    public record Segment(int generatedStart, int length, int sourceStart, boolean linear) {}

    /** Exact authored-to-Java location for native editor services. Markup/synthetic text has no match. */
    public OptionalInt generatedOffset(int sourceOffset) {
        if (sourceOffset < 0 || sourceOffset > sourceLength) return OptionalInt.empty();
        Segment boundary = null;
        for (Segment segment : segments) {
            if (!segment.linear()) continue;
            int relative = sourceOffset - segment.sourceStart();
            if (relative >= 0 && relative < segment.length()) return OptionalInt.of(segment.generatedStart() + relative);
            if (relative == segment.length()) boundary = segment;
        }
        return boundary == null ? OptionalInt.empty() : OptionalInt.of(boundary.generatedStart() + boundary.length());
    }

    /** Map an editor edit only when every character belongs to one unchanged authored Java slice.
     * Diagnostic anchoring via sourceOffset must never be used to authorize source edits. */
    public Optional<SourceSpan> editableSourceSpan(SourceSpan generated) {
        for (Segment segment : segments) {
            if (!segment.linear() || generated.start() < segment.generatedStart()
                    || generated.end() > segment.generatedStart() + segment.length()) continue;
            int start = segment.sourceStart() + generated.start() - segment.generatedStart();
            int end = start + generated.end() - generated.start();
            if (start >= 0 && end <= sourceLength) return Optional.of(new SourceSpan(start, end));
        }
        return Optional.empty();
    }
    public int sourceOffset(long generatedOffset) {
        int offset = (int) Math.clamp(generatedOffset, 0, Integer.MAX_VALUE);
        int low = 0, high = segments.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            Segment segment = segments.get(mid);
            if (offset < segment.generatedStart()) high = mid - 1;
            else if (offset >= segment.generatedStart() + segment.length()) low = mid + 1;
            else return Math.min(sourceLength, segment.sourceStart() + (segment.linear() ? offset - segment.generatedStart() : 0));
        }
        return sourceLength;
    }
    /** Stable text interchange format: generated offset, length, source offset, linear flag. */
    public String serialize() {
        StringBuilder out = new StringBuilder("jx-map-v1\t").append(sourceLength).append('\n');
        for (Segment segment : segments) out.append(segment.generatedStart()).append('\t')
                .append(segment.length()).append('\t').append(segment.sourceStart()).append('\t')
                .append(segment.linear()).append('\n');
        return out.toString();
    }
}
