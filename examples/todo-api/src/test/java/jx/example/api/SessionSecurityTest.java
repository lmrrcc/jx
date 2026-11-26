package jx.example.api;

import jx.example.contract.SessionApi;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import static org.junit.jupiter.api.Assertions.*;

class SessionSecurityTest {
    @Test void realSessionsRotateTokensEnforceCsrfAndKeepTwoOwnersSeparate() throws Exception {
        try (var app = SpringApplication.run(TodoApplication.class, "--server.port=0", "--spring.datasource.url=jdbc:h2:mem:session-test",
                "--jx.demo.password-hash={noop}" + TestSession.PASSWORD, "--jx.demo.other-password-hash={noop}" + TestSession.PASSWORD,
                "--spring.main.banner-mode=off", "--logging.level.root=WARN")) {
            String base = "http://127.0.0.1:" + app.getEnvironment().getRequiredProperty("local.server.port");
            try (var demo = new TestSession(base); var other = new TestSession(base)) {
                var loginPage = demo.send("GET", SessionApi.LOGIN, null);
                assertEquals(200, loginPage.statusCode(), loginPage.body());
                assertTrue(loginPage.body().contains("name=\"_csrf\""));
                assertTrue(loginPage.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
                assertEquals(401, demo.send("GET", "/api/todos", null).statusCode());
                String anonymousToken = demo.csrf();
                String anonymousId = demo.cookies.getCookieStore().getCookies().getFirst().getValue();
                var invalid = demo.form(SessionApi.LOGIN, "username=demo&password=wrong&_csrf=" + TestSession.encode(anonymousToken));
                assertEquals(303, invalid.statusCode());
                assertEquals(SessionApi.LOGIN + "?error", invalid.headers().firstValue("Location").orElseThrow());
                assertEquals(401, demo.send("GET", "/api/todos", null).statusCode());
                demo.login("demo"); other.login("other");
                assertNotEquals(anonymousId, demo.cookies.getCookieStore().getCookies().getFirst().getValue());
                assertEquals(403, demo.send("POST", "/api/todos", "{\"title\":\"Missing CSRF\"}", null).statusCode());
                assertEquals(403, demo.send("POST", "/api/todos", "{\"title\":\"Stale CSRF\"}", anonymousToken).statusCode());
                assertEquals("[]", demo.send("GET", "/api/todos", null).body());
                assertEquals(201, demo.send("POST", "/api/todos", "{\"title\":\"Private demo\"}").statusCode());
                var repository = app.getBean(TodoRepository.class);
                String id = repository.list("demo").getFirst().id();
                assertEquals("[]", other.send("GET", "/api/todos", null).body());
                assertEquals(409, other.send("PUT", "/api/todos/" + id, "{\"title\":\"Stolen\",\"completed\":true,\"version\":0}").statusCode());
                assertEquals(409, other.send("DELETE", "/api/todos/" + id + "?version=0", null).statusCode());
                assertEquals("Private demo", repository.list("demo").getFirst().title());
                assertEquals(0, repository.list("demo").getFirst().version());
                String beforeLogout = demo.csrf();
                assertEquals(403, demo.form(SessionApi.LOGOUT, "").statusCode());
                assertEquals(200, demo.send("GET", "/api/todos", null).statusCode());
                assertEquals(303, demo.form(SessionApi.LOGOUT, "_csrf=" + TestSession.encode(beforeLogout)).statusCode());
                assertEquals(401, demo.send("GET", "/api/todos", null).statusCode());
                assertEquals(403, demo.send("POST", "/api/todos", "{\"title\":\"After logout\"}", beforeLogout).statusCode());
                assertEquals(1, repository.list("demo").size());
                assertEquals(200, other.send("GET", "/api/todos", null).statusCode());
            }
        }
    }
}
