package jx;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a Java-authored JX function with a public static render entry point,
 * or a directly derived {@link ClassComponent}/{@link PureComponent} with an
 * instance render method and public nested record schemas.
 * This is a browser component declaration, independent of Spring's component annotation.
 * The compiler generates a stable {@link ComponentType} or {@link ClassComponentType}
 * descriptor. Class authoring currently requires explicit Props/state records and a
 * public Props constructor; unsupported schema/lifecycle forms receive diagnostics. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Component {}
