package jx.intellij;

import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.nio.file.Path;
import java.awt.BorderLayout;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/** One owned development process per project; IDE threads never wait for Maven. */
@Service(Service.Level.PROJECT)
public final class JxDevelopmentService implements Disposable {
    private record Run(long request, DevLaunchPlan plan, KillableColoredProcessHandler process) {}
    private final Project project;
    private final Object lock = new Object();
    private long request;
    private boolean starting;
    private boolean disposed;
    private Run active;
    private RunContentDescriptor content;

    public JxDevelopmentService(Project project) { this.project = project; }
    static JxDevelopmentService getInstance(Project project) { return project.getService(JxDevelopmentService.class); }

    public void start(Path source) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        if (project.isDisposed() || !TrustedProjects.isTrusted(project)) {
            notifyError("Trust this project before starting its Maven Wrapper."); return;
        }
        var settings = JxSettings.getInstance().getState();
        List<String> arguments;
        try { arguments = DevLaunchPlan.validateArguments(settings.developmentArguments); }
        catch (IllegalArgumentException failure) { notifyError(failure.getMessage()); return; }
        String repository = settings.developmentRepository;
        boolean cache = settings.useProjectMavenCache;
        String java = settings.javaPath;
        long launch;
        Run previous;
        synchronized (lock) {
            if (disposed) return;
            previous = active != null && !active.process().isProcessTerminated() ? active : null;
            if (previous == null && starting) return;
            if (previous == null) { starting = true; launch = ++request; }
            else launch = request;
        }
        if (previous != null) {
            RunContentManager.getInstance(project).toFrontRunContent(DefaultRunExecutor.getRunExecutorInstance(), previous.process());
            return;
        }
        try { FileDocumentManager.getInstance().saveAllDocuments(); }
        catch (RuntimeException failure) { cancel(launch); notifyError("Cannot save project documents: " + failure.getMessage()); return; }
        new Task.Backgroundable(project, "Starting JX development server", true) {
            @Override public void run(@NotNull ProgressIndicator indicator) {
                try {
                    DevLaunchPlan plan = DevLaunchPlan.resolve(source, arguments, repository, cache);
                    indicator.checkCanceled();
                    synchronized (lock) { if (disposed || request != launch) return; }
                    if (!TrustedProjects.isTrusted(project)) throw new IllegalStateException("Project trust was revoked before starting Maven.");
                    var process = new KillableColoredProcessHandler(plan.commandLine(java));
                    process.setShouldDestroyProcessRecursively(true);
                    process.setShouldKillProcessSoftly(true);
                    Run run = new Run(launch, plan, process);
                    process.addProcessListener(new ProcessAdapter() {
                        @Override public void processTerminated(@NotNull ProcessEvent event) {
                            synchronized (lock) {
                                if (active == run) { active = null; if (request == run.request()) starting = false; }
                            }
                        }
                    });
                    synchronized (lock) {
                        if (disposed || request != launch) { terminate(process); return; }
                        active = run;
                    }
                    ApplicationManager.getApplication().invokeLater(() -> show(run, source), ModalityState.nonModal());
                } catch (Exception failure) {
                    synchronized (lock) { if (request != launch || disposed) return; starting = false; }
                    if (failure instanceof com.intellij.openapi.progress.ProcessCanceledException cancelled) throw cancelled;
                    notifyError(failure.getMessage() == null ? failure.toString() : failure.getMessage());
                }
            }
            @Override public void onCancel() { cancel(launch); }
        }.queue();
    }

    private void show(Run run, Path source) {
        synchronized (lock) {
            if (disposed || project.isDisposed() || request != run.request()) { terminate(run.process()); return; }
            starting = false;
        }
        try {
            ProcessTerminatedListener.attach(run.process(), project);
            var console = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
            console.addMessageFilter(new UrlFilter(project));
            var actions = new DefaultActionGroup();
            actions.add(new DumbAwareAction("Rerun JX", "Start the same Maven module again", AllIcons.Actions.Rerun) {
                @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
                @Override public void actionPerformed(@NotNull AnActionEvent event) { start(source); }
                @Override public void update(@NotNull AnActionEvent event) { event.getPresentation().setEnabled(!isRunning()); }
            });
            actions.add(new DumbAwareAction("Stop JX", "Stop the development server and build", AllIcons.Actions.Suspend) {
                @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
                @Override public void actionPerformed(@NotNull AnActionEvent event) { stop(); }
                @Override public void update(@NotNull AnActionEvent event) { event.getPresentation().setEnabled(isRunning()); }
            });
            actions.addAll(console.createConsoleActions());
            var panel = new JPanel(new BorderLayout());
            var toolbar = ActionManager.getInstance().createActionToolbar("JxDevelopment", actions, false);
            toolbar.setTargetComponent(panel);
            panel.add(toolbar.getComponent(), BorderLayout.WEST);
            panel.add(console.getComponent(), BorderLayout.CENTER);
            var descriptor = new RunContentDescriptor(console, run.process(), panel, "JX Dev · " + run.plan().module().getFileName());
            Disposer.register(this, descriptor);
            Disposer.register(descriptor, console);
            RunContentDescriptor previous;
            synchronized (lock) { previous = content; content = descriptor; }
            console.attachToProcess(run.process());
            RunContentManager.getInstance(project).showRunContent(DefaultRunExecutor.getRunExecutorInstance(), descriptor, previous);
            if (previous != null && !Disposer.isDisposed(previous)) Disposer.dispose(previous);
            run.process().startNotify();
        } catch (RuntimeException failure) {
            stop(); notifyError("Cannot open the JX Run console: " + failure.getMessage());
        }
    }

    public boolean isRunning() {
        synchronized (lock) { return starting || active != null && !active.process().isProcessTerminated(); }
    }
    KillableColoredProcessHandler process() { synchronized (lock) { return active == null ? null : active.process(); } }
    DevLaunchPlan currentPlan() { synchronized (lock) { return active == null ? null : active.plan(); } }
    RunContentDescriptor consoleDescriptor() { synchronized (lock) { return content; } }

    public void stop() {
        Run running;
        synchronized (lock) { request++; starting = false; running = active; }
        if (running != null) terminate(running.process());
    }
    private void cancel(long launch) {
        synchronized (lock) { if (request != launch) return; }
        stop();
    }
    private static void terminate(KillableColoredProcessHandler process) {
        if (!process.isStartNotified()) process.startNotify();
        if (process.isProcessTerminated() || process.isProcessTerminating()) return;
        process.destroyProcess();
        try {
            AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
                if (!process.isProcessTerminated()) process.killProcess();
            }, 5, TimeUnit.SECONDS);
        } catch (RejectedExecutionException shuttingDown) { process.killProcess(); }
    }
    private void notifyError(String message) {
        if (!project.isDisposed()) NotificationGroupManager.getInstance().getNotificationGroup("JX Development")
                .createNotification("JX development server", message, NotificationType.ERROR).notify(project);
    }
    @Override public void dispose() {
        synchronized (lock) { disposed = true; }
        stop();
    }
}
