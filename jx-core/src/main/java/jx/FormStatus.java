package jx;

/** The result of {@link Hooks#useFormStatus()}: the submission state of the enclosing form.
 * {@code data}, {@code method}, {@code action} and {@code actionUrl} describe the running submission
 * and are null when none runs. The action is the submitted function, including
 * a button's override or an action-state dispatcher. When React reports a URL
 * during an onSubmit transition, {@code actionUrl} retains it and {@code action}
 * is null. At most one action alternative is non-null; an empty URL is valid.
 * Java-authored actions retain identity. A native React action is exposed
 * as a stable typed handle that can be passed back to a form unchanged.
 */
public record FormStatus(boolean pending, FormData data, String method, FormAction action, String actionUrl) {
    public FormStatus {
        if (action != null && actionUrl != null) throw new IllegalArgumentException("A submission cannot have both a function and a URL action");
    }
    /** Compatibility constructor for runtimes that do not expose the action. */
    public FormStatus(boolean pending, FormData data, String method) {
        this(pending, data, method, null, null);
    }
    /** A submission with a function action and no URL alternative. */
    public FormStatus(boolean pending, FormData data, String method, FormAction action) {
        this(pending, data, method, action, null);
    }
}
