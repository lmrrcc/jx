package jx.intellij;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/** Actual host-editor actions: no substitution of an injected editor in the fixture. */
public final class JxTypingTest extends LightJavaCodeInsightFixtureTestCase {
    @Override protected LightProjectDescriptor getProjectDescriptor() { return JAVA_25; }
    @Override protected void setUp() throws Exception { super.setUp(); myFixture.setCaresAboutInjection(false); }

    public void testTypingClosesTagAndKeepsCaretInside() {
        myFixture.configureByText("App.jx", "class App { Object render() { return <section<caret>; } }");
        myFixture.type('>');
        myFixture.checkResult("class App { Object render() { return <section><caret></section>; } }");
    }
    public void testTypingDoesNotDuplicateExistingClosingTag() {
        myFixture.configureByText("App.jx", "class App { Object render() { return <section<caret></section>; } }");
        myFixture.type('>');
        myFixture.checkResult("class App { Object render() { return <section><caret></section>; } }");
    }
    public void testClosingTagPreferenceIsRespected() {
        var options = WebEditorOptions.getInstance(); boolean before = options.isAutomaticallyInsertClosingTag();
        try {
            options.setAutomaticallyInsertClosingTag(false);
            myFixture.configureByText("App.jx", "class App { Object render() { return <section<caret>; } }");
            myFixture.type('>');
            myFixture.checkResult("class App { Object render() { return <section><caret>; } }");
        } finally { options.setAutomaticallyInsertClosingTag(before); }
    }
    public void testSelfClosingTagAndJavaOperatorsRemainUnchanged() {
        myFixture.configureByText("App.jx", "class App { Object render() { return <Child/<caret>; } }");
        myFixture.type('>');
        myFixture.checkResult("class App { Object render() { return <Child/><caret>; } }");
        myFixture.configureByText("Other.jx", "class Other { boolean test(int a, int b) { return a <caret> b; } }");
        myFixture.type('>');
        assertFalse(myFixture.getFile().getText().contains("</"));
    }
    public void testAttributeQuotePair() {
        myFixture.configureByText("App.jx", "class App { Object render() { return <section title=<caret>/>; } }");
        assertTrue("Host quote handler must be registered", com.intellij.codeInsight.editorActions.TypedHandler
                .getQuoteHandler(myFixture.getFile(), myFixture.getEditor()) instanceof JxQuoteHandler);
        myFixture.type('"');
        assertEquals("class App { Object render() { return <section title=\"\"/>; } }", myFixture.getEditor().getDocument().getText());
        myFixture.checkResult("class App { Object render() { return <section title=\"<caret>\"/>; } }");
    }
    public void testEnterBetweenTags() {
        myFixture.configureByText("App.jx", "class App {\n    Object render() {\n        return <section><caret></section>;\n    }\n}");
        myFixture.type('\n');
        myFixture.checkResult("class App {\n    Object render() {\n        return <section>\n            <caret>\n        </section>;\n    }\n}");
    }
    public void testMarkupCommentActionRoundTrip() {
        String text = "class App { Object render() { return <section>\n    <span>hello</span>\n</section>; } }";
        myFixture.configureByText("App.jx", text);
        int offset = text.indexOf("<span>");
        myFixture.getEditor().getCaretModel().moveToOffset(offset);
        myFixture.performEditorAction("CommentByLineComment");
        com.intellij.psi.PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().contains("<!--<span>hello</span>-->"));
        myFixture.getEditor().getCaretModel().moveToOffset(offset);
        myFixture.performEditorAction("CommentByLineComment");
        com.intellij.psi.PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        myFixture.checkResult(text);
    }
    public void testJavaCommentActionInsideMethod() {
        myFixture.configureByText("App.jx", "class App { Object render() {\n    int <caret>count = 1;\n    return <span>{count}</span>;\n} }");
        myFixture.performEditorAction("CommentByLineComment");
        com.intellij.psi.PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        String text = myFixture.getFile().getText();
        assertTrue(text, text.contains("//") && text.contains("int count = 1;"));
        assertTrue(text, text.contains("<span>{count}</span>"));
    }
    public void testMarkupBlockCommentRoundTrip() {
        String text = "class App { Object render() { return <section><span>hello</span></section>; } }";
        myFixture.configureByText("App.jx", text);
        int start = text.indexOf("<span>"), end = text.indexOf("</span>") + 7;
        myFixture.getEditor().getSelectionModel().setSelection(start, end);
        myFixture.performEditorAction("CommentByBlockComment");
        com.intellij.psi.PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().contains("<!--<span>hello</span>-->"));
        myFixture.getEditor().getSelectionModel().setSelection(start, end + 7);
        myFixture.performEditorAction("CommentByBlockComment");
        myFixture.checkResult(text);
    }
    public void testMarkupTextThatLooksLikeJavaCommentIsNotDeleted() {
        myFixture.configureByText("App.jx", "class App { Object render() { return <span>\n// path<caret>\n</span>; } }");
        myFixture.performEditorAction("CommentByLineComment");
        com.intellij.psi.PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().contains("<!--// path-->"));
    }
    public void testJavaLineCommentIsNotRecognizedAsBlockComment() {
        myFixture.configureByText("App.jx", "class App {\n// keep last two chars XY\n}");
        var commenter = new JxCommenter();
        var document = myFixture.getEditor().getDocument();
        int start = document.getText().indexOf("//"), end = document.getText().indexOf("XY") + 2;
        var state = commenter.createBlockCommentingState(start, end, document, myFixture.getFile());
        assertNull(commenter.getBlockCommentRange(start, end, document, state));
    }
}
