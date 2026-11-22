package jx;

import java.util.List;

/** The fields of a submitted form, as a form action receives them. A borrowed view: read it
 * inside the action. File fields are not offered; they read as absent. */
public interface FormData {
    /** The first text value of the field, or null when the form has no such field. */
    String get(String name);
    /** Every text value of the field, in document order; empty when there is none. */
    List<String> getAll(String name);
    /** Whether the form has a field of this name. */
    boolean has(String name);
}
