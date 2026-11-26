package jx.compiler;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordCodecIsolationTest {
    @TempDir Path directory;

    @Test void ignoresAutoStartPluginOnTheHostClassPath() throws Exception {
        generateWithPlugin(true);
    }

    @Test void ignoresAutoStartPluginOnTheContractClassPath() throws Exception {
        generateWithPlugin(false);
    }

    private void generateWithPlugin(boolean hostPlugin) throws Exception {
        Path plugin = write("sources/AmbientPlugin.java", """
                import com.sun.source.util.JavacTask;
                import com.sun.source.util.Plugin;
                public final class AmbientPlugin implements Plugin {
                    public String getName() { return "ambient-contract-plugin"; }
                    public boolean autoStart() { return true; }
                    public void init(JavacTask task, String... arguments) {
                        throw new AssertionError("contract codec executed an application javac plugin");
                    }
                }
                """);
        Path record = write("sources/sample/Contract.java", """
                package sample;
                public record Contract(String name, int version) {
                    static { if (System.nanoTime() != 0) throw new AssertionError("contract initialized"); }
                }
                """);
        Path probe = write("sources/CodecProbe.java", """
                import java.nio.file.Path;
                import java.util.Arrays;
                import java.util.List;
                import jx.compiler.RecordCodecGenerator;
                public final class CodecProbe {
                    public static void main(String[] args) throws Exception {
                        var sources = new RecordCodecGenerator().generate(List.of("sample.Contract"),
                            Arrays.stream(args).map(Path::of).toList());
                        if (sources.size() != 1 || !sources.getFirst().content().contains(".intValue()"))
                            throw new AssertionError("real record codec was not generated");
                    }
                }
                """);
        Path pluginClasses = directory.resolve("plugin");
        Path contractClasses = directory.resolve("contract");
        Path probeClasses = directory.resolve("probe");
        String compilerPath = Path.of(RecordCodecGenerator.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toString();
        compile(pluginClasses, plugin.toString());
        compile(contractClasses, record.toString());
        compile(probeClasses, "-classpath", compilerPath, probe.toString());
        Path services = pluginClasses.resolve("META-INF/services");
        Files.createDirectories(services);
        Files.writeString(services.resolve("com.sun.source.util.Plugin"), "AmbientPlugin\n");

        String hostPath = probeClasses + File.pathSeparator + compilerPath
                + (hostPlugin ? File.pathSeparator + pluginClasses : "");
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-classpath", hostPath, "CodecProbe", contractClasses.toString()));
        if (!hostPlugin) command.add(pluginClasses.toString());
        Path output = directory.resolve("probe.log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(Duration.ofSeconds(20).toMillis(), TimeUnit.MILLISECONDS),
                    "bounded codec generation probe timed out");
            assertEquals(0, process.exitValue(), Files.readString(output));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS), "probe did not terminate");
            }
        }
    }

    private void compile(Path output, String... arguments) throws Exception {
        Files.createDirectories(output);
        var command = new ArrayList<>(List.of("-proc:none", "--release", "25", "-d", output.toString()));
        command.addAll(List.of(arguments));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, command.toArray(String[]::new)));
    }

    private Path write(String relative, String content) throws Exception {
        Path path = directory.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
    }
}
