package jx.compiler;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HookSignaturesTest {
    @Test void markupTextAndEventImplementationChangesKeepTheHookSignature() {
        String before = """
                State<Integer> count = Hooks.useState(0);
                return <button onClick={event -> count.update(value -> value + 1)}>Before {count.value()}</button>;
                """;
        String signature = signature(component(before, ""));
        assertTrue(signature.startsWith("jx-hooks-v1:"));
        assertEquals(signature, signature(component(before.replace("Before", "After").replace("value + 1", "value + 2"), "")));
        assertEquals(signature, signature(component("// layout comment\n" + before, "")));
    }

    @Test void hookOrderSlotNamesTypesAndCodecsResetCompatibility() {
        String before = "State<Integer> first = Hooks.useState(0); State<Integer> second = Hooks.useState(1); return <span/>;";
        String original = signature(component(before, ""));
        assertNotEquals(original, signature(component("State<Integer> second = Hooks.useState(1); State<Integer> first = Hooks.useState(0); return <span/>;", "")));
        assertNotEquals(original, signature(component(before.replace("first =", "renamed ="), "")));
        assertNotEquals(signature(component("var count = Hooks.useState(0); return <span/>;", "")),
                signature(component("var count = Hooks.useState(0L); return <span/>;", "")));
        assertNotEquals(signature(component("State<Integer> count = Hooks.useState(0, StateCodecs.integers()); return <span/>;", "")),
                signature(component("State<Integer> count = Hooks.useState(0, CustomCodec.VERSION_TWO); return <span/>;", "")));
    }

    @Test void expandsLocalCustomHooksAndIgnoresTheirDeclarationOrder() {
        String render = "State<Integer> count = useCounter(); return <span>{count.value()}</span>;";
        String helper = """
                private static State<Integer> useCounter() {
                    State<Integer> value = Hooks.useState(0);
                    return value;
                }
                """;
        String signature = signature(component(render, helper));
        assertFalse(signature.isEmpty());
        assertNotEquals(signature, signature(component(render, helper.replace("return value;", "Ref<Integer> extra = Hooks.useRef(0); return value;"))));
        assertEquals(signature, signature(component(render.replace("<span>", "<b>").replace("</span>", "</b>"), helper)));
    }

    @Test void unresolvedExternalHooksOverloadsAndRecursionDisableStateRestoration() {
        assertEquals("", signature(component("State<Integer> count = External.useCounter(); return <span/>;", "")));
        assertEquals("", signature(component("State<Integer> count = useCounter(0); return <span/>;",
                "static State<Integer> useCounter(int value) { return Hooks.useState(value); }"
                        + "static State<Integer> useCounter(String value) { return Hooks.useState(0); }")));
        assertEquals("", signature(component("State<Integer> count = useCounter(); return <span/>;",
                "static State<Integer> useCounter() { return useCounter(); }")));
        assertEquals("", signature(component("State<Integer> count = hidden(); return <span/>;",
                "static State<Integer> hidden() { return Hooks.useState(0); }")));
    }

    @Test void staticImportsAndHooksInsideMarkupUseTheSameParser() {
        String source = """
                import jx.*;
                import static jx.Hooks.useState;
                @JxComponent public class App {
                    public static View render() {
                        State<Integer> count = useState(0);
                        return <span>{count.value()}</span>;
                    }
                }
                """;
        assertFalse(signature(source).isEmpty());
        assertNotEquals(signature(source), signature(source.replace("{count.value()}", "{count.value()}{Hooks.useId()}")));
    }

    @Test void propsSchemaChangesAndInitializersResetConservatively() {
        String source = """
                import jx.*;
                @JxComponent public class App {
                    public record Props(int initial) {}
                    public static View render(Props props) {
                        var count = Hooks.useState(props.initial());
                        return <span>{count.value()}</span>;
                    }
                }
                """;
        assertNotEquals(signature(source), signature(source.replace("Props(int initial)", "Props(long initial)")));
        assertNotEquals(signature(component("var count = Hooks.useState(0); return <span/>;", "")),
                signature(component("var count = Hooks.useState(1); return <span/>;", "")));
    }

    private String signature(String source) {
        Compilation compilation = new JxCompiler().compile(List.of(new SourceFile(Path.of("App.jx"), source)));
        assertTrue(compilation.successful(), compilation.diagnostics().toString());
        String signature = HookSignatures.compute(compilation.generatedSources(), compilation.components()).get("App");
        assertTrue(compilation.generatedSources().getFirst().javaSource().contains("new jx.Component<>(\"App\", \"" + signature + "\","));
        return signature;
    }

    private String component(String render, String helpers) {
        return "import jx.*; @JxComponent public class App { public static View render() { " + render + " } " + helpers + " }";
    }
}

