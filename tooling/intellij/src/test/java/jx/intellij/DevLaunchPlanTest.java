package jx.intellij;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import junit.framework.TestCase;

public final class DevLaunchPlanTest extends TestCase {
    private Path root;
    @Override protected void setUp() throws Exception { root = Files.createTempDirectory("jx-module-selection-"); }
    @Override protected void tearDown() throws Exception {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
    private Path source(String relative) throws Exception {
        Path file = root.resolve(relative); Files.createDirectories(file.getParent()); Files.writeString(file, ""); return file;
    }
    private void wrapper() throws Exception {
        source("pom.xml"); source("mvnw").toFile().setExecutable(true); source("mvnw.cmd");
    }
    public void testNearestModuleUsesAncestorWrapperAndExistingCache() throws Exception {
        wrapper(); source("examples/counter/pom.xml");
        Path file = source("examples/counter/src/main/jx/App.jx");
        Files.createDirectories(root.resolve(".cache/maven-repository"));
        var plan = DevLaunchPlan.resolve(file, List.of(DevLaunchPlan.DEFAULT_GOAL, "-Djx.port=4299"), "", true);
        assertEquals(root.resolve("examples/counter"), plan.module());
        assertEquals(root, plan.wrapper().getParent());
        assertEquals(List.of("-B", "-ntp", "-f", root.resolve("examples/counter/pom.xml").toString(),
                "-Dmaven.repo.local=" + root.resolve(".cache/maven-repository"), DevLaunchPlan.DEFAULT_GOAL, "-Djx.port=4299"), plan.arguments());
    }
    public void testRepositoryOverrideAndDefaultAreExplicit() throws Exception {
        wrapper(); Path file = source("src/main/jx/App.jx");
        Files.createDirectories(root.resolve(".cache/maven-repository"));
        var defaults = DevLaunchPlan.resolve(file, List.of(DevLaunchPlan.DEFAULT_GOAL), "", false);
        assertTrue(defaults.arguments().stream().noneMatch(value -> value.startsWith("-Dmaven.repo.local=")));
        var custom = DevLaunchPlan.resolve(file, List.of(DevLaunchPlan.DEFAULT_GOAL), "local dependencies", true);
        assertTrue(custom.arguments().contains("-Dmaven.repo.local=" + root.resolve("local dependencies")));
    }
    public void testArgumentsKeepSpacesAndRejectModuleOverride() {
        assertEquals(List.of(DevLaunchPlan.DEFAULT_GOAL, "-Dlabel=hello world"), DevLaunchPlan.parseArguments(DevLaunchPlan.DEFAULT_GOAL + "\n-Dlabel=hello world\n"));
        for (List<String> invalid : List.of(List.of("package"), List.of(DevLaunchPlan.DEFAULT_GOAL, "-fother.xml"),
                List.of(DevLaunchPlan.DEFAULT_GOAL, "--file=other.xml"), List.of(DevLaunchPlan.DEFAULT_GOAL, "-Dmaven.repo.local=elsewhere"))) {
            try { DevLaunchPlan.validateArguments(invalid); fail("Expected argument validation failure"); }
            catch (IllegalArgumentException expected) { assertFalse(expected.getMessage().isBlank()); }
        }
    }
    public void testMissingModuleAndWrapperReportErrors() throws Exception {
        Path file = source("src/main/jx/App.jx");
        try { DevLaunchPlan.resolve(file, List.of(DevLaunchPlan.DEFAULT_GOAL), "", false); fail(); }
        catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("pom.xml")); }
        source("pom.xml");
        try { DevLaunchPlan.resolve(file, List.of(DevLaunchPlan.DEFAULT_GOAL), "", false); fail(); }
        catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("mvnw")); }
    }
    public void testCurrentRepositoryCounterIsResolvedAsItsOwnModule() throws Exception {
        Path repository = Path.of(System.getProperty("jx.repositoryRoot")).toAbsolutePath().normalize();
        Path file = repository.resolve("examples/counter-app/src/main/jx/jx/example/counter/App.jx");
        var plan = DevLaunchPlan.resolve(file, List.of(DevLaunchPlan.DEFAULT_GOAL), "", true);
        assertEquals(repository.resolve("examples/counter-app"), plan.module());
        assertEquals(repository, plan.wrapper().getParent());
    }
}
