package jx.react;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StreamOptionsTest {
    @Test void immutableCopiesRetainSecuritySettingsAndLegacyConstruction() {
        var urls = new ArrayList<>(List.of("/app.js"));
        var legacy = new ReactServer.StreamOptions("old", urls, null, null);
        urls.clear();
        assertEquals(List.of("/app.js"), legacy.bootstrapScripts());
        assertNull(legacy.nonce());
        assertNull(legacy.bootstrapScriptContent());
        var options = legacy.withNonces(new ReactServer.Nonces("script", "style"))
                .withBootstrapScriptContent("").withIdentifierPrefix("new")
                .withBootstrapScripts(List.of("/new.js")).withBootstrapModules(List.of("/new.mjs"))
                .withError(error -> {});
        assertEquals(new ReactServer.Nonces("script", "style"), options.nonce());
        assertEquals("", options.bootstrapScriptContent());
        assertThrows(UnsupportedOperationException.class, () -> options.bootstrapScripts().clear());
        assertNull(options.withNonce(null).nonce());
        assertEquals(new ReactServer.ScriptNonce(""), options.withNonce("").nonce());
        assertThrows(IllegalArgumentException.class, () -> new ReactServer.Nonces(null, null));
    }

    @Test void staticRenderingRejectsRequestNoncesBeforeEnteringNativeRuntime() {
        var options = ReactServer.StreamOptions.defaults().withNonce("request");
        assertThrows(IllegalArgumentException.class, () -> ReactStatic.prerender(null, options));
        assertThrows(IllegalArgumentException.class, () -> ReactStatic.prerenderToNodeStream(null, options));
        assertThrows(IllegalArgumentException.class, () -> ReactStatic.resumeAndPrerender(null, null, options));
        assertThrows(IllegalArgumentException.class, () -> ReactStatic.resumeAndPrerenderToNodeStream(null, null, options));
        assertDoesNotThrow(() -> ReactStatic.validateStaticOptions(options.withNonce(null)));
    }
}
