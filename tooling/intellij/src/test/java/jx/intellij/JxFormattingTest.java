package jx.intellij;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.TokenType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import jx.compiler.JxCompiler;
import jx.compiler.SourceFile;

/** Exercises the registered formatter through ordinary authored-file CodeStyleManager calls. */
public final class JxFormattingTest extends LightJavaCodeInsightFixtureTestCase {
    @Override protected LightProjectDescriptor getProjectDescriptor() { return JAVA_25; }
    @Override protected void setUp() throws Exception { super.setUp(); myFixture.setCaresAboutInjection(false); }

    public void testReformatJavaAndMarkupIsIdempotentAndKeepsRenderedText() {
        myFixture.configureByText(JxFileType.INSTANCE, """
                public class Counter{
                int add(int value){return value+1;}
                Object render(int count){
                return (<section  class = "panel">
                <button title = {count+1}>  Count: {count+1}  </button>
                </section>);
                }
                }
                """);
        var compiledBefore = compiledTokens(myFixture.getFile().getText());
        format();
        String formatted = myFixture.getFile().getText();
        assertTrue(formatted, formatted.contains("public class Counter {"));
        assertTrue(formatted, formatted.contains("int add(int value) {"));
        assertTrue(formatted, formatted.contains("return value + 1;"));
        assertTrue(formatted, formatted.contains("<section class=\"panel\">"));
        assertTrue(formatted, formatted.contains("<button title={count + 1}>  Count: {count + 1}  </button>"));
        assertTrue(formatted, formatted.contains("\n    Object render"));
        assertTrue(formatted, formatted.contains("\n            <button"));
        assertEquals("Formatting must preserve generated tokens, including rendered text literals",
                compiledBefore, compiledTokens(formatted));
        format();
        assertEquals(formatted, myFixture.getFile().getText());
    }

    public void testFormattingUsesJavaSpacingPreferences() {
        var manager = CodeStyleSettingsManager.getInstance(getProject());
        var settings = manager.getCurrentSettings().clone();
        settings.getCommonSettings(JavaLanguage.INSTANCE).SPACE_AROUND_ADDITIVE_OPERATORS = false;
        settings.getLanguageIndentOptions(JavaLanguage.INSTANCE).INDENT_SIZE = 2;
        manager.setTemporarySettings(settings);
        try {
            myFixture.configureByText(JxFileType.INSTANCE,
                    "public class Counter{\nint sum(int n){return n + 1;}\nObject render(){return <i/>;}\n}");
            format();
            String formatted = myFixture.getFile().getText();
            assertTrue(formatted, formatted.contains("return n+1;"));
            assertTrue(formatted, formatted.contains("\n  int sum"));
        } finally { manager.dropTemporarySettings(); }
    }

    public void testMeaningfulMarkupSpacesAndStringContentsRemainVerbatim() {
        myFixture.configureByText(JxFileType.INSTANCE, """
                class App {
                    String text = "<i>  untouched  </i>";
                    Object render() {return (<p><b>one</b> <b>two</b>{"  literal  "}</p>);}
                }
                """);
        format();
        String formatted = myFixture.getFile().getText();
        assertTrue(formatted, formatted.contains("\"<i>  untouched  </i>\""));
        assertTrue(formatted, formatted.contains("<b>one</b> <b>two</b>"));
        assertTrue(formatted, formatted.contains("{\"  literal  \"}"));
    }

