package jx.fixture.lazy;

import java.util.Objects;

/** The props of the lazily loaded component. Both applications compile against this record, but
 * at run time each has its own copy of the class, so a value crosses only in encoded form. */
public record Greeting(String name, int count, boolean loud, long serial) {
    public Greeting { Objects.requireNonNull(name); }
}
