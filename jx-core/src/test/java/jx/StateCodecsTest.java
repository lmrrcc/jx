package jx;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StateCodecsTest {
    @Test void scalarCheckpointsPreserveNullUnicodeSignedZeroAndExactIntegers() {
        assertNull(StateCodecs.strings().decode(StateCodecs.strings().encode(null)));
        assertEquals("0\njava \uD83D\uDE80", StateCodecs.strings().decode(StateCodecs.strings().encode("0\njava \uD83D\uDE80")));
        assertEquals(Long.MAX_VALUE, StateCodecs.longs().decode(StateCodecs.longs().encode(Long.MAX_VALUE)));
        assertEquals(Double.doubleToLongBits(-0.0), Double.doubleToLongBits(StateCodecs.doubles().decode(StateCodecs.doubles().encode(-0.0))));
        assertTrue(Double.isNaN(StateCodecs.doubles().decode(StateCodecs.doubles().encode(Double.NaN))));
        assertThrows(IllegalArgumentException.class, () -> StateCodecs.booleans().decode("1yes"));
        assertThrows(IllegalArgumentException.class, () -> StateCodecs.integers().decode("1invalid"));
        assertNull(StateCodecs.forInitialValue(new Object()));
        assertNull(StateCodecs.forInitialValue(null));
    }
}
