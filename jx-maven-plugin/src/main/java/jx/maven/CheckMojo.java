package jx.maven;

import jx.compiler.JxCompiler;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Run javac semantic validation with errors mapped to original .jx sources; annotation processors are disabled. */
@Mojo(name = "check", defaultPhase = LifecyclePhase.PROCESS_SOURCES, requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public final class CheckMojo extends AbstractJxMojo {
    @Override public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            var compiler = new JxCompiler();
            var compilation = compiler.compile(GenerationService.readSources(sourceDirectory.toPath()), componentClasspath(false));
            report(compiler.check(compilation, ordinaryJava(project.getCompileSourceRoots()),
                    project.getCompileClasspathElements().stream().map(Path::of).toList(),
                    metadataDirectory.toPath().resolve("checked-classes")));
        } catch (IOException | DependencyResolutionRequiredException | IllegalStateException ex) {
            throw new MojoExecutionException("Cannot check JX Java semantics", ex);
        }
    }
}
