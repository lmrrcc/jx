import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.jar.JarInputStream;
import java.util.zip.ZipFile;

/** Checks that editor packages contain the current shared server and their native Java adapters. */
public final class VerifyPackages {
    private static final int MAX_JAR_BYTES = 64 * 1024 * 1024;
    public static void main(String[] args) throws Exception {
        if (args.length > 1 || args.length == 1 && !args[0].equals("--intellij-only"))
            throw new IllegalArgumentException("Usage: VerifyPackages.java [--intellij-only]");
        boolean intellijOnly = args.length == 1;
        Path server = Path.of("jx-language-server/target/jx-language-server-0.1.0-SNAPSHOT.jar");
        Path compiler = Path.of("jx-compiler/target/jx-compiler-0.1.0-SNAPSHOT.jar");
        Path bridge = Path.of("tooling/vscode/server/jx-java-bridge.jar");
        byte[] expected = Files.readAllBytes(server);
        Path vscode = Path.of("tooling/vscode/jx-language-0.2.0.vsix");
        if (!intellijOnly) try (var zip = new ZipFile(vscode.toFile())) {
            var entry = zip.getEntry("extension/server/jx-language-server.jar");
            if (entry == null) throw new IOException("VSIX is missing the shared language server");
            try (var input = zip.getInputStream(entry)) { equal(expected, bounded(input), "VSIX"); }
            var javaBridge = zip.getEntry("extension/server/jx-java-bridge.jar");
            if (javaBridge == null) throw new IOException("VSIX is missing the native Java bridge");
            try (var input = zip.getInputStream(javaBridge)) {
                equal(Files.readAllBytes(bridge), bounded(input), "VSIX Java bridge");
            }
        }
        Path intellij = Path.of("tooling/intellij/build/distributions/jx-intellij-0.3.2.zip");
        int found = 0, compilers = 0;
        try (var zip = new ZipFile(intellij.toFile())) {
            for (var entry : zip.stream().filter(item -> item.getName().endsWith(".jar")).toList()) {
                byte[] contents;
                try (var input = zip.getInputStream(entry)) { contents = bounded(input); }
                if (entry.getName().endsWith("/" + compiler.getFileName())) {
                    equal(Files.readAllBytes(compiler), contents, "IntelliJ compiler"); compilers++;
                }
                try (var jar = new JarInputStream(new ByteArrayInputStream(contents))) {
                    for (var resource = jar.getNextJarEntry(); resource != null; resource = jar.getNextJarEntry()) {
                        if (resource.getName().equals("server/jx-language-server.jar")) {
                            equal(expected, bounded(jar), "IntelliJ ZIP"); found++;
                        }
                    }
                }
            }
        }
        if (found != 1) throw new IOException("Expected exactly one bundled IntelliJ server; found " + found);
        if (compilers != 1) throw new IOException("Expected exactly one bundled IntelliJ compiler; found " + compilers);
        System.out.println(intellijOnly
                ? "PASS: IntelliJ package bundles the current language server and compiler byte-for-byte."
                : "PASS: editor packages bundle the current language server, compiler and Java bridge byte-for-byte.");
        for (Path file : intellijOnly ? new Path[] {server, compiler, intellij} : new Path[] {server, compiler, bridge, vscode, intellij}) {
            System.out.println(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))) + "  " + file);
        }
    }
    private static byte[] bounded(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_JAR_BYTES + 1);
        if (bytes.length > MAX_JAR_BYTES) throw new IOException("Editor JAR exceeds verification size limit");
        return bytes;
    }
    private static void equal(byte[] expected, byte[] actual, String name) throws IOException {
        if (!Arrays.equals(expected, actual)) throw new IOException(name + " contains a stale or altered artifact; rebuild it");
    }
}
