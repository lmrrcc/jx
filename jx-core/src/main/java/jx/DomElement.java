package jx;

/** Borrowed browser DOM handle. Retain only while its ref is attached. */
public interface DomElement {
    void focus();
    /** The element's form value, or an empty string when it has none. */
    String value();
    /** Sets the form value directly. React does not see this as a change of a controlled input. */
    void setValue(String value);
    /** The upper-case tag name for HTML elements, such as {@code INPUT}. */
    String tagName();
    /** The text of the element and its descendants as currently committed to the DOM. */
    String textContent();
}
