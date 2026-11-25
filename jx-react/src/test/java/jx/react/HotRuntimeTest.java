package jx.react;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HotRuntimeTest {
    @Test void protocolAttestationDoesNotInitializeTheBrowserRuntime() {
        // HotReload/React initialization requires native browser methods, unavailable on this JVM.
        assertEquals(2, HotRuntime.version());
    }
}
