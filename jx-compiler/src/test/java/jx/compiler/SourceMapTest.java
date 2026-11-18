package jx.compiler;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class SourceMapTest {
    @Test void onlyAuthoredJavaRangesCanBecomeEditsIncludingReorderedExpressions() {
        var map = new SourceMap(List.of(
                new SourceMap.Segment(0, 4, 0, true),
                new SourceMap.Segment(4, 10, 4, false),
                new SourceMap.Segment(14, 3, 20, true),
                new SourceMap.Segment(17, 2, 12, false),
                new SourceMap.Segment(19, 5, 8, true)), 30);
        assertEquals(15, map.generatedOffset(21).orElseThrow());
        assertEquals(20, map.generatedOffset(9).orElseThrow());
        assertTrue(map.generatedOffset(6).isEmpty());
        assertTrue(map.generatedOffset(-1).isEmpty());
        assertTrue(map.generatedOffset(31).isEmpty());
        assertEquals(new SourceSpan(9, 12), map.editableSourceSpan(new SourceSpan(20, 23)).orElseThrow());
        assertEquals(new SourceSpan(23, 23), map.editableSourceSpan(new SourceSpan(17, 17)).orElseThrow());
        assertTrue(map.editableSourceSpan(new SourceSpan(4, 5)).isEmpty(), "Diagnostic anchors cannot authorize edits");
        assertTrue(map.editableSourceSpan(new SourceSpan(3, 15)).isEmpty(), "An edit cannot cross synthetic glue");
        assertTrue(map.editableSourceSpan(new SourceSpan(17, 20)).isEmpty(), "An edit cannot cross reordered fragments");
        assertTrue(map.editableSourceSpan(new SourceSpan(25, 25)).isEmpty());
    }
}
