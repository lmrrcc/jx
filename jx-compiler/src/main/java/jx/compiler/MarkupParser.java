package jx.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static jx.compiler.Syntax.*;

/** A lexical Java scanner coupled to a recursive markup parser; no Java text is rewritten by regex. */
final class MarkupParser {
    private static final int MAX_DEPTH = 128;
    private static final int MAX_RECOVERY_DIAGNOSTICS = 128;
    private static final Set<String> EXPRESSION_PREFIX = Set.of("return", "yield", "throw", "case");
    private final SourceFile source;
    private final String text;
    private final boolean editor;
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private int cursor;
    private int depth;

    MarkupParser(SourceFile source) { this(source, false); }

    /** Editor recovery retains authored Java slices; it never inserts characters into the source. */
    MarkupParser(SourceFile source, boolean editor) {
        this.source = source; this.text = source.content(); this.editor = editor;
    }

    ParsedSource parse() {
        if (text.length() > 4 * 1024 * 1024) {
            diagnostics.add(Diagnostic.error(source, "JX001", "Source exceeds the 4 MiB limit", new SourceSpan(0, 0)));
            return new ParsedSource(source, List.of(), diagnostics);
        }
        List<Node> nodes;
        try { nodes = java(false); }
        catch (ParseFailure failure) {
            diagnostics.add(Diagnostic.error(source, failure.code, failure.getMessage(),
                    new SourceSpan(failure.offset, Math.min(text.length(), failure.offset + 1))));
            nodes = List.of();
        }
        return new ParsedSource(source, nodes, diagnostics);
    }

    private List<Node> java(boolean untilBrace) {
        List<Node> result = new ArrayList<>();
        int start = cursor, braces = 0, parentheses = 0, brackets = 0;
        boolean expressionExpected = true;
        while (cursor < text.length()) {
            char c = text.charAt(cursor);
            if (Character.isWhitespace(c)) { cursor++; continue; }
            if (starts("//")) { skipLineComment(); continue; }
            if (starts("/*")) { skipBlockComment(); continue; }
            if (c == '"' || c == '\'') { skipQuoted(); expressionExpected = false; continue; }
            if (untilBrace && braces == 0 && (starts("/>") || starts("</"))) {
                if (editor) break;
                fail("JX002", "Unterminated Java interpolation; expected '}' before tag delimiter", Math.max(0, start - 1));
            }
            // A missing interpolation brace must not consume the enclosing return statement/class.
            // Nested calls, array expressions and block lambdas keep their own delimiters.
            if (editor && untilBrace && braces == 0 && parentheses == 0 && brackets == 0
                    && (c == ';' || c == ')' || c == ']' || (c == '>' && previousNonWhitespace(cursor) == '.'))) break;
            if (c == '}' && braces == 0 && untilBrace) break;
            if (c == '<' && expressionExpected && markupAhead()) {
                if (cursor > start) result.add(new JavaText(new SourceSpan(start, cursor)));
                result.add(element());
                start = cursor;
                expressionExpected = false;
                continue;
            }
            if (Character.isJavaIdentifierStart(c)) {
                int wordStart = cursor++;
                while (cursor < text.length() && Character.isJavaIdentifierPart(text.charAt(cursor))) cursor++;
                expressionExpected = EXPRESSION_PREFIX.contains(text.substring(wordStart, cursor));
                continue;
            }
            if (Character.isDigit(c)) {
                cursor++;
                while (cursor < text.length() && (Character.isJavaIdentifierPart(text.charAt(cursor))
                        || text.charAt(cursor) == '.')) cursor++;
                expressionExpected = false;
                continue;
            }
            if (starts("->")) { cursor += 2; expressionExpected = true; continue; }
            if (starts("::")) { cursor += 2; expressionExpected = false; continue; }
            if (starts("++") || starts("--")) { cursor += 2; continue; }
            if (c == '{') braces++;
            if (c == '}') braces--;
            if (c == '(') parentheses++;
            if (c == ')') parentheses--;
            if (c == '[') brackets++;
            if (c == ']') brackets--;
            expressionExpected = switch (c) {
                case '(', '[', '{', '=', ',', ';', ':', '?', '!', '~', '+', '-', '*', '/', '%', '&', '|', '^' -> true;
                default -> false;
            };
            cursor++;
        }
        if (cursor > start) result.add(new JavaText(new SourceSpan(start, cursor)));
        if (untilBrace && cursor == text.length() && !editor)
            fail("JX002", "Unterminated Java interpolation; expected '}'", cursor);
        return result;
    }

    private boolean markupAhead() {
        return cursor + 1 < text.length() && (text.charAt(cursor + 1) == '>'
                || Character.isJavaIdentifierStart(text.charAt(cursor + 1)));
    }

