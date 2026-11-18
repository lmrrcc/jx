package jx.maven;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DevelopmentToolsTest {
    @TempDir Path root;

    @Test void servesOnlyLoopbackAssetsWithReloadStatusAndBlocksTraversal() throws Exception {
        Path assets = root.resolve("site");
        Files.createDirectories(assets);
        Files.writeString(assets.resolve("index.html"), "<!doctype html><body>Java application</body>");
        Path privateFile = root.resolve("private.txt");
        Files.writeString(privateFile, "not a web asset");
        Files.createSymbolicLink(assets.resolve("escape.txt"), privateFile);
        var status = new AtomicReference<>(new DevServer.Status(1, "ready"));
        try (var server = new DevServer(assets, 0, status); var client = HttpClient.newHttpClient()) {
            String url = "http://127.0.0.1:" + server.port();
            var index = get(client, url + "/");
            assertEquals(200, index.statusCode());
            assertTrue(index.body().contains("Java application"));
            assertTrue(index.body().contains("let version = 1;"));
            assertTrue(index.body().contains("/__jx_dev/version"));
            assertEquals("no-store", index.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(200, get(client, url + "/todos/42").statusCode());
            assertEquals(403, get(client, url + "/%2e%2e/private.txt").statusCode());
            assertEquals(403, get(client, url + "/escape.txt").statusCode());
            assertEquals(404, get(client, url + "/missing.js").statusCode());
            status.set(new DevServer.Status(2, "failed"));
            assertEquals("{\"version\":2,\"state\":\"failed\"}", get(client, url + "/__jx_dev/version").body());
        }
    }

    @Test void fingerprintDetectsSameSizeAndTimestampEditsAndDeletion() throws Exception {
        Path source = root.resolve("App.jx");
        Files.writeString(source, "first");
        FileTime timestamp = Files.getLastModifiedTime(source);
        String before = DevMojo.fingerprint(List.of(root));
        Files.writeString(source, "other");
        Files.setLastModifiedTime(source, timestamp);
        String edited = DevMojo.fingerprint(List.of(root));
        assertNotEquals(before, edited);
        Files.delete(source);
        assertNotEquals(edited, DevMojo.fingerprint(List.of(root)));
    }

    @Test void browserAssetsTrackDeletionAndPreserveUnownedFiles() throws Exception {
        Path input = root.resolve("web"), output = root.resolve("site"), manifest = root.resolve("metadata/assets.txt");
        Files.createDirectories(input);
        Files.createDirectories(output);
        Files.writeString(input.resolve("index.html"), "<body>JX</body>");
        Files.writeString(input.resolve("style.css"), "body { color: navy; }");
        Files.writeString(output.resolve("keep.txt"), "unrelated");
        BrowserMojo.copyAssets(input, output, manifest);
        assertTrue(Files.exists(output.resolve("style.css")));
        Files.delete(input.resolve("style.css"));
        BrowserMojo.copyAssets(input, output, manifest);
        assertFalse(Files.exists(output.resolve("style.css")));
        assertEquals("unrelated", Files.readString(output.resolve("keep.txt")));
        Files.writeString(input.resolve("keep.txt"), "cannot overwrite");
        assertThrows(java.io.IOException.class, () -> BrowserMojo.copyAssets(input, output, manifest));
        assertEquals("unrelated", Files.readString(output.resolve("keep.txt")));
    }

    private static HttpResponse<String> get(HttpClient client, String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
