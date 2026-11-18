package jx.compiler;

import java.util.List;

/** Piecewise UTF-16 map. Copied Java is mapped character-for-character; synthetic code maps to its owner. */
public record SourceMap(List<Segment> segments, int sourceLength) {
    public SourceMap { segments = List.copyOf(segments); }
    public record Segment(int generatedStart, int length, int sourceStart, boolean linear) {}
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
