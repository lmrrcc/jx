package jx.maven;

import jx.compiler.JxCompiler;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Check production and test .jx with the test classpath, preserving authored diagnostic positions. */
@Mojo(name = "check-test", defaultPhase = LifecyclePhase.PROCESS_TEST_SOURCES, requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public final class CheckTestMojo extends AbstractJxMojo {
    @Override public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            var compiler = new JxCompiler();
            var sources = new ArrayList<>(GenerationService.readSources(sourceDirectory.toPath()));
            sources.addAll(GenerationService.readSources(testSourceDirectory.toPath()));
            var roots = new ArrayList<>(project.getCompileSourceRoots());
            roots.addAll(project.getTestCompileSourceRoots());
            report(compiler.check(compiler.compile(sources, componentClasspath(true)), ordinaryJava(roots),
                    project.getTestClasspathElements().stream().map(Path::of).toList(),
                    testMetadataDirectory.toPath().resolve("checked-classes")));
        } catch (IOException | DependencyResolutionRequiredException | IllegalStateException ex) {
            throw new MojoExecutionException("Cannot check JX test Java semantics", ex);
        }
    }
}
