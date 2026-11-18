package jx.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.APP)
@State(name = "JxSettings", storages = @Storage("jx.xml"))
public final class JxSettings implements PersistentStateComponent<JxSettings.Values> {
    public static final class Values {
        public String javaPath = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        public List<String> classpath = new ArrayList<>();
    }
    private Values values = new Values();
    public static JxSettings getInstance() { return ApplicationManager.getApplication().getService(JxSettings.class); }
    @Override public @NotNull Values getState() { return values; }
    @Override public void loadState(@NotNull Values state) { values = state; }
}
