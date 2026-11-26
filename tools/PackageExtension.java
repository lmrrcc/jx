import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Package the fixed MV3 proof from Maven-built Java and locked local WASM assets.
 * Extract the ZIP again so the browser test consumes exactly the distributable. */
public final class PackageExtension {
    private static final List<String> SITE_FILES = List.of("manifest.json", "index.html", "popup.css", "application.js");
    private static final int MAX_FILE = 8 * 1024 * 1024;
    private static final int MAX_TOTAL = 16 * 1024 * 1024;

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 0) throw new IllegalArgumentException("Usage: java tools/PackageExtension.java");
        Path root = Path.of("").toAbsolutePath().normalize();
        Path module = root.resolve("examples/mv3-app");
        Path output = safe(root, module.resolve("target/mv3"));
        Files.createDirectories(output);
        Path bundle = safe(root, output.resolve("wasm.js"));
        Path esbuild = root.resolve("node_modules/esbuild/bin/esbuild");
        if (!Files.isRegularFile(esbuild)) throw new IOException("Run npm ci first");
        var command = new java.util.ArrayList<String>();
        try (var stream = Files.newInputStream(esbuild)) {
            if (stream.read() == '#' && stream.read() == '!') command.add("node");
        }
        command.addAll(List.of(esbuild.toString(), module.resolve("wasm-entry.js").toString(),
                "--bundle", "--format=esm", "--platform=browser", "--external:./application.js", "--outfile=" + bundle));
        Process process = new ProcessBuilder(command).directory(root.toFile()).inheritIO().start();
        try {
            if (!process.waitFor(60, TimeUnit.SECONDS)) throw new IOException("WASM binding bundle timed out");
            if (process.exitValue() != 0) throw new IOException("WASM binding bundle failed");
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }

        var entries = new LinkedHashMap<String, Path>();
        for (String name : SITE_FILES) entries.put(name, module.resolve("target/site").resolve(name));
        entries.put("wasm.js", bundle);
        entries.put("brotli_wasm_bg.wasm", root.resolve("node_modules/brotli-wasm/pkg.web/brotli_wasm_bg.wasm"));
        entries.put("brotli-LICENSE.txt", root.resolve("node_modules/brotli-wasm/LICENSE"));
        entries.put("react-LICENSE.txt", root.resolve("node_modules/react/LICENSE"));
        entries.put("react-dom-LICENSE.txt", root.resolve("node_modules/react-dom/LICENSE"));
        entries.put("teavm-LICENSE.txt", module.resolve("teavm-LICENSE.txt"));
        entries.put("JX-LICENSE.txt", root.resolve("LICENSE"));
        entries.put("THIRD-PARTY-NOTICES.txt", module.resolve("THIRD-PARTY-NOTICES.txt"));
        Path archive = safe(root, module.resolve("target/jx-extension.zip"));
        int total = 0;
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (var entry : entries.entrySet()) {
                Path source = safe(root, entry.getValue());
                if (!Files.isRegularFile(source) || Files.size(source) > MAX_FILE)
                    throw new IOException("Missing/oversized extension input; run Maven package: " + source);
                byte[] bytes = Files.readAllBytes(source);
                total = Math.addExact(total, bytes.length);
                if (total > MAX_TOTAL) throw new IOException("Extension proof exceeds package budget");
                ZipEntry item = new ZipEntry(entry.getKey()); item.setTime(0);
                zip.putNextEntry(item); zip.write(bytes); zip.closeEntry();
            }
        }

        Path extracted = safe(root, module.resolve("target/mv3-extracted"));
        Files.createDirectories(extracted);
        // A removed/missing package input must not be supplied by an earlier extraction.
        try (var previous = Files.list(extracted)) {
            for (Path file : previous.toList()) {
                if (!entries.containsKey(file.getFileName().toString()) || !Files.isRegularFile(safe(root, file)))
                    throw new IOException("Unexpected file in generated extraction directory: " + file);
                Files.delete(file);
            }
        }
        var remaining = new java.util.HashSet<>(entries.keySet());
        try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                if (entry.isDirectory() || !remaining.remove(entry.getName())) throw new IOException("Unexpected archive entry");
                byte[] bytes = zip.readNBytes(MAX_FILE + 1);
                if (bytes.length > MAX_FILE) throw new IOException("Oversized archive entry");
                Files.write(safe(root, extracted.resolve(entry.getName())), bytes);
            }
        }
        if (!remaining.isEmpty()) throw new IOException("Incomplete extension archive");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive)));
        System.out.println("MV3 archive: " + archive + " SHA-256 " + hash);
        System.out.println("Extracted browser input: " + extracted);
    }

    private static Path safe(Path root, Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) throw new IOException("Path outside checkout");
        for (Path current = normalized; current != null && current.startsWith(root); current = current.getParent())
            if (Files.isSymbolicLink(current)) throw new IOException("Symlink in extension artifact path: " + current);
        return normalized;
    }
}
