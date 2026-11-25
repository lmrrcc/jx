package jx;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComponentContractTest {
    @Test void aFunctionalComponentMayIntentionallyRenderNothing() {
        ComponentType<Boolean> component = new ComponentType<>("Optional", visible -> visible ? new View.Text("present") : null);
        assertNull(component.render(false));
        assertNotNull(component.render(true));
        var failure = new IllegalStateException("render failed");
        ComponentType<Void> broken = new ComponentType<>("Broken", ignored -> { throw failure; });
        assertSame(failure, assertThrows(IllegalStateException.class, () -> broken.render(null)));
    }

    @Test void memoComparesEquivalentNativeScalarsAcrossJavaBoxedTypes() {
        assertAll(
                () -> assertTrue(ComponentType.sameValue(7, 7.0)),
                () -> assertTrue(ComponentType.sameValue(7L, (short) 7)),
                () -> assertTrue(ComponentType.sameValue('x', "x")),
                () -> assertTrue(ComponentType.sameValue(Double.NaN, Float.NaN)),
                () -> assertTrue(ComponentType.sameValue(0, 0.0f)),
                () -> assertFalse(ComponentType.sameValue(0, -0.0)),
                () -> assertFalse(ComponentType.sameValue(-0.0f, 0.0)),
                () -> assertFalse(ComponentType.sameValue(0.1f, 0.1)),
                () -> assertFalse(ComponentType.sameValue(true, 1)));
    }

    @Test void inexactNativeLongsRemainExactAndNonScalarObjectsKeepIdentity() {
        BigDecimal value = new BigDecimal("1.0");
        assertAll(
                () -> assertTrue(ComponentType.sameValue(Long.MAX_VALUE, Long.valueOf(Long.MAX_VALUE))),
                () -> assertFalse(ComponentType.sameValue(Long.MAX_VALUE, Long.MAX_VALUE - 1)),
                () -> assertFalse(ComponentType.sameValue(Long.MAX_VALUE, (double) Long.MAX_VALUE)),
                () -> assertTrue(ComponentType.sameValue(value, value)),
                () -> assertFalse(ComponentType.sameValue(value, new BigDecimal("1.0"))),
                () -> assertFalse(ComponentType.sameValue(value, 1)),
                () -> assertTrue(ComponentType.sameValue(null, null)),
                () -> assertFalse(ComponentType.sameValue(null, false)));
    }
}
