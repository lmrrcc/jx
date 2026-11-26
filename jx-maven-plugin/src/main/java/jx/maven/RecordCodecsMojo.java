package jx.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/** Generate strict browser JSON codecs from selected portable dependency records. */
@Mojo(name = "record-codecs", defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public final class RecordCodecsMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;
    /** Fully qualified public record names; declaring a type does not load or initialize it. */
    @Parameter
    private List<String> contractTypes = List.of();
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/jx-contracts", required = true)
    private File contractOutputDirectory;
    @Parameter(defaultValue = "${project.build.directory}/jx/contracts", required = true)
    private File contractMetadataDirectory;

    @Override public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            var result = new RecordCodecGeneration().generate(contractTypes == null ? List.of() : contractTypes,
                    project.getCompileClasspathElements().stream().map(Path::of).toList(),
                    contractOutputDirectory.toPath(), contractMetadataDirectory.toPath(),
                    Path.of(project.getBuild().getOutputDirectory()));
            project.addCompileSourceRoot(contractOutputDirectory.getAbsolutePath());
            getLog().info("JX contract codecs: " + result.written() + " changed, " + result.removed() + " removed");
        } catch (IllegalArgumentException failure) {
            throw new MojoFailureException(failure.getMessage(), failure);
        } catch (IOException | DependencyResolutionRequiredException failure) {
            throw new MojoExecutionException("Cannot generate contract codecs", failure);
        }
    }
}
