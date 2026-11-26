package jx.example.contract;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ExactValuesTest {
    @Test void canonicalTextRoundTripsExactNumbersAndPresenceWithoutChangingScale() {
        for (var value : ExactValues.vectors()) {
            var restored = ExactValues.fromWire(value.idText(), value.amountText(), value.note(), value.extra());
            assertEquals(value, restored);
            assertEquals(value.amount().scale(), restored.amount().scale());
            assertEquals(value.amount().unscaledValue(), restored.amount().unscaledValue());
        }
        assertNotEquals(ExactValues.TextField.absent(), ExactValues.TextField.present(null));
        for (String bad : new String[] {"01", "+1", "-0", "1.0", "9007199254740993.0", "9223372036854775808"}) {
            assertThrows(IllegalArgumentException.class, () -> ExactValues.fromWire(bad, "1.0", null, ExactValues.TextField.absent()));
        }
        for (String bad : new String[] {"01.0", "+1", "1e3", "NaN", "1E+129", "1E-129", "1".repeat(129)}) {
            assertThrows(IllegalArgumentException.class, () -> ExactValues.fromWire("1", bad, null, ExactValues.TextField.absent()));
        }
    }
}
