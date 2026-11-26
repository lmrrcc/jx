package jx.maven;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebJarMojoTest {
    @TempDir Path directory;

    @Test void resourceOnlyArchiveReplacesStaleAssetsAndKeepsDeterministicBytes() throws Exception {
        write("site/index.html", "<main>App</main>");
        write("site/application.js", "console.log('app');");
        write("site/images/old.svg", "old");
        write("site/application.js.map", "private source path");
        write("site/Secret.class", "bytecode");
        write("site/.env", "secret");
        Path archive = directory.resolve("app-web.jar");
        WebJarMojo.write(directory.resolve("site"), archive);
        try (var jar = new JarFile(archive.toFile())) {
            assertEquals(List.of("META-INF/resources/application.js", "META-INF/resources/images/old.svg", "META-INF/resources/index.html"),
                    jar.stream().map(entry -> entry.getName()).toList());
        }
        byte[] original = Files.readAllBytes(archive);
        WebJarMojo.write(directory.resolve("site"), archive);
        assertArrayEquals(original, Files.readAllBytes(archive));
        Files.delete(directory.resolve("site/images/old.svg"));
        WebJarMojo.write(directory.resolve("site"), archive);
        try (var jar = new JarFile(archive.toFile())) { assertNull(jar.getEntry("META-INF/resources/images/old.svg")); }
    }
    @Test void rejectsUnbuiltSitesAndLinksWithoutReplacingLastArtifact() throws Exception {
        Path archive = write("app-web.jar", "previous");
        write("site/index.html", "app");
        assertThrows(IOException.class, () -> WebJarMojo.write(directory.resolve("site"), archive));
        write("site/application.js", "app");
        Path secret = write("outside.txt", "outside");
        Files.createSymbolicLink(directory.resolve("site/linked.txt"), secret);
        assertThrows(IOException.class, () -> WebJarMojo.write(directory.resolve("site"), archive));
        assertEquals("previous", Files.readString(archive));
    }
    @Test void rejectsPosixBackslashNamesBeforeZipPathNormalization() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(java.io.File.separatorChar == '/');
        write("site/index.html", "app");
        write("site/application.js", "app");
        write("site/assets\\..\\..\\outside.txt", "must not escape resource prefix");
        Path archive = write("app-web.jar", "previous");
        assertThrows(IOException.class, () -> WebJarMojo.write(directory.resolve("site"), archive));
        assertEquals("previous", Files.readString(archive));
    }
    private Path write(String relative, String content) throws Exception {
        Path file = directory.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }
}
