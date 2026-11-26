package jx.intellij;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Measures completed native operations, not frame painting or a cold IDE process. */
public final class JxEditorScaleTest extends LightJavaCodeInsightFixtureTestCase {
    private static final int WARMUPS = 3, SAMPLES = 30;
    private boolean wasTrusted;
    @Override protected LightProjectDescriptor getProjectDescriptor() { return JAVA_25; }
    @Override protected void setUp() throws Exception {
        super.setUp();
        myFixture.setCaresAboutInjection(false);
        wasTrusted = TrustedProjects.isTrusted(getProject());
        TrustedProjects.setTrusted(getProject(), false);
        Path core = Path.of(System.getProperty("jx.repositoryRoot")).resolve("jx-core/target/classes");
        assertTrue("Build this checkout's core before running SDK acceptance", Files.isRegularFile(core.resolve("jx/ComponentType.class")));
        PsiTestUtil.addLibrary(getTestRootDisposable(), getModule(), "jx-core", core.getParent().toString(), core.getFileName().toString());
    }
    @Override protected void tearDown() throws Exception {
        try { TrustedProjects.setTrusted(getProject(), wasTrusted); } finally { super.tearDown(); }
    }

    public void testHundredComponentsNativeEditingLatencyAndFreshResults() throws Exception {
        var app = new JxScaleFixture(myFixture);
        var service = JxJavaProjection.getInstance(getProject());
        long firstStart = System.nanoTime();
        var first = service.snapshot();
        long firstNanos = System.nanoTime() - firstStart;
        assertEquals(JxScaleFixture.COMPONENTS, first.generated().size());
        assertEquals(1, first.batches().size());
        PsiFile active = app.pages.getFirst();
        myFixture.configureFromExistingVirtualFile(active.getVirtualFile());
        for (PsiFile sibling : app.pages.subList(1, app.pages.size()))
            assertFalse("The scale must include closed consumers", FileEditorManager.getInstance(getProject()).isFileOpen(sibling.getVirtualFile()));
        var samples = new ArrayList<Sample>();
        samples.add(new Sample("first-project-projection", 0, firstNanos));
        // Opening an editor may legitimately invalidate a project snapshot through VFS/PSI.
        // The warm baseline begins after that operation, outside the measured lookup loop.
        var warm = service.snapshot();
        for (int index = -WARMUPS; index < SAMPLES; index++) {
            long start = System.nanoTime();
            var snapshot = service.snapshot();
            if (index >= 0) samples.add(new Sample("unchanged-project-projection", index, System.nanoTime() - start));
            assertTrue("An unchanged warm lookup must reuse the current project snapshot", warm == snapshot);
        }
        writeReport(samples);
        for (int index = -WARMUPS; index < SAMPLES; index++) {
            String value = "Sample-" + (index + WARMUPS + 100);
            long start = System.nanoTime();
            replace(active, JxScaleFixture.page(0, "Counter").replace("Sample-000", value));
            var projected = service.forHost(JxJavaProjection.host(active)).orElseThrow();
            if (index >= 0) samples.add(new Sample("edit-to-projection", index, System.nanoTime() - start));
            assertTrue("Edited text must reach the current projection", projected.javaSource().contains(value));
            assertEquals(100, service.snapshot().generated().size());
        }
        writeReport(samples);
        for (int index = -WARMUPS; index < SAMPLES; index++) {
            String source = JxScaleFixture.page(0, "Counter").replace("Sample-000", "Completion-" + index)
                    .replace("Helpers.buildName(\"Completion-" + index + "\")", "Helpers.bu");
            long start = System.nanoTime();
            replace(active, source);
            myFixture.getEditor().getCaretModel().moveToOffset(source.indexOf("Helpers.bu") + "Helpers.bu".length());
            var choices = myFixture.completeBasic();
            long elapsed = System.nanoTime() - start;
            assertNotNull("Native Java completion must present both candidates", choices);
            List<String> names = Arrays.stream(choices).map(choice -> choice.getLookupString()).toList();
            assertTrue(names.toString(), names.containsAll(List.of("buildName", "buildLabel")));
            LookupManager.getInstance(getProject()).hideActiveLookup();
            if (index >= 0) samples.add(new Sample("edit-to-native-completion", index, elapsed));
        }
        writeReport(samples);
        for (int index = -WARMUPS; index < SAMPLES; index++) {
            boolean invalid = index % 2 == 0;
            String source = JxScaleFixture.page(0, "Counter").replace("Sample-000", "Diagnostic-" + index);
            if (invalid) source = source.replace("Helpers.buildLabel(title)", "Helpers.buildLabel(42)");
            long start = System.nanoTime();
            replace(active, source);
            var errors = myFixture.doHighlighting(HighlightSeverity.ERROR);
            long elapsed = System.nanoTime() - start;
            int bad = source.indexOf("Helpers.buildLabel(42)");
            if (invalid) {
                assertFalse("Java must diagnose the invalid typed argument", errors.isEmpty());
                for (var error : errors) assertTrue("Unexpected/unmapped error: " + error,
                        error.getStartOffset() < bad + "Helpers.buildLabel(42)".length() && error.getEndOffset() > bad);
            } else assertTrue("Correcting the argument must clear stale diagnostics: " + errors, errors.isEmpty());
            if (index >= 0) samples.add(new Sample("edit-to-native-diagnostics", index, elapsed));
        }
        replace(active, JxScaleFixture.page(0, "Counter"));
        assertTrue("Recovery must clear previous errors", myFixture.doHighlighting(HighlightSeverity.ERROR).isEmpty());
        writeReport(samples);
    }

    private void replace(PsiFile file, String source) {
        var manager = PsiDocumentManager.getInstance(getProject());
        var document = manager.getDocument(file);
        assertNotNull(document);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            document.setText(source);
            manager.commitDocument(document);
        });
    }

    private record Sample(String operation, int index, long nanos) {}
    private static void writeReport(List<Sample> samples) throws Exception {
        Path directory = Path.of(System.getProperty("jx.repositoryRoot"), "tooling/intellij/build/reports/editor-scale");
        Files.createDirectories(directory);
        var rows = new ArrayList<String>();
        rows.add("operation\tsample\tnanoseconds");
        for (Sample sample : samples) rows.add(sample.operation() + "\t" + sample.index() + "\t" + sample.nanos());
        Files.write(directory.resolve("latency.tsv"), rows, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("environment.txt"), "components=100\nmodules=1\nwarmups=" + WARMUPS
                + "\nsamples=" + SAMPLES + "\njava=" + System.getProperty("java.runtime.version")
                + "\nos=" + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch")
                + "\nprocessors=" + Runtime.getRuntime().availableProcessors()
                + "\nmaxHeapBytes=" + Runtime.getRuntime().maxMemory() + "\n", StandardCharsets.UTF_8);
        for (String operation : samples.stream().map(Sample::operation).distinct().toList()) {
            long[] values = samples.stream().filter(sample -> sample.operation().equals(operation)).mapToLong(Sample::nanos).sorted().toArray();
            System.out.printf(Locale.ROOT, "JX 100 components %s: n=%d p50=%.3fms p95=%.3fms max=%.3fms%n", operation,
                    values.length, values[(int) Math.ceil(values.length * .50) - 1] / 1_000_000.0,
                    values[(int) Math.ceil(values.length * .95) - 1] / 1_000_000.0, values[values.length - 1] / 1_000_000.0);
        }
    }
}
