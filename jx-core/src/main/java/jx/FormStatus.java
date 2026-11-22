package jx;

/** The result of {@link Hooks#useFormStatus()}: the submission state of the enclosing form.
 * {@code data} and {@code method} describe the running submission and are null when none runs. */
public record FormStatus(boolean pending, FormData data, String method) {}
