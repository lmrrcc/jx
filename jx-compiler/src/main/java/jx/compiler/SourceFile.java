package jx.compiler;

import java.nio.file.Path;
import java.util.Objects;

/** An immutable UTF-16 source snapshot. Paths identify documents; compilation does not read them. */
public record SourceFile(Path path, String content) {
    public SourceFile {
        path = Objects.requireNonNull(path).normalize();
        Objects.requireNonNull(content);
    }
    /** One-based line at a clamped UTF-16 offset. */
    public int line(int offset) {
        int result = 1;
        for (int i = 0; i < Math.min(offset, content.length()); i++) if (content.charAt(i) == '\n') result++;
        return result;
    }
    /** One-based UTF-16 column at a clamped offset, matching javac and LSP UTF-16 positions. */
    public int column(int offset) {
        int end = Math.clamp(offset, 0, content.length());
        int newline = content.lastIndexOf('\n', end - 1);
        return end - newline;
    }
}
