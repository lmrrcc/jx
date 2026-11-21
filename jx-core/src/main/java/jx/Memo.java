package jx;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Makes a {@link Component} skip rendering when its parent rerenders with equal props, like
 * React's {@code memo}. The compiler compares the Props fields one by one with
 * {@link ComponentType#sameValue}. To compare differently, also declare
 * {@code public static boolean arePropsEqual(Props previous, Props next)}; returning true skips
 * the render. State, context and hot updates still rerender a memoized component. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Memo {}
