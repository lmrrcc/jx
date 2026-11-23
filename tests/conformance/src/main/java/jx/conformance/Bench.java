package jx.conformance;

import org.teavm.jso.JSBody;

/**
 * Browser hooks shared by every bench case. The reference implementation in
 * {@code reference/bench.js} exposes the same observable contract.
 */
public final class Bench {
    private Bench() {}

    /** Returns the {@code case} query parameter, or an empty string when absent. */
    @JSBody(script = "return new URLSearchParams(location.search).get('case') || '';")
    public static native String caseId();

    /** Appends one entry to the page's ordered {@code __benchLog}, read by the specs. */
    @JSBody(params = "entry", script = "(globalThis.__benchLog = globalThis.__benchLog || []).push(entry);")
    public static native void log(String entry);

    /** The markup a test rendered in Node and handed to the page, or an empty string. */
    @JSBody(script = "return typeof globalThis.__benchServerHtml === 'string' ? globalThis.__benchServerHtml : '';")
    public static native String serverMarkup();

    /** Puts server markup into the root element and marks its button, so that a test can tell
     * whether hydration kept the node. */
    @JSBody(params = "html", script = "var e = document.getElementById('app'); e.innerHTML = html;"
            + "e.querySelector('button').benchServerNode = true;")
    public static native void seed(String html);

    /** Reports a case id that this implementation does not provide. */
    @JSBody(params = "id", script = "var e = document.getElementById('app');"
            + "e.setAttribute('data-bench', 'unknown'); e.textContent = 'unknown case: ' + id;")
    public static native void unknown(String id);
}