    private Element element() {
        if (++depth > MAX_DEPTH) fail("JX003", "Markup nesting exceeds " + MAX_DEPTH, cursor);
        int start = cursor++;
        int nameStart = cursor;
        String name = starts(">") ? "" : name();
        SourceSpan nameSpan = new SourceSpan(nameStart, cursor);
        List<Attribute> attributes = new ArrayList<>();
        if (!name.isEmpty()) {
            while (true) {
                boolean separated = whitespace();
                if (editor && (cursor == text.length() || javaTail())) {
                    report("JX008", "Unterminated element '" + name + "'", start);
                    depth--;
                    return new Element(new SourceSpan(start, cursor), name, nameSpan, attributes, List.of());
                }
                if (starts("/>")) {
                    cursor += 2; depth--;
                    return new Element(new SourceSpan(start, cursor), name, nameSpan, attributes, List.of());
                }
                if (starts(">")) break;
                if (!separated) {
                    if (!editor) fail("JX004", "Expected whitespace, '>' or '/>' after " + name, cursor);
                    report("JX004", "Expected whitespace, '>' or '/>' after " + name, cursor);
                }
                if (editor && !Character.isJavaIdentifierStart(text.charAt(cursor))) {
                    // A partial property/tag delimiter is markup, not editable Java. Leave a new
                    // opening tag to the child parser; consume other punctuation to make progress.
                    if (starts("<")) break;
                    cursor++;
                    continue;
                }
                int attributeStart = cursor;
                String attribute = name();
                SourceSpan attributeName = new SourceSpan(attributeStart, cursor);
                int afterName = cursor;
                whitespace();
                String literal = null;
                Interpolation expression = null;
                if (starts("=")) {
                    cursor++; whitespace();
                    if (starts("{")) expression = interpolation();
                    else if (starts("\"") || starts("'")) {
                        char quote = text.charAt(cursor++);
                        int valueStart = cursor;
                        while (cursor < text.length() && text.charAt(cursor) != quote) cursor++;
                        if (cursor == text.length() && !editor) fail("JX005", "Unterminated attribute string", valueStart);
                        literal = entities(text.substring(valueStart, cursor), valueStart);
                        if (cursor < text.length()) cursor++;
                        else report("JX005", "Unterminated attribute string", valueStart);
                    } else {
                        if (!editor) fail("JX006", "Attribute values require quotes or a Java expression in braces", cursor);
                        report("JX006", "Attribute values require quotes or a Java expression in braces", cursor);
                    }
                } else cursor = afterName;
                attributes.add(new Attribute(attribute, new SourceSpan(attributeStart, cursor), attributeName, literal, expression));
            }
        }
        if (editor && !starts(">")) report("JX014", "Expected '>'", cursor);
        else expect(">", "Expected '>'");
        List<Node> children = new ArrayList<>();
        while (cursor < text.length()) {
            if (editor && javaTail()) break;
            if (starts("<!--")) {
                skipMarkupComment();
                continue;
            }
            if (starts("</")) {
                int closeStart = cursor;
                cursor += 2;
                String close;
                if (name.isEmpty() && starts(">")) close = "";
                else if (editor && (cursor == text.length() || !Character.isJavaIdentifierStart(text.charAt(cursor)))) close = "";
                else close = name();
                whitespace();
                if (editor && !starts(">")) report("JX014", "Expected '>' after closing tag", cursor);
                else expect(">", "Expected '>' after closing tag");
                if (!name.equals(close)) {
                    if (!editor) fail("JX007", "Closing tag '" + close + "' does not match '" + name + "'", cursor - close.length() - 1);
                    report("JX007", "Closing tag '" + close + "' does not match '" + name + "'", closeStart);
                    // Let an ancestor consume its own closing tag instead of discarding its subtree.
                    if (!close.isEmpty()) cursor = closeStart;
                }
                depth--;
                return new Element(new SourceSpan(start, cursor), name, nameSpan, attributes, children);
            }
            if (starts("<")) {
                if (editor && !markupAhead()) {
                    report("JX010", "Expected element or property name", cursor++);
                    continue;
                }
                children.add(element());
            } else if (starts("{")) children.add(interpolation());
            else {
                int textStart = cursor;
                while (cursor < text.length() && text.charAt(cursor) != '<' && text.charAt(cursor) != '{'
                        && !(editor && javaTail())) cursor++;
                children.add(new Text(new SourceSpan(textStart, cursor), entities(text.substring(textStart, cursor), textStart)));
            }
        }
        String message = "Unterminated " + (name.isEmpty() ? "fragment" : "element '" + name + "'");
        if (!editor) fail("JX008", message, start);
        report("JX008", message, start);
        depth--;
        return new Element(new SourceSpan(start, cursor), name, nameSpan, attributes, children);
    }

    private Interpolation interpolation() {
        int start = cursor++;
        if (++depth > MAX_DEPTH) fail("JX003", "Markup nesting exceeds " + MAX_DEPTH, start);
        List<Node> expression = java(true);
        if (text.substring(start + 1, cursor).isBlank()) {
            if (!editor) fail("JX009", "Java interpolation cannot be empty", start);
            report("JX009", "Java interpolation cannot be empty", start);
        }
        if (starts("}")) cursor++;
        else report("JX002", "Unterminated Java interpolation; expected '}'", cursor);
        depth--;
        return new Interpolation(new SourceSpan(start, cursor), expression);
    }

