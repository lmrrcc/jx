import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipFile;
import javax.tools.ToolProvider;

/** Compiles against the pinned official Red Hat Java extension SDK without bundling its implementation. */
public final class PrepareJavaBridge {
    private static final String SDK_URL = "https://marketplace.visualstudio.com/_apis/public/gallery/publishers/redhat/vsextensions/java/1.58.0/vspackage?targetPlatform=linux-x64";
    private static final String SDK_SHA256 = "45049a2273c78814287fb31a45255e5ce1fdbe34161bb9f1ba8f62a6aa84bdfc";
    public static void main(String[] args) throws Exception {
        String configured = System.getenv("JX_JAVA_EXTENSION");
        Path extension;
        if (configured != null && !configured.isBlank()) extension = Path.of(configured);
        else {
            Path extensions = Path.of(System.getProperty("user.home"), ".vscode", "extensions");
            extension = null;
            if (Files.isDirectory(extensions)) try (var entries = Files.list(extensions)) {
                extension = entries.filter(path -> path.getFileName().toString().startsWith("redhat.java-1.58.0"))
                    .sorted().findFirst().orElse(null);
            }
            if (extension == null) extension = sdk();
        }
        String metadata = Files.readString(extension.resolve("package.json"));
        if (!metadata.matches("(?s).*\"version\"\\s*:\\s*\"1\\.58\\.0\".*"))
            throw new IllegalArgumentException("The Java bridge SDK must be redhat.java 1.58.0");
        String classpath;
        try (var jars = Files.list(extension.resolve("server/plugins"))) {
            classpath = String.join(File.pathSeparator, jars.filter(path -> path.toString().endsWith(".jar")).sorted().map(Path::toString).toList());
        }
        Path classes = Path.of("out/java-bridge");
        // This directory is exclusively owned by this builder. Removed nested types must not survive packaging.
        if (Files.exists(classes)) try (var previous = Files.walk(classes)) {
            for (Path file : previous.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file);
        }
        Files.createDirectories(classes);
        var compiler = ToolProvider.getSystemJavaCompiler();
        int status = compiler.run(null, null, null, "--release", "25", "-classpath", classpath,
            "-d", classes.toString(), "java-bridge/src/jx/editor/JavaBridge.java");
        if (status != 0) throw new IllegalStateException("Java bridge compilation failed: " + status);
        var manifest = new Manifest();
        var attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Bundle-ManifestVersion", "2");
        attributes.putValue("Bundle-SymbolicName", "dev.jx.editor.java;singleton:=true");
        attributes.putValue("Bundle-Version", "0.2.0");
        attributes.putValue("Bundle-Name", "JX native Java editing bridge");
        attributes.putValue("Bundle-RequiredExecutionEnvironment", "JavaSE-25");
        attributes.putValue("Require-Bundle", "org.eclipse.jdt.ls.core;bundle-version=\"[1.63.0,1.64.0)\",org.eclipse.jdt.core,org.eclipse.core.runtime,org.eclipse.core.resources,org.eclipse.lsp4j,org.eclipse.lsp4j.jsonrpc");
        Files.createDirectories(Path.of("server"));
        try (var jar = new JarOutputStream(Files.newOutputStream(Path.of("server/jx-java-bridge.jar")), manifest);
             var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                var entry = new JarEntry(classes.relativize(file).toString().replace(File.separatorChar, '/'));
                entry.setTime(0);
                jar.putNextEntry(entry);
                Files.copy(file, jar);
                jar.closeEntry();
            }
            var entry = new JarEntry("plugin.xml");
            entry.setTime(0);
            jar.putNextEntry(entry);
            Files.copy(Path.of("java-bridge/plugin.xml"), jar);
            jar.closeEntry();
        }
        System.out.println("Prepared native Java bridge against Red Hat Java 1.58.0 / JDT LS 1.63.0");
    }

    /** The SDK's Java class files are platform independent; no bundled Linux runtime is extracted or shipped. */
    private static Path sdk() throws Exception {
        Path cache = Path.of("../../.cache/java-sdk").toAbsolutePath().normalize();
        Path destination = cache.resolve("redhat.java-1.58.0-linux-x64");
        Path marker = destination.resolve(".verified-sha256");
        if (Files.isRegularFile(marker) && Files.readString(marker).equals(SDK_SHA256)) return destination;
        Files.createDirectories(cache);
        Path archive = cache.resolve("redhat.java-1.58.0-linux-x64.vsix");
        if (!Files.isRegularFile(archive)) {
            Path download = Files.createTempFile(cache, "java-sdk-", ".download");
            try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build()) {
                System.out.println("Downloading pinned official Red Hat Java 1.58.0 SDK (build input only)");
                var request = HttpRequest.newBuilder(URI.create(SDK_URL)).timeout(Duration.ofMinutes(5)).GET().build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofFile(download));
                if (response.statusCode() != 200) throw new IllegalStateException("Java SDK download returned HTTP " + response.statusCode());
                try (var raw = new java.io.BufferedInputStream(Files.newInputStream(download))) {
                    raw.mark(2);
                    boolean gzip = raw.read() == 0x1f && raw.read() == 0x8b;
                    raw.reset();
                    try (InputStream body = gzip ? new GZIPInputStream(raw) : raw;
                         var output = Files.newOutputStream(archive)) {
                        byte[] bytes = new byte[64 * 1024];
                        long count = 0;
                        for (int read; (read = body.read(bytes)) >= 0;) {
                            count += read;
                            if (count > 300_000_000) throw new IllegalStateException("Java SDK exceeds the expected download bound");
                            output.write(bytes, 0, read);
                        }
                    }
                }
            } finally { Files.deleteIfExists(download); }
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(archive)) {
            byte[] bytes = new byte[64 * 1024];
            for (int read; (read = input.read(bytes)) >= 0;) digest.update(bytes, 0, read);
        }
        if (!HexFormat.of().formatHex(digest.digest()).equals(SDK_SHA256))
            throw new IllegalStateException("Java SDK checksum mismatch. Remove the unverified cache archive and retry: " + archive);
        try (var zip = new ZipFile(archive.toFile())) {
            for (var entries = zip.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement();
                if (!(entry.getName().equals("extension/package.json") || entry.getName().startsWith("extension/server/plugins/") && entry.getName().endsWith(".jar"))) continue;
                Path file = destination.resolve(entry.getName().substring("extension/".length())).normalize();
                if (!file.startsWith(destination)) throw new IllegalStateException("Invalid Java SDK archive path");
                Files.createDirectories(file.getParent());
                try (var input = zip.getInputStream(entry)) { Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING); }
            }
        }
        Files.writeString(marker, SDK_SHA256);
        return destination;
    }
}
