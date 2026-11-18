package jx.intellij;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.Configurable;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public final class JxConfigurable implements Configurable {
    private JTextField javaPath;
    private JTextArea classpath;
    private JTextArea developmentArguments;
    private JTextField developmentRepository;
    private JCheckBox useProjectCache;
    @Override public String getDisplayName() { return "JX"; }
    @Override public JComponent createComponent() {
        javaPath = new JTextField();
        classpath = new JTextArea(8, 50);
        developmentArguments = new JTextArea(5, 50);
        developmentRepository = new JTextField();
        useProjectCache = new JCheckBox("Use .cache/maven-repository beside Maven Wrapper when present");
        JPanel java = new JPanel(new GridLayout(2, 1));
        java.add(new JLabel("Java 25 executable (absolute path):")); java.add(javaPath);
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(java, BorderLayout.NORTH);
        JPanel entries = new JPanel(new BorderLayout());
        entries.add(new JLabel("Additional classpath entries, one per line (restart the server after changes):"), BorderLayout.NORTH);
        entries.add(new JScrollPane(classpath), BorderLayout.CENTER);
        JPanel sections = new JPanel(new GridLayout(2, 1, 8, 8));
        sections.add(entries);
        JPanel development = new JPanel(new BorderLayout(4, 4));
        development.add(new JLabel("Development Maven arguments: one argument per line; the active .jx file selects the module."), BorderLayout.NORTH);
        development.add(new JScrollPane(developmentArguments), BorderLayout.CENTER);
        JPanel repository = new JPanel(new GridLayout(3, 1));
        repository.add(new JLabel("Development Maven repository override (blank uses cache/default; relative to Wrapper):"));
        repository.add(developmentRepository); repository.add(useProjectCache);
        development.add(repository, BorderLayout.SOUTH);
        sections.add(development);
        panel.add(sections, BorderLayout.CENTER);
        reset();
        return panel;
    }
    private List<String> entries() { return classpath.getText().lines().map(String::trim).filter(line -> !line.isEmpty()).toList(); }
    @Override public boolean isModified() {
        var settings = JxSettings.getInstance().getState();
        return javaPath != null && (!javaPath.getText().trim().equals(settings.javaPath) || !entries().equals(settings.classpath)
                || !developmentArguments.getText().equals(String.join("\n", settings.developmentArguments))
                || !developmentRepository.getText().trim().equals(settings.developmentRepository)
                || useProjectCache.isSelected() != settings.useProjectMavenCache);
    }
    @Override public void apply() throws ConfigurationException {
        if (javaPath.getText().isBlank()) throw new ConfigurationException("Select a Java 25 executable.");
        var settings = new JxSettings.Values();
        settings.javaPath = javaPath.getText().trim(); settings.classpath = entries();
        try { settings.developmentArguments = DevLaunchPlan.parseArguments(developmentArguments.getText()); }
        catch (IllegalArgumentException failure) { throw new ConfigurationException(failure.getMessage()); }
        settings.developmentRepository = developmentRepository.getText().trim();
        try { if (!settings.developmentRepository.isBlank()) java.nio.file.Path.of(settings.developmentRepository); }
        catch (java.nio.file.InvalidPathException failure) { throw new ConfigurationException("Development Maven repository is not a valid path."); }
        settings.useProjectMavenCache = useProjectCache.isSelected();
        JxSettings.getInstance().loadState(settings);
    }
    @Override public void reset() {
        var settings = JxSettings.getInstance().getState();
        javaPath.setText(settings.javaPath); classpath.setText(String.join("\n", settings.classpath));
        developmentArguments.setText(String.join("\n", settings.developmentArguments));
        developmentRepository.setText(settings.developmentRepository);
        useProjectCache.setSelected(settings.useProjectMavenCache);
    }
    @Override public void disposeUIResources() {
        javaPath = null; classpath = null; developmentArguments = null;
        developmentRepository = null; useProjectCache = null;
    }
}
