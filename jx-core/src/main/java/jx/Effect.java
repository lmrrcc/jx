package jx;

/** Setup runs after commit (before paint for layout effects), and returns owned cleanup.
 * Setup/cleanup must tolerate React development remounts. */
@FunctionalInterface
public interface Effect { Runnable setup(); }
