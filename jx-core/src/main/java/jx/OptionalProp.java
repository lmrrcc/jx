package jx;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a Props field that markup may omit. An omitted field receives Java's default for its
 * type: null, zero or false. Supply another default in the record's compact constructor:
 * <pre>{@code
 * public record Props(@OptionalProp String label, @OptionalProp String tone) {
 *     public Props { if (label == null) label = "default label"; }
 * }
 * }</pre>
 * Like a default parameter value in JavaScript, such a default also replaces an explicit null. */
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.SOURCE)
public @interface OptionalProp {}
