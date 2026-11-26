import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Local gate for {@code git push}: runs the checks of the Verify workflow so a commit
 * that would fail there does not leave this machine.
 *
 * <p>Git calls this through {@code .githooks/pre-push} once the hooks directory is
 * enabled with {@code java tools/PrePush.java --install}. The checks run against the
 * working tree, so the push is refused unless the working tree is exactly the commit
 * being pushed: no uncommitted or untracked files, and the pushed commit checked out.
 * {@code git push --no-verify} skips the gate; the protected main branch still requires
 * the Verify check on GitHub.
 *
 * <p>The step list must stay equal to the {@code run} steps of
 * {@code .github/workflows/verify.yml}, except the two that install tools
 * ({@code npm ci} and the Playwright browser install). {@code --check} verifies that
 * and is itself one of the steps, so drift fails both here and in the workflow.
 */
public final class PrePush {
    private static final String WORKFLOW = ".github/workflows/verify.yml";
    private static final String HOOKS = ".githooks";
    private static final String NO_COMMIT = "0".repeat(40);
    private static final long STEP_TIMEOUT_MINUTES = 20;
    private static final List<String> INSTALL_STEPS = List.of("npm ci", "npx playwright install --with-deps chrome");
    private static final List<String> STEPS = List.of(
            "java tools/CheckAgentSetup.java",
            "java tools/CheckBaseline.java",
            "java tools/PrePush.java --check",
            "java tools/GenerateDomTables.java --check",
            "./mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository install",
            "npm run build:proof",
            "npm run test:proof",
            "npm run build:runtime",
            "npm run test:runtime",
            "npm run test:bench",
            "npm run test:lazy",
            "npm run test:rsc",
            "npm run test:counter",
            "npm run test:todo",
            "npm run test:packaged",
            "npm run test:auth",
            "npm run test:ssr-protected",
            "npm run test:hot",
            "npm run test:extension",
            "npm run test:consumer");

    private PrePush() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--install")) {
            System.exit(install());
        } else if (args.length == 1 && args[0].equals("--check")) {
            System.exit(checkWorkflow());
        } else if (args.length == 0) {
            System.exit(gate());
        }
        System.err.println("Usage: java tools/PrePush.java [--install | --check]");
        System.exit(2);
    }

    private static int install() throws Exception {
        if (!Files.isExecutable(Path.of(HOOKS, "pre-push"))) {
            System.err.println("FAIL: " + HOOKS + "/pre-push is missing or not executable");
            return 1;
        }
        if (run(List.of("git", "config", "core.hooksPath", HOOKS), 1) != 0) {
            return 1;
        }
        System.out.println("PASS: git push in this clone now runs " + HOOKS + "/pre-push");
        return 0;
    }

    /** Fails when the step list here and the workflow's run steps have drifted apart. */
    private static int checkWorkflow() throws IOException {
        List<String> workflow = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(WORKFLOW), StandardCharsets.UTF_8)) {
            String trimmed = line.strip();
            if (trimmed.startsWith("- run: ")) {
                workflow.add(trimmed.substring("- run: ".length()));
            }
        }
        workflow.removeAll(INSTALL_STEPS);
        if (!workflow.equals(STEPS)) {
            System.err.println("FAIL: tools/PrePush.java and " + WORKFLOW + " run different steps");
            System.err.println("  workflow: " + workflow);
            System.err.println("  pre-push: " + STEPS);
            return 1;
        }
        System.out.println("PASS: pre-push runs the " + STEPS.size() + " check steps of " + WORKFLOW);
        return 0;
    }

    private static int gate() throws Exception {
        List<String> pushed = pushedCommits();
        if (pushed.isEmpty()) {
            System.out.println("pre-push: nothing but deletions to push; checks skipped");
            return 0;
        }
        String head = output("git", "rev-parse", "HEAD");
        for (String commit : pushed) {
            if (!commit.equals(head)) {
                return refuse("the push includes " + commit.substring(0, 7) + ", which is not the checked-out commit "
                        + head.substring(0, 7) + "; check it out so the checks test what is pushed");
            }
        }
        String changes = output("git", "status", "--porcelain");
        if (!changes.isEmpty()) {
            return refuse("the working tree differs from the commit being pushed; commit, stash or remove:\n" + changes);
        }
        long start = System.nanoTime();
        for (int index = 0; index < STEPS.size(); index++) {
            String step = STEPS.get(index);
            System.out.printf("%npre-push [%d/%d] %s%n", index + 1, STEPS.size(), step);
            if (run(List.of(step.split(" ")), STEP_TIMEOUT_MINUTES) != 0) {
                return refuse("step failed: " + step);
            }
        }
        System.out.printf("%npre-push: all %d steps passed in %d s%n", STEPS.size(),
                TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start));
        return 0;
    }

    /** Reads git's "local-ref local-sha remote-ref remote-sha" lines; deletions are left out. */
    private static List<String> pushedCommits() throws Exception {
        List<String> commits = new ArrayList<>();
        var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            String[] cells = line.strip().split(" ");
            if (cells.length == 4 && !cells[1].equals(NO_COMMIT)) {
                // Resolve to a commit so an annotated tag compares equal to its target.
                commits.add(output("git", "rev-parse", cells[1] + "^{commit}"));
            }
        }
        return commits;
    }

    private static int refuse(String reason) {
        System.err.println();
        System.err.println("pre-push: push refused: " + reason);
        System.err.println("pre-push: `git push --no-verify` skips this gate; main still requires Verify on GitHub");
        return 1;
    }

    private static int run(List<String> command, long timeoutMinutes) throws Exception {
        Process process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        process.getOutputStream().close();
        if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            System.err.println("pre-push: timed out after " + timeoutMinutes + " minutes: " + String.join(" ", command));
            return 1;
        }
        return process.exitValue();
    }

    private static String output(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        process.getOutputStream().close();
        String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        if (process.waitFor() != 0) {
            throw new IOException("command failed: " + String.join(" ", command));
        }
        return text;
    }
}
