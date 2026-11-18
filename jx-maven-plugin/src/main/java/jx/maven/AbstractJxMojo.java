package jx.maven;

import jx.compiler.Diagnostic;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

abstract class AbstractJxMojo extends AbstractMojo {
    /** The current reactor project, never shared across plugin invocations. */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${project.basedir}/src/main/jx", property = "jx.sourceDirectory", required = true)
    protected File sourceDirectory;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/jx", property = "jx.outputDirectory", required = true)
    protected File outputDirectory;

    @Parameter(defaultValue = "${project.build.directory}/jx/main", property = "jx.metadataDirectory", required = true)
    protected File metadataDirectory;

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true, required = true)
    protected File classDirectory;

    @Parameter(defaultValue = "${project.basedir}/src/test/jx", property = "jx.testSourceDirectory", required = true)
    protected File testSourceDirectory;

    @Parameter(defaultValue = "${project.build.directory}/generated-test-sources/jx", property = "jx.testOutputDirectory", required = true)
    protected File testOutputDirectory;

    @Parameter(defaultValue = "${project.build.directory}/jx/test", property = "jx.testMetadataDirectory", required = true)
    protected File testMetadataDirectory;

    @Parameter(defaultValue = "${project.build.testOutputDirectory}", readonly = true, required = true)
    protected File testClassDirectory;

    protected void report(List<Diagnostic> diagnostics) throws MojoFailureException {
        boolean failed = false;
        for (Diagnostic diagnostic : diagnostics) {
            if (diagnostic.severity() == Diagnostic.Severity.ERROR) { getLog().error(diagnostic.toString()); failed = true; }
            else getLog().warn(diagnostic.toString());
        }
        if (failed) throw new MojoFailureException("JX validation failed; see original-source diagnostics above");
    }
    protected List<Path> ordinaryJava(List<String> roots) throws IOException {
        Path generated = outputDirectory.toPath().toAbsolutePath().normalize();
        Path generatedTest = testOutputDirectory.toPath().toAbsolutePath().normalize();
        List<Path> result = new ArrayList<>();
        for (String root : roots) {
            Path path = Path.of(root).toAbsolutePath().normalize();
            if (path.equals(generated) || path.equals(generatedTest) || !Files.isDirectory(path)) continue;
            try (var paths = Files.walk(path)) {
                result.addAll(paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> !p.startsWith(generated) && !p.startsWith(generatedTest)).sorted().toList());
            }
        }
        return result.stream().distinct().toList();
    }
}
