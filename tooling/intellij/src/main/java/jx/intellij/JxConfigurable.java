package jx.intellij;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.Configurable;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public final class JxConfigurable implements Configurable {
    private JTextField javaPath;
    private JTextArea classpath;
    @Override public String getDisplayName() { return "JX"; }
    @Override public JComponent createComponent() {
        javaPath = new JTextField();
        classpath = new JTextArea(8, 50);
        JPanel java = new JPanel(new GridLayout(2, 1));
        java.add(new JLabel("Java 25 executable (absolute path):")); java.add(javaPath);
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(java, BorderLayout.NORTH);
        JPanel entries = new JPanel(new BorderLayout());
        entries.add(new JLabel("Additional classpath entries, one per line (restart the server after changes):"), BorderLayout.NORTH);
        entries.add(new JScrollPane(classpath), BorderLayout.CENTER);
        panel.add(entries, BorderLayout.CENTER);
        reset();
        return panel;
    }
    private List<String> entries() { return classpath.getText().lines().map(String::trim).filter(line -> !line.isEmpty()).toList(); }
    @Override public boolean isModified() {
        var settings = JxSettings.getInstance().getState();
        return javaPath != null && (!javaPath.getText().trim().equals(settings.javaPath) || !entries().equals(settings.classpath));
    }
    @Override public void apply() throws ConfigurationException {
        if (javaPath.getText().isBlank()) throw new ConfigurationException("Select a Java 25 executable.");
        var settings = new JxSettings.Values();
        settings.javaPath = javaPath.getText().trim(); settings.classpath = entries();
        JxSettings.getInstance().loadState(settings);
    }
    @Override public void reset() {
        var settings = JxSettings.getInstance().getState();
        javaPath.setText(settings.javaPath); classpath.setText(String.join("\n", settings.classpath));
    }
    @Override public void disposeUIResources() { javaPath = null; classpath = null; }
}
