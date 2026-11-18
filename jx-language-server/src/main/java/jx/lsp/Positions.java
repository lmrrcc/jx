package jx.lsp;

import jx.compiler.SourceFile;
import jx.compiler.SourceSpan;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

/** LSP UTF-16 positions, including CRLF and surrogate pairs, without encoding conversion. */
final class Positions {
    private Positions() {}

    static Position position(String text, int offset) {
        int end = Math.clamp(offset, 0, text.length());
        int line = 0, start = 0;
        for (int i = 0; i < end; i++) {
            if (text.charAt(i) == '\n') { line++; start = i + 1; }
        }
        return new Position(line, end - start);
    }

    static int offset(String text, Position position) {
        int line = 0, start = 0;
        while (line < position.getLine() && start < text.length()) {
            int next = text.indexOf('\n', start);
            if (next < 0) return text.length();
            start = next + 1;
            line++;
        }
        int end = text.indexOf('\n', start);
        if (end < 0) end = text.length();
        if (end > start && text.charAt(end - 1) == '\r') end--;
        return Math.min(end, start + Math.max(0, position.getCharacter()));
    }

    static Range range(SourceFile source, SourceSpan span) {
        return new Range(position(source.content(), span.start()), position(source.content(), span.end()));
    }
}
