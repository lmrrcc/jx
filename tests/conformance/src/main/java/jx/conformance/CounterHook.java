package jx.conformance;

import jx.Dependencies;
import jx.Hooks;
import jx.State;

/** A custom hook for the custom-hook case: an ordinary method composed from built-in hooks. */
public final class CounterHook {
    private CounterHook() {}

    /** The value of one hook call site and the action that advances it. */
    public record Counter(int value, Runnable advance) {}

    /** Counts in steps of {@code step} and logs each committed value under {@code name}. */
    public static Counter useCounter(String name, int step) {
        State<Integer> value = Hooks.useState(0);
        int current = value.value();
        Hooks.useEffect(() -> {
            Bench.log(name + " " + current);
            return null;
        }, Dependencies.of(current));
        return new Counter(current, () -> value.update(previous -> previous + step));
    }
}
