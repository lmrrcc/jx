package jx;

import java.util.Objects;

/** React's committed component error information. The component stack is the native React
 * string verbatim and may be empty. It is not the Java exception stack, a mapped Java source
 * trace, or a guarantee that every Java-authored owner appears. Its contents differ across
 * development, production and minification. No native object or component instance is retained. */
public record ComponentErrorInfo(String componentStack) {
    public ComponentErrorInfo { Objects.requireNonNull(componentStack, "componentStack"); }
}