    public void testReformatSelectionLeavesOtherMethodUntouched() {
        myFixture.configureByText(JxFileType.INSTANCE, """
                class App {
                    int first( int n ){return n+1;}
                    int second( int n ){return n+2;}
                    Object render(){return <i/>;}
                }
                """);
        String source = myFixture.getFile().getText();
        int start = source.indexOf("int first"), end = source.indexOf("    int second");
        WriteCommandAction.runWriteCommandAction(getProject(), () ->
                CodeStyleManager.getInstance(getProject()).reformatText(myFixture.getFile(), start, end));
        assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().contains("int second( int n ){return n+2;}"));
        assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().contains("return n + 1;"));
    }

    public void testJavaContinuationLayoutMatchesNativeJavaFormatter() {
        String source = """
                class Counter{
                int total(){return sum(
                1,
                2);}
                int sum(int x,int y){return x+y;}
                Object render(){return <span/>;}
                }
                """;
        var nativeFile = com.intellij.psi.PsiFileFactory.getInstance(getProject()).createFileFromText(
                "Counter.java", com.intellij.ide.highlighter.JavaFileType.INSTANCE, source.replace("<span/>", "null"));
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            CodeStyleManager.getInstance(getProject()).reformat(nativeFile);
        });
        myFixture.configureByText(JxFileType.INSTANCE, source);
        format();
        String expected = nativeFile.getText(), actual = myFixture.getFile().getText();
        assertEquals(expected.substring(0, expected.indexOf("Object render")),
                actual.substring(0, actual.indexOf("Object render")));
    }

    public void testNestedMarkupInAttributeKeepsOuterTagIndentation() {
        myFixture.configureByText(JxFileType.INSTANCE, """
                class Counter {
                Object render() {
                return (<section title={<span>nested</span>}>
                <p>child</p>
                </section>);
                }
                }
                """);
        var before = compiledTokens(myFixture.getFile().getText());
        format();
        String formatted = myFixture.getFile().getText();
        assertEquals(before, compiledTokens(formatted));
        assertTrue(formatted, formatted.contains("\n            <p>child</p>"));
        format();
        assertEquals(formatted, myFixture.getFile().getText());
    }

    public void testJavaTabPreferencesApplyToMarkupAndJava() {
        var manager = CodeStyleSettingsManager.getInstance(getProject());
        var settings = manager.getCurrentSettings().clone();
        var indent = settings.getLanguageIndentOptions(JavaLanguage.INSTANCE);
        indent.USE_TAB_CHARACTER = true; indent.TAB_SIZE = 4; indent.INDENT_SIZE = 4;
        manager.setTemporarySettings(settings);
        try {
            myFixture.configureByText(JxFileType.INSTANCE, "class Counter{\nObject render(){\nreturn <div>\n<span/>\n</div>;\n}\n}");
            format();
            String formatted = myFixture.getFile().getText();
            assertTrue(formatted, formatted.contains("\n\tObject render"));
            assertTrue(formatted, formatted.contains("\n\t\t\t<span/>"));
        } finally { manager.dropTemporarySettings(); }
    }

    public void testReformatIsOneUndoableOperation() {
        String source = "class Counter{\nObject render(){return <span title = {1+2}>keep  text</span>;}\n}";
        var file = myFixture.addFileToProject("Counter.jx", source);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        format();
        assertFalse(source.equals(file.getText()));
        com.intellij.openapi.ui.TestDialogManager.setTestDialog(com.intellij.openapi.ui.TestDialog.OK, getTestRootDisposable());
        myFixture.performEditorAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_UNDO);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertEquals(source, file.getText());
    }

    public void testMultilineTextAndJavaTextBlocksRemainVerbatim() {
        String literal = "\"\"\"\n    <raw>  text  </raw>\n    \"\"\"";
        String text = "first\n   second  line\nthird";
        myFixture.configureByText(JxFileType.INSTANCE,
                "class Counter{\nString literal=" + literal + ";\nObject render(){return <pre>" + text + "</pre>;}\n}");
        var before = compiledTokens(myFixture.getFile().getText());
        format();
        String formatted = myFixture.getFile().getText();
        assertTrue(formatted, formatted.contains(literal));
        assertTrue(formatted, formatted.contains("<pre>" + text + "</pre>"));
        assertEquals(before, compiledTokens(formatted));
    }

    private void format() {
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            CodeStyleManager.getInstance(getProject()).reformat(myFixture.getFile());
        });
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }

    private static List<String> compiledTokens(String source) {
        var compilation = new JxCompiler().compile(List.of(new SourceFile(Path.of("Counter.jx"), source)));
        assertTrue(compilation.diagnostics().toString(), compilation.successful());
        var lexer = new JavaLexer(LanguageLevel.JDK_25);
        String java = compilation.generatedSources().getFirst().javaSource();
        lexer.start(java);
        var result = new ArrayList<String>();
        while (lexer.getTokenType() != null) {
            if (lexer.getTokenType() != TokenType.WHITE_SPACE)
                result.add(java.substring(lexer.getTokenStart(), lexer.getTokenEnd()));
            lexer.advance();
        }
        return result;
    }
}
