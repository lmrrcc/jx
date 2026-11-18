package jx.intellij;

import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class JxDevelopmentServiceTest extends BasePlatformTestCase {
    private JxDevelopmentService service;
    private JxSettings.Values previousSettings;
    private Path module;
    private Path source;

    @Override protected com.intellij.testFramework.fixtures.TempDirTestFixture createTempDirTestFixture() {
        return new com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl();
    }

    @Override protected void setUp() throws Exception {
        super.setUp();
        TrustedProjects.setTrusted(getProject(), true);
        previousSettings = JxSettings.getInstance().getState();
        var settings = new JxSettings.Values();
        settings.useProjectMavenCache = false;
        JxSettings.getInstance().loadState(settings);
        service = new JxDevelopmentService(getProject());
        Disposer.register(getTestRootDisposable(), service);
        Path root = Path.of(myFixture.getTempDirFixture().getTempDirPath());
        module = root.resolve("examples/example app");
        Files.createDirectories(module.resolve("src/main/jx"));
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        Files.writeString(module.resolve("pom.xml"), "<project/>");
        source = module.resolve("src/main/jx/App.jx");
        Files.writeString(source, "// process fixture");
        Files.writeString(root.resolve("DevFixture.java"), """
                import java.net.ServerSocket;
                import java.nio.file.Files;
                import java.nio.file.Path;
                public final class DevFixture {
                    public static void main(String[] args) throws Exception {
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try { Files.writeString(Path.of("dev-stopped.txt"), "stopped"); }
                            catch (Exception failure) { failure.printStackTrace(); }
                        }));
                        try (var listener = new ServerSocket(0)) {
                            Files.writeString(Path.of("dev-arguments.txt"), String.join("\\n", args));
                            Files.writeString(Path.of("dev-ready.txt"), Integer.toString(listener.getLocalPort()));
                            System.out.println("JX process fixture listening on http://127.0.0.1:" + listener.getLocalPort());
                            while (true) Thread.sleep(1000);
                        }
                    }
                }
                """);
        // Test boundary: a tiny wrapper runs a real Java process and accepts the
        // production Maven argument vector; no Maven or project build is mocked inside the adapter.
        Path wrapper = root.resolve("mvnw");
        Files.writeString(wrapper, "#!/bin/sh\nexec \"$JAVA_HOME/bin/java\" " + quote(root.resolve("DevFixture.java").toString()) + " \"$@\"\n");
        assertTrue(wrapper.toFile().setExecutable(true));
        Files.writeString(root.resolve("mvnw.cmd"), "@\"%JAVA_HOME%\\bin\\java.exe\" \"%~dp0DevFixture.java\" %*\r\n");
    }

    private static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }

    private void awaitReady() {
        PlatformTestUtil.waitWithEventsDispatching("JX development fixture did not start", () ->
                Files.isRegularFile(module.resolve("dev-ready.txt")) && service.process() != null && service.process().isStartNotified(), 20);
    }

    public void testRunConsoleModuleArgumentsDuplicateStartAndStop() throws Exception {
        service.start(source); awaitReady();
        var process = service.process();
        assertNotNull(process);
        var descriptor = service.consoleDescriptor();
        assertNotNull("Process must have an IDE Run console", descriptor);
        assertNotNull(descriptor.getExecutionConsole());
        assertSame(process, descriptor.getProcessHandler());
        assertEquals("JX Dev · example app", descriptor.getDisplayName());
        assertEquals(module, service.currentPlan().module());
        assertEquals(List.of("-B", "-ntp", "-f", module.resolve("pom.xml").toString(), DevLaunchPlan.DEFAULT_GOAL),
                Files.readAllLines(module.resolve("dev-arguments.txt")));
        service.start(source);
        assertSame("Duplicate start must preserve the existing process", process, service.process());
        int port = Integer.parseInt(Files.readString(module.resolve("dev-ready.txt")));
        service.stop();
        PlatformTestUtil.waitWithEventsDispatching("JX process did not stop", process::isProcessTerminated, 12);
        assertFalse(service.isRunning());
        assertTrue("Soft stop must execute Java shutdown cleanup", Files.exists(module.resolve("dev-stopped.txt")));
        try (var released = new ServerSocket(port)) { assertEquals(port, released.getLocalPort()); }
        RunContentManager.getInstance(getProject()).removeRunContent(DefaultRunExecutor.getRunExecutorInstance(), descriptor);
    }

    public void testDisposalStopsOwnedProcess() {
        service.start(source); awaitReady();
        var process = service.process();
        Disposer.dispose(service);
        PlatformTestUtil.waitWithEventsDispatching("Disposed service left a process running", process::isProcessTerminated, 12);
        assertFalse(service.isRunning());
    }

    public void testUntrustedProjectCannotStartWrapper() {
        TrustedProjects.setTrusted(getProject(), false);
        assertFalse(TrustedProjects.isTrusted(getProject()));
        service.start(source);
        assertFalse(service.isRunning());
        assertNull(service.process());
        assertFalse(Files.exists(module.resolve("dev-ready.txt")));
    }

    @Override protected void tearDown() throws Exception {
        try {
            if (service != null) {
                var process = service.process(); service.stop();
                if (process != null) PlatformTestUtil.waitWithEventsDispatching("Fixture process leaked", process::isProcessTerminated, 12);
                if (!Disposer.isDisposed(service)) Disposer.dispose(service);
            }
            var content = RunContentManager.getInstance(getProject());
            for (var descriptor : content.getAllDescriptors()) {
                if (descriptor.getDisplayName().startsWith("JX Dev")) content.removeRunContent(DefaultRunExecutor.getRunExecutorInstance(), descriptor);
            }
            TrustedProjects.setTrusted(getProject(), true);
            JxSettings.getInstance().loadState(previousSettings);
        } finally { super.tearDown(); }
    }
}