    private String name() {
        int start = cursor;
        if (cursor >= text.length() || !Character.isJavaIdentifierStart(text.charAt(cursor)))
            fail("JX010", "Expected element or property name", cursor);
        cursor++;
        while (cursor < text.length()) {
            char c = text.charAt(cursor);
            if (Character.isJavaIdentifierPart(c) || c == '-' || c == ':' || c == '.') cursor++;
            else break;
        }
        return text.substring(start, cursor);
    }

    private void skipLineComment() {
        cursor += 2;
        while (cursor < text.length() && text.charAt(cursor) != '\n') cursor++;
    }

    private void skipBlockComment() {
        int start = cursor;
        int end = text.indexOf("*/", cursor + 2);
        if (end < 0) {
            if (!editor) fail("JX011", "Unterminated Java block comment", start);
            report("JX011", "Unterminated Java block comment", start);
            cursor = text.length();
            return;
        }
        cursor = end + 2;
    }

    /** Markup comments are neither rendered children nor Java interpolation contexts. */
    private void skipMarkupComment() {
        int start = cursor;
        int end = text.indexOf("-->", cursor + 4);
        if (end < 0) {
            if (!editor) fail("JX015", "Unterminated markup comment; expected '-->'", start);
            report("JX015", "Unterminated markup comment; expected '-->'", start);
            cursor = text.length();
            return;
        }
        cursor = end + 3;
    }

    private void skipQuoted() {
        int start = cursor;
        if (starts("\"\"\"")) {
            cursor += 3;
            while (cursor < text.length()) {
                if (text.charAt(cursor) == '\\') { cursor = Math.min(text.length(), cursor + 2); continue; }
                if (starts("\"\"\"")) { cursor += 3; return; }
                cursor++;
            }
        } else {
            char quote = text.charAt(cursor++);
            while (cursor < text.length()) {
                char c = text.charAt(cursor++);
                if (c == '\\') cursor = Math.min(text.length(), cursor + 1);
                else if (c == quote) return;
                else if (c == '\n' || c == '\r') break;
            }
        }
        if (!editor) fail("JX012", "Unterminated Java string, character literal, or text block", start);
        report("JX012", "Unterminated Java string, character literal, or text block", start);
    }

    private String entities(String value, int sourceStart) {
        StringBuilder decoded = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '&') { decoded.append(c); continue; }
            int end = value.indexOf(';', i + 1);
            if (end < 0) {
                if (!editor) fail("JX013", "Escape literal '&' as &amp;", sourceStart + i);
                // No remaining entity can terminate; copy once instead of rescanning every '&'.
                decoded.append(value, i, value.length());
                break;
            }
            String entity = value.substring(i + 1, end);
            String replacement = switch (entity) {
                case "amp" -> "&"; case "lt" -> "<"; case "gt" -> ">";
                case "quot" -> "\""; case "apos" -> "'"; case "nbsp" -> "\u00a0";
                default -> null;
            };
            if (replacement == null && entity.startsWith("#")) {
                try {
                    int cp = Integer.parseInt(entity.substring(entity.startsWith("#x") || entity.startsWith("#X") ? 2 : 1),
                            entity.startsWith("#x") || entity.startsWith("#X") ? 16 : 10);
                    if (!Character.isValidCodePoint(cp) || cp >= 0xD800 && cp <= 0xDFFF || cp == 0)
                        throw new NumberFormatException();
                    replacement = new String(Character.toChars(cp));
                } catch (NumberFormatException ex) {
                    if (!editor) fail("JX013", "Invalid numeric entity '&" + entity + ";'", sourceStart + i);
                }
            }
            if (replacement == null) {
                if (!editor) fail("JX013", "Unknown entity '&" + entity + ";'", sourceStart + i);
                replacement = value.substring(i, end + 1);
            }
            decoded.append(replacement); i = end;
        }
        return decoded.toString();
    }

    private boolean whitespace() {
        int before = cursor;
        while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) cursor++;
        return cursor != before;
    }
    private boolean starts(String token) { return text.startsWith(token, cursor); }
    private char previousNonWhitespace(int offset) {
        while (offset > 0 && Character.isWhitespace(text.charAt(offset - 1))) offset--;
        return offset == 0 ? '\0' : text.charAt(offset - 1);
    }
    private boolean javaTail() {
        return starts(";") || starts(")") || starts("]") || starts("}");
    }
    private void report(String code, String message, int offset) {
        if (diagnostics.size() >= MAX_RECOVERY_DIAGNOSTICS) return;
        int start = Math.clamp(offset, 0, text.length());
        diagnostics.add(Diagnostic.error(source, code, message, new SourceSpan(start, Math.min(text.length(), start + 1))));
    }
    private void expect(String token, String message) {
        if (!starts(token)) fail("JX014", message, cursor);
        cursor += token.length();
    }
    private void fail(String code, String message, int offset) {
        throw new ParseFailure(code, message, Math.clamp(offset, 0, text.length()));
    }
    private static final class ParseFailure extends RuntimeException {
        final String code;
        final int offset;
        ParseFailure(String code, String message, int offset) { super(message); this.code = code; this.offset = offset; }
    }
}
