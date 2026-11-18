import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Copies already-built release inputs into the VSIX staging directory. */
public final class PrepareServer {
    public static void main(String[] args) throws Exception {
        Path root = Path.of("../..").toAbsolutePath().normalize();
        Path jar = root.resolve("jx-language-server/target/jx-language-server-0.1.0-SNAPSHOT.jar");
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("Build the language server first with ./mvnw package: " + jar);
        }
        Files.createDirectories(Path.of("server"));
        Files.copy(jar, Path.of("server/jx-language-server.jar"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(root.resolve("LICENSE"), Path.of("LICENSE"), StandardCopyOption.REPLACE_EXISTING);
    }
}
