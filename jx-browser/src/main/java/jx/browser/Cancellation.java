package jx.browser;

/** Idempotently cancels an owned browser operation; cancellation suppresses its completion callback. */
@FunctionalInterface
public interface Cancellation { void cancel(); }
