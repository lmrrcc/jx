package jx;

/** A callback ref, as in React 19: called with the element when it is attached. The returned
 * cleanup runs when the element is removed or the callback changes. When the callback returns
 * null, it is called again with a null element instead. React compares callbacks by identity,
 * so keep one stable with {@code Hooks.useCallback} unless it should run on every render. */
@FunctionalInterface
public interface RefCallback { Runnable attach(DomElement element); }
