package jx;

import java.util.List;

/**
 * Typed access to a React synthetic event, valid only while its handler runs.
 *
 * <p>One interface covers every event family. An accessor that does not apply to
 * the event at hand returns an empty string, zero, {@code false}, an empty list or,
 * for targets, {@code null}; it never throws. Values are read from the browser
 * event when the accessor is called, so read what you need inside the handler and
 * keep the values, not the event.
 */
public interface Event {
    /** One contact point of a touch event. */
    record Touch(int identifier, double clientX, double clientY) {}

    /** The event name without the {@code on} prefix, for example {@code click}. */
    String type();
    /** The element the event was dispatched to; never null for DOM events. */
    EventTarget target();
    /** The element whose handler is running. */
    EventTarget currentTarget();
    /** The other element of a focus or pointer transition, or null. */
    EventTarget relatedTarget();
    /** Milliseconds since the page's time origin. */
    double timeStamp();

    /** Shorthand for {@code target().value()}. */
    String targetValue();
    /** Shorthand for {@code target().checked()}. */
    boolean targetChecked();

    boolean shiftKey();
    boolean altKey();
    boolean ctrlKey();
    boolean metaKey();

    /** Keyboard: the key value, for example {@code Enter} or {@code a}. */
    String key();
    /** Keyboard: the physical key, for example {@code KeyA}. */
    String code();
    /** Keyboard: whether the key is being held down. */
    boolean repeat();

    /** Mouse and pointer: the button that changed; 0 is the main button. */
    int button();
    /** Mouse and pointer: the bit mask of buttons currently pressed. */
    int buttons();
    double clientX();
    double clientY();
    double pageX();
    double pageY();
    /** Mouse: the click count. */
    int detail();

    int pointerId();
    /** Pointer: {@code mouse}, {@code pen} or {@code touch}. */
    String pointerType();
    boolean isPrimary();
    double pressure();

    double deltaX();
    double deltaY();
    /** Wheel: 0 for pixels, 1 for lines, 2 for pages. */
    int deltaMode();

    /** Touch: all current contact points. */
    List<Touch> touches();
    /** Touch: the contact points that changed in this event. */
    List<Touch> changedTouches();

    /** Composition and input events: the text involved. */
    String data();
    /** Clipboard and drag events: the data stored under a format such as {@code text/plain}. */
    String transferData(String format);
    /** Clipboard and drag events: stores data under a format; ignored when the event carries no transfer. */
    void setTransferData(String format, String value);

    String animationName();
    /** Transition events: the CSS property that finished. */
    String propertyName();
    /** Animation and transition events: seconds the effect has been running. */
    double elapsedTime();

    void preventDefault();
    void stopPropagation();
    boolean defaultPrevented();
}
