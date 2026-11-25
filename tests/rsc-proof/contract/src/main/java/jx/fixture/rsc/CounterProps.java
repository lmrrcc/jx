package jx.fixture.rsc;

/** One definition compiled separately into each runtime; only its native fields cross Flight. */
public record CounterProps(int initial, String label) {
    public CounterProps {
        if (initial < 0 || initial > 100 || label == null || !label.matches("[a-z]{1,32}")) {
            throw new IllegalArgumentException("Invalid counter props");
        }
    }
}
