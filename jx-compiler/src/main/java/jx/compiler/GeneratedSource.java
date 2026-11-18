package jx.compiler;

import java.nio.file.Path;
import java.util.List;

/** A generated Java compilation unit and the authored snapshot to which all mappings refer. */
public record GeneratedSource(SourceFile source, Path relativePath, String javaSource, SourceMap sourceMap,
                              List<String> topLevelTypes) {
    public GeneratedSource { topLevelTypes = List.copyOf(topLevelTypes); }
}
