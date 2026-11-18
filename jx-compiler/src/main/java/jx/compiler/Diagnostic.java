package jx.compiler;

import java.nio.file.Path;
import java.util.Objects;

/** A diagnostic in the authored source; line and column are one-based UTF-16 positions. */
public record Diagnostic(Path source, Severity severity, String code, String message,
                         SourceSpan span, int line, int column) {
    public enum Severity { ERROR, WARNING }
    public Diagnostic {
        Objects.requireNonNull(source); Objects.requireNonNull(severity); Objects.requireNonNull(code);
        Objects.requireNonNull(message); Objects.requireNonNull(span);
        if (line < 1 || column < 1) throw new IllegalArgumentException("Invalid source position");
    }
    public static Diagnostic error(SourceFile source, String code, String message, SourceSpan span) {
        return new Diagnostic(source.path(), Severity.ERROR, code, message, span,
                source.line(span.start()), source.column(span.start()));
    }
    @Override public String toString() {
        return source + ":" + line + ":" + column + ": " + severity.name().toLowerCase(java.util.Locale.ROOT)
                + " [" + code + "] " + message;
    }
}
