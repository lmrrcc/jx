package jx.elm;

/** Delivers a typed message on the owning browser/event-loop thread. */
@FunctionalInterface
public interface Dispatch<M> { void send(M message); }
