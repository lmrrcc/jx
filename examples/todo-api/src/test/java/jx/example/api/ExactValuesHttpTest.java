package jx.example.api;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import jx.example.contract.ExactValues;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import tools.jackson.databind.json.JsonMapper;

class ExactValuesHttpTest {
    @Test void realSpringRoundTripsStringNumbersAndDistinguishesMissingFromPresentNull() throws Exception {
        try (var app = SpringApplication.run(TodoApplication.class, "--server.port=0", "--spring.profiles.active=contract-proof",
                "--spring.datasource.url=jdbc:h2:mem:contract-proof", "--spring.main.banner-mode=off", "--logging.level.root=WARN");
             var client = HttpClient.newHttpClient()) {
            URI uri = URI.create("http://127.0.0.1:" + app.getEnvironment().getRequiredProperty("local.server.port") + ExactValues.PATH);
            var mapper = JsonMapper.builder().build();
            for (var value : ExactValues.vectors()) {
                String wire = ExactValuesController.encode(value).toString();
                var response = send(client, uri, wire);
                assertEquals(200, response.statusCode(), response.body());
                assertEquals(wire, response.body());
                assertEquals(value, ExactValuesController.decode(mapper.readTree(response.body())));
            }
            for (String invalid : new String[] {
                    "{\"id\":1,\"amount\":\"1.0\",\"note\":null}",
                    "{\"id\":\"1\",\"amount\":1.0,\"note\":null}",
                    "{\"id\":\"1\",\"amount\":\"1.0\"}",
                    "{\"id\":\"01\",\"amount\":\"1.0\",\"note\":null}",
                    "{\"id\":\"1\",\"amount\":\"1.0\",\"note\":null,\"extra\":false}"
            }) assertEquals(400, send(client, uri, invalid).statusCode(), invalid);
        }
    }
    private static HttpResponse<String> send(HttpClient client, URI uri, String json) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
