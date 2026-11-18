package jx.lsp;

import java.util.List;
import jx.compiler.SourceMap;

/** Native Java delegation protocol. Every offset is UTF-16; only linear segments authorize edits. */
public record EditorProjectionResult(int protocolVersion, String projectUri, List<File> files, List<String> unavailable) {
    public EditorProjectionResult { files = List.copyOf(files); unavailable = List.copyOf(unavailable); }
    public record File(String sourceUri, Integer version, String sourceText, String relativeJavaPath,
                       String javaText, List<SourceMap.Segment> segments, int sourceLength, List<String> topLevelTypes) {
        public File { segments = List.copyOf(segments); topLevelTypes = List.copyOf(topLevelTypes); }
    }
}
