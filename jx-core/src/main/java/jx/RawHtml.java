package jx;

import java.util.Objects;

/** Markup that the browser parses as HTML, for the {@code dangerouslySetInnerHTML} property.
 *
 * <p>Nothing here escapes or sanitizes. Markup that contains text from a user, a request or any
 * other source the application does not control lets that source run script in the page. Pass
 * only markup the application wrote itself or that a sanitizer has cleaned. Prefer ordinary
 * elements and text, which are always escaped. */
public final class RawHtml {
    private final String html;
    private RawHtml(String html) { this.html = html; }
    /** Wraps markup as it is. The name states what the caller takes responsibility for. */
    public static RawHtml unsanitized(String html) { return new RawHtml(Objects.requireNonNull(html)); }
    public String html() { return html; }
}
