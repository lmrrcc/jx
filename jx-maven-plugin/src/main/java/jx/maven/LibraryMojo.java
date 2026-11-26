package jx.maven;

import java.io.IOException;
import jx.compiler.JxCompiler;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Publishes a versioned, binary-verified component index in the module's ordinary JAR.
 * Run after javac; the goal does not package sources or initialize application classes. */
@Mojo(name = "library", defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public final class LibraryMojo extends AbstractJxMojo {
    @Override public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            var sources = GenerationService.readSources(sourceDirectory.toPath());
            var result = new JxCompiler().compile(sources, componentClasspath(false));
            report(result.diagnostics());
            var names = sources.stream().map(value -> value.path()).collect(java.util.stream.Collectors.toSet());
            var own = result.components().stream().filter(component -> names.contains(component.source())).toList();
            GenerationService.library(own, classDirectory.toPath(), metadataDirectory.toPath());
            getLog().info("JX library index: " + own.size() + " component(s)");
        } catch (IOException | DependencyResolutionRequiredException | IllegalStateException invalid) {
            throw new MojoExecutionException("Cannot publish JX component library metadata", invalid);
        }
    }
}
