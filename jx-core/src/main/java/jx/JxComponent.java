package jx;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a static Java component entry point for the JX compiler. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface JxComponent {}
