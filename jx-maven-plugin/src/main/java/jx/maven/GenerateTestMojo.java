package jx.maven;

import java.io.IOException;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/** Generate test Java, indexing production component contracts without copying production outputs. */
@Mojo(name = "generate-test", defaultPhase = LifecyclePhase.GENERATE_TEST_SOURCES, threadSafe = true)
public final class GenerateTestMojo extends AbstractJxMojo {
    @Override public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            var result = new GenerationService().generate(testSourceDirectory.toPath(), List.of(sourceDirectory.toPath()),
                    testOutputDirectory.toPath(), testMetadataDirectory.toPath(), testClassDirectory.toPath());
            report(result.compilation().diagnostics());
            project.addTestCompileSourceRoot(testOutputDirectory.getAbsolutePath());
            getLog().info("JX generated " + result.written() + " changed test source(s), removed " + result.deleted() + " owned source(s)");
        } catch (GenerationService.InvalidSources ex) { report(ex.diagnostics); }
        catch (IOException | IllegalStateException ex) { throw new MojoExecutionException("Cannot generate JX test sources", ex); }
    }
}
