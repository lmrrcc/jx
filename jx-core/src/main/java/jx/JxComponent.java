package jx;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Legacy spelling of {@link Component}, retained for existing source compatibility.
 * @deprecated Use {@code @jx.Component}; generated descriptors use {@link ComponentType}. */
@Deprecated(since = "0.1.0", forRemoval = false)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface JxComponent {}
