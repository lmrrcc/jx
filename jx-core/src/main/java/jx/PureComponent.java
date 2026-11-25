package jx;

/** Java peer backed by actual React.PureComponent. Its declared props/state fields use React's
 * shallow Object.is comparison by default; nested Java references compare by identity. A
 * declared shouldComponentUpdate lifecycle or context policy takes precedence over shallow
 * comparison, as in React, and React emits its usual development warning. Prefer an ordinary
 * ClassComponent when authoring a custom render policy. */
public abstract class PureComponent<P,S,X> extends ClassComponent<P,S,X> {
    protected PureComponent(P props, S state) { super(props, state); }
}
