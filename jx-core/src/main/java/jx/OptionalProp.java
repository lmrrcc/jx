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
 * This Java constructor convention also replaces an explicit null; it is distinct from
 * JavaScript's defaulting of undefined values.
 *
 * <p>For an authored class component declaring {@code public static final ClassProps<Props>
 * defaultProps}, this annotation instead permits the input field to be absent. The native
 * React default must supply a defined value for every such field (null is defined for a
 * nullable value type). Markup omits the field instead of constructing a Java default value;
 * explicit null/zero/false values remain inputs. Classes without native defaultProps retain
 * the ordinary Java constructor convention above. */
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.SOURCE)
public @interface OptionalProp {}
