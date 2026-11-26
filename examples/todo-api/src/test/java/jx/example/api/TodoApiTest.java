package jx.example.api;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import jx.example.contract.Todo;
import jx.example.contract.TodoApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

class TodoApiTest {
    @TempDir Path directory;

    @Test void httpValidationOptimisticConflictsAndPersistenceAcrossRestart() throws Exception {
        String database = "jdbc:h2:file:" + directory.resolve("todos") + ";DB_CLOSE_ON_EXIT=FALSE";
        String id;
        try (var client = HttpClient.newHttpClient(); var application = start(database)) {
            String base = base(application);
            assertEquals("[]", send(client, base, "GET", "/api/todos", null).body());
            var invalid = send(client, base, "POST", "/api/todos", "{\"title\":\"   \"}");
            assertEquals(400, invalid.statusCode());
            assertTrue(invalid.body().contains("fieldErrors"), invalid.body());
            var created = send(client, base, "POST", "/api/todos", "{\"title\":\"Ship Java UI\"}");
            assertEquals(201, created.statusCode(), created.body());
            id = application.getBean(TodoRepository.class).list().getFirst().id();
            assertEquals(TodoApi.item(id), created.headers().firstValue("Location").orElseThrow());
            assertEquals(new Todo(id, "Ship Java UI", false, 0),
                    application.getBean(TodoRepository.class).list().getFirst());
            assertEquals("{\"id\":\"" + id + "\",\"title\":\"Ship Java UI\",\"completed\":false,\"version\":0}", created.body());
            var updated = send(client, base, "PUT", "/api/todos/" + id, "{\"title\":\"Ship Java UI\",\"completed\":true,\"version\":0}");
            assertEquals(200, updated.statusCode(), updated.body());
            assertTrue(updated.body().contains("\"version\":1"));
            var stale = send(client, base, "PUT", "/api/todos/" + id, "{\"title\":\"Lost update\",\"completed\":false,\"version\":0}");
            assertEquals(409, stale.statusCode());
            assertEquals(415, send(client, base, "POST", "/api/todos", null).statusCode());
        }
        try (var client = HttpClient.newHttpClient(); var restarted = start(database)) {
            var rows = restarted.getBean(TodoRepository.class).list();
            assertEquals(1, rows.size());
            assertEquals("Ship Java UI", rows.getFirst().title());
            assertTrue(rows.getFirst().completed());
            assertEquals(1, rows.getFirst().version());
            assertEquals(409, send(client, base(restarted), "DELETE", "/api/todos/" + id + "?version=0", null).statusCode());
            assertEquals(204, send(client, base(restarted), "DELETE", "/api/todos/" + id + "?version=1", null).statusCode());
            assertEquals(List.of(), restarted.getBean(TodoRepository.class).list());
        }
    }

    @Test void concurrentCreatesCannotExceedTheVisibleWorkspaceCapacity() throws Exception {
        try (var client = HttpClient.newHttpClient(); var application = start("jdbc:h2:mem:capacity-test")) {
            var jdbc = application.getBean(org.springframework.jdbc.core.simple.JdbcClient.class);
            jdbc.sql("insert into todos(id,title,completed,version) select random_uuid(), 'Existing task', false, 0 from system_range(1,499)").update();
            var builder = HttpRequest.newBuilder(URI.create(base(application) + "/api/todos"))
                    .header("Content-Type", "application/json").timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"Last slot\"}"));
            var first = client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString());
            var second = client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString());
            var statuses = java.util.stream.Stream.of(first.join().statusCode(), second.join().statusCode()).sorted().toList();
            assertEquals(List.of(201, 409), statuses);
            assertEquals(500, jdbc.sql("select count(*) from todos").query(Integer.class).single());
            assertEquals(500, application.getBean(TodoRepository.class).list().size());
        }
    }

    private ConfigurableApplicationContext start(String database) {
        return SpringApplication.run(TodoApplication.class, "--server.port=0", "--spring.datasource.url=" + database,
                "--spring.main.banner-mode=off", "--logging.level.root=WARN");
    }
    private static String base(ConfigurableApplicationContext application) {
        return "http://127.0.0.1:" + application.getEnvironment().getRequiredProperty("local.server.port");
    }
    private static HttpResponse<String> send(HttpClient client, String base, String method, String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(5));
        if (body != null) request.header("Content-Type", "application/json");
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
