package jx.example.api;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import jx.example.contract.SessionApi;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Actual session/filter flow over HTTP; no test authentication or disabled CSRF filters. */
final class TestSession implements AutoCloseable {
    static final String PASSWORD = "test-only-password";
    final String base;
    final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5)).build();
    TestSession(String base) { this.base = base; }
    String csrf() throws Exception {
        var response = send("GET", SessionApi.CURRENT, null, null);
        assertEquals(200, response.statusCode(), response.body());
        return JsonMapper.builder().build().readTree(response.body()).get("csrfToken").asString();
    }
    void login(String username) throws Exception {
        var response = form(SessionApi.LOGIN, "username=" + username + "&password=" + PASSWORD + "&_csrf=" + encode(csrf()));
        assertEquals(303, response.statusCode(), response.body());
        assertEquals("/todos", response.headers().firstValue("Location").orElseThrow());
    }
    HttpResponse<String> send(String method, String path, String body) throws Exception {
        return send(method, path, body, method.equals("GET") ? null : csrf());
    }
    HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(5));
        if (body != null) request.header("Content-Type", "application/json");
        if (token != null) request.header(SessionApi.CSRF_HEADER, token);
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> form(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    static String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
    @Override public void close() { client.close(); }
}
