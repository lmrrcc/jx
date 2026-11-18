package jx;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a Java-authored JX component with a public static render entry point.
 * This is a browser component declaration, independent of Spring's component annotation.
 * The compiler generates a stable {@link ComponentType} descriptor for the declaration. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Component {}
