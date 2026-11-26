package jx.react;

import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceHeadersTest {
    @Test void optionCopiesRetainHeaderOwnershipAndExplicitBudget() {
        Consumer<ReactServer.ResourceHeaders> listener = headers -> {};
        var options = ReactServer.StreamOptions.defaults().withHeaders(listener).withMaxHeadersLength(73)
                .withIdentifierPrefix("id").withBootstrapScripts(List.of("/main.js"))
                .withBootstrapModules(List.of("/main.mjs")).withError(error -> {})
                .withBootstrapScriptContent("").withNonce("script")
                .withNonces(new ReactServer.Nonces("script", "style"));
        assertSame(listener, options.onHeaders());
        assertEquals(73, options.maxHeadersLength());
        assertNull(options.withHeaders(null).onHeaders());
        assertNull(options.withMaxHeadersLength(null).maxHeadersLength());
        assertThrows(IllegalArgumentException.class, () -> options.withMaxHeadersLength(0));
        assertThrows(IllegalArgumentException.class, () -> options.withMaxHeadersLength(-1));
        var previous = new ReactServer.StreamOptions("id", null, null, null, "", null);
        assertNull(previous.onHeaders());
        assertNull(previous.maxHeadersLength());
    }

    @Test void headerValueRejectsMessageInjectionWithoutParsingLinkSyntax() {
        assertNull(new ReactServer.ResourceHeaders(null).link());
        String link = "<https://example.test/a,b>; rel=preload; as=font";
        assertEquals(link, new ReactServer.ResourceHeaders(link).link());
        assertEquals("", new ReactServer.ResourceHeaders("").link());
        for (String invalid : List.of("a\r\nX-Injected: yes", "a\u0000b", "a\u007fb"))
            assertThrows(IllegalArgumentException.class, () -> new ReactServer.ResourceHeaders(invalid));
    }

    @Test void resumeRejectsInitialHeaderOptionsBeforeEnteringTheNativeRuntime() {
        for (var options : List.of(ReactServer.StreamOptions.defaults().withHeaders(headers -> {}),
                ReactServer.StreamOptions.defaults().withMaxHeadersLength(10))) {
            assertThrows(IllegalArgumentException.class, () -> ReactServer.resume(null, null, options));
            assertThrows(IllegalArgumentException.class, () -> ReactServer.resumeToPipeableStream(null, null, options));
            assertThrows(IllegalArgumentException.class, () -> ReactStatic.resumeAndPrerender(null, null, options));
            assertThrows(IllegalArgumentException.class, () -> ReactStatic.resumeAndPrerenderToNodeStream(null, null, options));
        }
    }
}
