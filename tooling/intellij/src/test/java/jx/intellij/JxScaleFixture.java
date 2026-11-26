package jx.intellij;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import java.util.ArrayList;
import java.util.List;

/** A generated, deterministic application: one shared component and 99 consumers. */
final class JxScaleFixture {
    static final int COMPONENTS = 100;
    static final String ROOT = "app/src/main/jx/";
    final PsiFile counter;
    final List<PsiFile> pages;
    final PsiFile caller;

    JxScaleFixture(CodeInsightTestFixture fixture) {
        this(fixture, COMPONENTS);
    }
    JxScaleFixture(CodeInsightTestFixture fixture, int count) {
        if (count < 2 || count > COMPONENTS) throw new IllegalArgumentException("Fixture requires 2..100 components");
        fixture.addFileToProject("app/pom.xml", "<project><modelVersion>4.0.0</modelVersion></project>");
        counter = fixture.addFileToProject(ROOT + "scale/widgets/Counter.jx", counter("Counter"));
        var files = new ArrayList<PsiFile>();
        for (int index = 0; index < count - 1; index++) {
            files.add(fixture.addFileToProject(ROOT + "scale/pages/Page" + index + ".jx", page(index, "Counter")));
        }
        pages = List.copyOf(files);
        fixture.addFileToProject("app/src/main/java/scale/Helpers.java", """
                package scale;
                public final class Helpers {
                    public static String buildName(String value) { return value; }
                    public static String buildLabel(String value) { return value; }
                }
                """);
        caller = fixture.addFileToProject("app/src/main/java/scale/Caller.java", caller("Counter"));
    }

    static String counter(String symbol) {
        return "\n   " + """
                package scale.widgets;
                // Counter is a public component. This comment is not a symbol reference.
                @jx.Component public final class %s {
                    public %s() {}
                    public record Props(String label, @jx.OptionalProp jx.View children) {}
                    public static jx.View render(Props props) {
                        return <section data-kind="Counter">Counter {props.label()}{props.children()}</section>;
                    }
                }
                """.formatted(symbol, symbol);
    }

    static String page(int index, String symbol) {
        String tag = index % 2 == 0 ? symbol : "scale.widgets." + symbol;
        return "\n" + """
                package scale.pages;
                import scale.Helpers;
                import scale.widgets.%s;
                // Counter in documentation is deliberately unchanged.
                @jx.Component public final class Page%d {
                    public static jx.View render() {
                        %s unused = new %s();
                        String title = Helpers.buildName("Sample-000");
                        return <article data-title="Counter"><%s label={Helpers.buildLabel(title)}>Counter &amp; text</%s></article>;
                    }
                }
                """.formatted(symbol, index, symbol, symbol, tag, tag);
    }

    static String caller(String symbol) {
        return """
                package scale;
                import scale.widgets.%s;
                // Counter remains prose and the string remains literal.
                public final class Caller {
                    public %s make() { return new %s(); }
                    public String text() { return "Counter"; }
                }
                """.formatted(symbol, symbol, symbol);
    }
}
