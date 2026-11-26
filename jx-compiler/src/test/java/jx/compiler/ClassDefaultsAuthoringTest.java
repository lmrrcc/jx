package jx.compiler;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ClassDefaultsAuthoringTest {
    @TempDir Path directory;
    private final JxCompiler compiler = new JxCompiler();
    private static final String PANEL = """
            package demo;
            import jx.*;
            @Component public final class Panel extends ClassComponent<Panel.Props,Panel.Model,Void> {
                public record Props(String required, @OptionalProp String label, @OptionalProp int initial, @OptionalProp View children) {}
                public record Model(int count) {}
                public static final ClassProps<Props> defaultProps = propsInput().withLabel("fallback").withInitial(7).withChildren(null).build();
                public Panel(Props props) { super(props,new Model(props.initial())); }
                @Override public View render() { return <section>{props().label()}: {state().count()}{props().children()}</section>; }
            }
            """;
    private static final String APP = """
            package demo;
            import jx.*;
            @Component public final class App {
                public static View render() { return <Panel required="value" />; }
            }
            """;

    @Test void markupOmitsNativeDefaultsAndStaticInitializationDoesNotReadTheOuterDescriptor() throws Exception {
        for (boolean pure : List.of(false,true)) {
            String panel = pure ? PANEL.replace("extends ClassComponent", "extends PureComponent") : PANEL;
            var sources = sources(panel,APP);
            Compilation compilation = compiler.compile(sources);
            assertTrue(compilation.successful(),compilation.diagnostics().toString());
            assertTrue(compilation.components().stream().filter(c -> c.simpleName().equals("Panel")).findFirst().orElseThrow().classSpec().defaultProps());
            Path probe = directory.resolve("Probe.java");
            Files.writeString(probe,"""
                    package demo;
                    import jx.*;
                    public final class Probe {
                        public static boolean verify() {
                            View view = App.render();
                            if (!(view instanceof View.ClassPropsView<?,?,?> input)) return false;
                            var original = Panel.propsInput().withRequired("value").build();
                            var undefined = Panel.propsInput().undefinedLabel().build();
                            return Panel.JX.defaultProps() == Panel.defaultProps
                                && Panel.defaultProps.entries().size() == 3
                                && input.input().entries().size() == 1
                                && input.input().entries().getFirst().field().name().equals("required")
                                && input.overrides() == null && input.input().schema() == Panel.JX.propsSchema()
                                && original.entries().size() == 1 && undefined.entries().getFirst().undefined()
                                && Panel.statePatch().withCount(3).build().entries().getFirst().value().equals(3);
                        }
                    }
                    """);
            String output = "defaults-" + pure;
            assertEquals(List.of(),check(compilation,List.of(probe),output));
            try(var loader=new URLClassLoader(new java.net.URL[]{directory.resolve(output).toUri().toURL()},null)) {
                assertEquals(true,loader.loadClass("demo.Probe").getMethod("verify").invoke(null));
            }
            Compilation projection = new Compilation(EditorProjection.project(sources),List.of(),List.of());
            assertEquals(List.of(),check(projection,List.of(),output+"-editor"));
            GeneratedSource generated = projection.generatedSources().stream().filter(g -> g.relativePath().endsWith("Panel.java")).findFirst().orElseThrow();
            String expression="propsInput().withLabel(\"fallback\").withInitial(7).withChildren(null).build()";
            int authored=panel.indexOf(expression), java=generated.sourceMap().generatedOffset(authored).orElseThrow();
            assertEquals(expression,generated.javaSource().substring(java,java+expression.length()));
            assertEquals(new SourceSpan(authored,authored+expression.length()),
                    generated.sourceMap().editableSourceSpan(new SourceSpan(java,java+expression.length())).orElseThrow());
        }
    }

    @Test void requiredPropsAndTypedOverridesKeepAuthoredDiagnosticsAndRealJavaTypes() throws Exception {
        Compilation absent=compiler.compile(sources(PANEL,APP.replace("required=\"value\"","")));
        assertTrue(absent.diagnostics().stream().anyMatch(d -> d.code().equals("JX103") && d.source().endsWith("App.jx")));
        String bad=APP.replace("required=\"value\"", "initial={\"wrong\"} required=\"value\"");
        Compilation compilation=compiler.compile(sources(PANEL,bad));
        assertTrue(compilation.successful(),compilation.diagnostics().toString());
        assertTrue(check(compilation,List.of(),"invalid-prop").stream().anyMatch(d ->
                d.source().endsWith("App.jx") && d.line()==source("App.jx",bad).line(bad.indexOf("<Panel"))),"Type error must map to the authored tag");
        String badRef=APP.replace("required=\"value\"", "required=\"value\" ref={new Ref<Panel>(null)}");
        assertTrue(check(compiler.compile(sources(PANEL,badRef)),List.of(),"invalid-ref").stream().anyMatch(d ->
                d.severity()==Diagnostic.Severity.ERROR && d.source().endsWith("App.jx")),"Raw input lowering must preserve ClassRef type checking");
        String children=APP.replace("<Panel required=\"value\" />", "<Panel initial={0} label={null} required=\"value\"><b>child</b></Panel>");
        assertEquals(List.of(),check(compiler.compile(sources(PANEL,children)),List.of(),"children"));
        String spread=APP.replace("required=\"value\"", "{...new Panel.Props(\"value\",null,0,null)}");
        assertEquals(List.of(),check(compiler.compile(sources(PANEL,spread)),List.of(),"spread"));
    }

    @Test void malformedDeclarationsAndGeneratedBuilderCollisionsFailAtSource() {
        for(String panel:List.of(
                PANEL.replace("public static final ClassProps", "private static final ClassProps"),
                PANEL.replace("public static final ClassProps", "public static ClassProps"),
                PANEL.replace("public static final ClassProps", "public final ClassProps"),
                PANEL.replace("ClassProps<Props>","ClassProps<Model>"),
                PANEL.replace("ClassProps<Props>","ClassProps<?>"),
                PANEL.replace("import jx.*;","import jx.*; import other.ClassProps;"),
                PANEL.replace("public Panel(Props props)","public static Object propsInput; public Panel(Props props)"),
                PANEL.replace("String required,", "String required, String Required,"))) {
            Compilation result=compiler.compile(List.of(source("Panel.jx",panel)));
            assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("JX110") || d.code().equals("JX111")),result.diagnostics().toString());
        }
    }

    @Test void optionalFieldsNeedDefinedDefaultsBeforeTheAuthoredDescriptorIsPublished() throws Exception {
        for(String expression:List.of("propsInput().withLabel(\"fallback\").withChildren(null).build()",
                "propsInput().withLabel(\"fallback\").undefinedInitial().withChildren(null).build()", "null")) {
            String panel=PANEL.replace("propsInput().withLabel(\"fallback\").withInitial(7).withChildren(null).build()",expression);
            Compilation compilation=compiler.compile(List.of(source("Panel.jx",panel)));
            assertTrue(compilation.successful(),compilation.diagnostics().toString());
            String output="invalid-default-"+Math.abs(expression.hashCode());
            assertEquals(List.of(),check(compilation,List.of(),output));
            try(var loader=new URLClassLoader(new java.net.URL[]{directory.resolve(output).toUri().toURL()},null)) {
                ExceptionInInitializerError failure=assertThrows(ExceptionInInitializerError.class,
                        () -> Class.forName("demo.Panel",true,loader));
                assertTrue(failure.getCause().getMessage().contains(expression.equals("null") ? "non-null" : "defined native default: initial"),failure.toString());
            }
        }
    }

    @Test void classesWithoutNativeDefaultsKeepTheirExistingApplicationMemberNames() throws Exception {
        String panel=PANEL.replace("public static final ClassProps<Props> defaultProps = propsInput().withLabel(\"fallback\").withInitial(7).withChildren(null).build();", """
                public static String propsInput() { return "application helper"; }
                public static final class PropsInput {}
                private static void __jxDefaults() {}
                """);
        Compilation compilation=compiler.compile(sources(panel,APP));
        assertTrue(compilation.successful(),compilation.diagnostics().toString());
        assertFalse(compilation.components().stream().filter(c -> c.simpleName().equals("Panel")).findFirst().orElseThrow().classSpec().defaultProps());
        assertEquals(List.of(),check(compilation,List.of(),"existing-member-names"));
    }
    private SourceFile source(String name,String text) { return new SourceFile(directory.resolve(name),text); }
    private List<SourceFile> sources(String panel,String app) { return List.of(source("Panel.jx",panel),source("App.jx",app)); }
    private List<Diagnostic> check(Compilation compilation,List<Path> extra,String output) throws Exception {
        var files=new ArrayList<>(extra);
        try(var paths=Files.walk(Path.of("..","jx-core","src","main","java"))) {
            files.addAll(paths.filter(p -> p.toString().endsWith(".java")).sorted().toList());
        }
        return compiler.check(compilation,files,List.of(),directory.resolve(output));
    }
}
