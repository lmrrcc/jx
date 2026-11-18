package jx.intellij;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Resolves a development command without launching it or evaluating project code. */
record DevLaunchPlan(Path module, Path wrapper, List<String> arguments) {
    static final String DEFAULT_GOAL = "dev.jx:jx-maven-plugin:0.1.0-SNAPSHOT:dev";

    DevLaunchPlan {
        module = module.toAbsolutePath().normalize();
        wrapper = wrapper.toAbsolutePath().normalize();
        arguments = List.copyOf(arguments);
    }

    static List<String> parseArguments(String text) {
        return validateArguments(text.lines().map(String::trim).filter(line -> !line.isEmpty()).toList());
    }

    static List<String> validateArguments(List<String> arguments) {
        if (arguments == null || arguments.isEmpty() || arguments.size() > 64) {
            throw new IllegalArgumentException("Provide 1–64 Maven arguments, one argument per line.");
        }
        for (String argument : arguments) {
            if (argument == null || argument.isBlank() || argument.length() > 4096 || argument.chars().anyMatch(character -> character < 32)) {
                throw new IllegalArgumentException("Maven arguments must be nonblank, at most 4096 characters, and contain no control characters.");
            }
            if (argument.equals("--file") || argument.startsWith("--file=")
                    || argument.startsWith("-f") && !List.of("-fae", "-ff", "-fn").contains(argument)) {
                throw new IllegalArgumentException("The active .jx file selects the Maven module; remove -f/--file from development arguments.");
            }
            if (argument.startsWith("-Dmaven.repo.local")) {
                throw new IllegalArgumentException("Use the Development Maven repository setting instead of -Dmaven.repo.local.");
            }
        }
        if (arguments.stream().noneMatch(argument -> !argument.startsWith("-") && argument.endsWith(":dev"))) {
            throw new IllegalArgumentException("Development arguments must include a Maven :dev goal.");
        }
        return List.copyOf(arguments);
    }

    static DevLaunchPlan resolve(Path source, List<String> arguments, String repository, boolean useProjectCache) throws IOException {
        Path file = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file) || !file.getFileName().toString().endsWith(".jx")) {
            throw new IOException("Open a saved .jx source file before starting the development server.");
        }
        Path module = ancestorContaining(file.getParent(), "pom.xml");
        if (module == null) throw new IOException("No pom.xml was found above " + file);
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        String wrapperName = windows ? "mvnw.cmd" : "mvnw";
        Path wrapperRoot = ancestorContaining(module, wrapperName);
        if (wrapperRoot == null) throw new IOException("No " + wrapperName + " was found in the Maven module or its parent directories.");
        Path wrapper = wrapperRoot.resolve(wrapperName);
        if (!windows && !Files.isExecutable(wrapper)) throw new IOException("Maven Wrapper is not executable: " + wrapper);
        List<String> command = new ArrayList<>(List.of("-B", "-ntp", "-f", module.resolve("pom.xml").toString()));
        if (repository != null && !repository.isBlank()) {
            Path selected = Path.of(repository.trim());
            Path resolved = (selected.isAbsolute() ? selected : wrapperRoot.resolve(selected)).normalize();
            if (Files.exists(resolved) && !Files.isDirectory(resolved)) throw new IOException("Maven repository is not a directory: " + resolved);
            command.add("-Dmaven.repo.local=" + resolved);
        } else if (useProjectCache && Files.isDirectory(wrapperRoot.resolve(".cache/maven-repository"))) {
            command.add("-Dmaven.repo.local=" + wrapperRoot.resolve(".cache/maven-repository"));
        }
        command.addAll(validateArguments(arguments));
        return new DevLaunchPlan(module, wrapper, command);
    }

    private static Path ancestorContaining(Path from, String name) {
        for (Path candidate = from; candidate != null; candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve(name))) return candidate;
        }
        return null;
    }

    GeneralCommandLine commandLine(String javaExecutable) throws ExecutionException {
        Path java = Path.of(javaExecutable).toAbsolutePath().normalize();
        Path javaHome = java.getParent() == null ? null : java.getParent().getParent();
        if (!Files.isRegularFile(java) || javaHome == null || !Files.isRegularFile(javaHome.resolve("release"))) {
            throw new ExecutionException("Set the JX Java executable to a JDK's bin/java (Java 25), not a system launcher alias.");
        }
        var command = new GeneralCommandLine().withWorkDirectory(module.toFile()).withEnvironment("JAVA_HOME", javaHome.toString());
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        if (windows) {
            for (String value : Stream.concat(Stream.of(wrapper.toString()), arguments.stream()).toList()) {
                if (value.chars().anyMatch(character -> "&|<>^%!\r\n".indexOf(character) >= 0)) {
                    throw new ExecutionException("Windows Maven Wrapper paths and arguments cannot contain command-interpreter metacharacters.");
                }
            }
            command.withExePath("cmd.exe").addParameters("/d", "/c", wrapper.toString());
        } else command.withExePath(wrapper.toString());
        command.addParameters(arguments);
        return command;
    }
}
