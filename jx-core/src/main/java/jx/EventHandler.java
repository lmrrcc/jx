package jx;

/** Synchronous browser event callback; event properties should be read before asynchronous work. */
@FunctionalInterface
public interface EventHandler { void handle(Event event); }
