package jx;

/** Java peer backed by actual React.PureComponent. Its declared props/state fields use React's
 * shallow Object.is comparison; nested Java references compare by identity. Do not supply a
 * shouldComponentUpdate lifecycle for a pure descriptor. */
public abstract class PureComponent<P,S,X> extends ClassComponent<P,S,X> {
    protected PureComponent(P props, S state) { super(props, state); }
}
