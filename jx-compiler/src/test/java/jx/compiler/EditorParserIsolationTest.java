package jx.compiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Syntax projection must not discover executable javac plugins from its host's classpath. */
class EditorParserIsolationTest {
    @TempDir Path temporary;

    @Test void ambientAutoStartPluginCannotRunDuringProjection() throws Exception {
        Path plugin = temporary.resolve("AmbientPlugin.java");
        Files.writeString(plugin, """
                import com.sun.source.util.JavacTask;
                import com.sun.source.util.Plugin;
                public final class AmbientPlugin implements Plugin {
                    public String getName() { return "ambient"; }
                    public boolean autoStart() { return true; }
                    public void init(JavacTask task, String... arguments) {
                        throw new AssertionError("ambient javac plugin executed by syntax projection");
                    }
                }
                """);
        Path probe = temporary.resolve("ParserProbe.java");
        Files.writeString(probe, """
                import java.nio.file.Path;
                import java.util.List;
                import jx.compiler.EditorProjection;
                import jx.compiler.SourceFile;
                public final class ParserProbe {
                    public static void main(String[] args) {
                        var sources = EditorProjection.project(List.of(new SourceFile(Path.of("Counter.jx"),
                            "@jx.Component public class Counter { public static jx.View render() { return <div>safe</div>; } }")));
                        if (sources.size() != 1 || !sources.getFirst().javaSource().contains("safe"))
                            throw new AssertionError("projection was not produced");
                    }
                }
                """);
        String compilerPath = Path.of(EditorProjection.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-proc:none", "-cp", compilerPath,
                "-d", temporary.toString(), plugin.toString(), probe.toString()));
        Path services = temporary.resolve("META-INF/services");
        Files.createDirectories(services);
        Files.writeString(services.resolve("com.sun.source.util.Plugin"), "AmbientPlugin\n");
        Path output = temporary.resolve("probe.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", temporary + java.io.File.pathSeparator + compilerPath, "ParserProbe")
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(Duration.ofSeconds(20).toMillis(), TimeUnit.MILLISECONDS), "bounded projection probe timed out");
            assertEquals(0, process.exitValue(), Files.readString(output));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS), "probe did not terminate");
            }
        }
    }
}
