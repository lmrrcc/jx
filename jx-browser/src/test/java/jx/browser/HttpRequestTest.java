package jx.browser;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HttpRequestTest {
    @Test void applicationHeadersAreOwnedBoundedAndCannotImpersonateBrowserHeaders() {
        var headers = new ArrayList<>(List.of(new Http.Header("X-CSRF-TOKEN", "masked")));
        var request = new Http.Request(Http.Method.GET, "/api/session", null, 1000, 1024, headers);
        headers.clear();
        assertEquals(1, request.headers().size());
        assertFalse(request.toString().contains("masked"));
        assertThrows(UnsupportedOperationException.class, () -> request.headers().clear());
        assertThrows(IllegalArgumentException.class, () -> request.withHeader("x-csrf-token", "other"));
        for (String name : List.of("Cookie", "Origin", "Host", "Sec-Fetch-Site", "Proxy-Authorization", "Content-Type", "X-HTTP-Method-Override")) {
            assertThrows(IllegalArgumentException.class, () -> new Http.Header(name, "anything"), name);
        }
        assertThrows(IllegalArgumentException.class, () -> new Http.Header("X-Token", "value\r\nCookie: secret"));
        assertThrows(IllegalArgumentException.class, () -> new Http.Header("X-Token", "x".repeat(8193)));
        var tooLarge = List.of(new Http.Header("A", "x".repeat(8192)), new Http.Header("B", "x".repeat(8192)),
                new Http.Header("C", "x".repeat(8192)), new Http.Header("D", "x".repeat(8192)));
        assertThrows(IllegalArgumentException.class, () -> new Http.Request(Http.Method.GET, "/", null, 1000, 1024, tooLarge));
    }
}
