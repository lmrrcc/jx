package jx.conformance;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** The handle that the imperative-handle case publishes instead of its input element. */
public record FieldHandle(Consumer<String> fill, Supplier<String> read) {}
