package jx;

/**
 * A borrowed view of an element involved in an {@link Event}. Use it inside the
 * handler; it is not a general DOM handle and offers no mutation.
 */
public interface EventTarget {
    /** The upper-case tag name for HTML elements, for example {@code INPUT}. */
    String tagName();
    /** The element id, or an empty string. */
    String id();
    /** The form control value, or an empty string. */
    String value();
    /** Whether a checkbox or radio is checked. */
    boolean checked();
    /** An attribute value, or an empty string when absent. */
    String attribute(String name);
    /** Media elements: the playback position in seconds. */
    double currentTime();
    /** Media elements: whether playback is paused. */
    boolean paused();
}
