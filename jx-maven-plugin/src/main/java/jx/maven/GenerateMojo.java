package jx.maven;

import java.io.IOException;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/** Generate ordinary Java and source maps from src/main/jx and register the source root. */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public final class GenerateMojo extends AbstractJxMojo {
    @Override public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            var result = new GenerationService().generate(sourceDirectory.toPath(), List.of(), outputDirectory.toPath(),
                    metadataDirectory.toPath(), classDirectory.toPath());
            report(result.compilation().diagnostics());
            project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
            getLog().info("JX generated " + result.written() + " changed source(s), removed " + result.deleted() + " owned source(s)");
        } catch (GenerationService.InvalidSources ex) { report(ex.diagnostics); }
        catch (IOException | IllegalStateException ex) { throw new MojoExecutionException("Cannot generate JX sources", ex); }
    }
}
