package jx.react;

import java.util.Objects;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBodyImport;
import org.teavm.jso.JSObject;

/** React DOM's resource hints: tell the browser early about servers and files the page will
 * need. React deduplicates equivalent hints, including responsive image source sets and sizes.
 * Hints may be
 * called while rendering, in an event handler or in an effect. URLs are used as given; do not
 * pass a URL taken from untrusted input. */
public final class Resources {
    private Resources() {}

    /** Optional settings shared by the hints; a null component is left out. {@code as} is the
     * kind of resource ({@code "style"}, {@code "script"}, {@code "font"}, {@code "image"}...),
     * {@code precedence} orders stylesheets for {@link #preinit}. Responsive image settings
     * require {@code as="image"}; their syntax and candidate selection belong to the browser.
     * {@code referrerPolicy} controls the preload request's Referer header. Null fields are omitted,
     * not serialized as the string {@code "null"}. */
    public record Options(String as, String crossOrigin, String integrity, String nonce, String fetchPriority,
                          String type, String precedence, String imageSrcSet, String imageSizes, String referrerPolicy) {
        public Options {
            if ((imageSrcSet != null || imageSizes != null) && !"image".equals(as))
                throw new IllegalArgumentException("Responsive image settings require as=image");
        }
        /** Compatibility constructor; responsive image and referrer policy settings are omitted. */
        public Options(String as, String crossOrigin, String integrity, String nonce, String fetchPriority,
                       String type, String precedence) {
            this(as, crossOrigin, integrity, nonce, fetchPriority, type, precedence, null, null, null);
        }
        /** Only the resource kind. */
        public static Options as(String as) { return new Options(Objects.requireNonNull(as), null, null, null, null, null, null); }
        /** No settings. */
        public static Options none() { return new Options(null, null, null, null, null, null, null); }
        /** Returns a copy with the image candidates and sizes. Either may be null to omit that
         * setting; this method requires {@code as="image"} when either setting is present. */
        public Options withImageSources(String imageSrcSet, String imageSizes) {
            return new Options(as, crossOrigin, integrity, nonce, fetchPriority, type, precedence,
                    imageSrcSet, imageSizes, referrerPolicy);
        }
        /** Returns a copy with a browser referrer policy such as {@code "no-referrer"}.
         * Null omits the option. The browser validates the policy token. */
        public Options withReferrerPolicy(String referrerPolicy) {
            return new Options(as, crossOrigin, integrity, nonce, fetchPriority, type, precedence,
                    imageSrcSet, imageSizes, referrerPolicy);
        }
    }

    /** {@code preconnect}: open a connection to the server of {@code href}. */
    public static void preconnect(String href) { preconnect(href, null); }
    /** As {@link #preconnect(String)}; {@code crossOrigin} may be null. */
    public static void preconnect(String href, String crossOrigin) {
        preconnectNative(Objects.requireNonNull(href), crossOrigin);
    }
    /** {@code prefetchDNS}: look up the address of the server of {@code href}. */
    public static void prefetchDNS(String href) { prefetchNative(Objects.requireNonNull(href)); }
    /** {@code preload}: download a resource of kind {@code as} without using it yet. */
    public static void preload(String href, String as) { preload(href, Options.as(as)); }
    /** As {@link #preload(String, String)} with further settings; {@code as} is required. */
    public static void preload(String href, Options options) {
        Objects.requireNonNull(options.as(), "preload needs the resource kind");
        preloadNative(Objects.requireNonNull(href), native_(options));
    }
    /** {@code preinit}: download and run a script, or download and insert a stylesheet;
     * {@code as} is {@code "script"} or {@code "style"}. */
    public static void preinit(String href, String as) { preinit(href, Options.as(as)); }
    /** As {@link #preinit(String, String)} with further settings; {@code as} is required. */
    public static void preinit(String href, Options options) {
        Objects.requireNonNull(options.as(), "preinit needs the resource kind");
        preinitNative(Objects.requireNonNull(href), native_(options));
    }
    /** {@code preloadModule}: download an ECMAScript module without running it. */
    public static void preloadModule(String href) { preloadModule(href, Options.none()); }
    public static void preloadModule(String href, Options options) { preloadModuleNative(Objects.requireNonNull(href), native_(options)); }
    /** {@code preinitModule}: download and run an ECMAScript module. */
    public static void preinitModule(String href) { preinitModule(href, Options.none()); }
    public static void preinitModule(String href, Options options) { preinitModuleNative(Objects.requireNonNull(href), native_(options)); }

    private static JSObject native_(Options options) {
        JSObject result = object();
        if (options.as() != null) set(result, "as", options.as());
        if (options.crossOrigin() != null) set(result, "crossOrigin", options.crossOrigin());
        if (options.integrity() != null) set(result, "integrity", options.integrity());
        if (options.nonce() != null) set(result, "nonce", options.nonce());
        if (options.fetchPriority() != null) set(result, "fetchPriority", options.fetchPriority());
        if (options.type() != null) set(result, "type", options.type());
        if (options.precedence() != null) set(result, "precedence", options.precedence());
        if (options.imageSrcSet() != null) set(result, "imageSrcSet", options.imageSrcSet());
        if (options.imageSizes() != null) set(result, "imageSizes", options.imageSizes());
        if (options.referrerPolicy() != null) set(result, "referrerPolicy", options.referrerPolicy());
        return result;
    }

    @JSBody(script = "return {};") private static native JSObject object();
    @JSBody(params = {"object", "name", "value"}, script = "object[name] = value;") private static native void set(JSObject object, String name, String value);
    @JSBody(params = {"href", "crossOrigin"}, script = "if (crossOrigin === null) ReactDOM.preconnect(href); else ReactDOM.preconnect(href, {crossOrigin: crossOrigin});", imports = @JSBodyImport(alias = "ReactDOM", fromModule = "react-dom")) private static native void preconnectNative(String href, String crossOrigin);
    @JSBody(params = "href", script = "ReactDOM.prefetchDNS(href);", imports = @JSBodyImport(alias = "ReactDOM", fromModule = "react-dom")) private static native void prefetchNative(String href);
    @JSBody(params = {"href", "options"}, script = "ReactDOM.preload(href, options);", imports = @JSBodyImport(alias = "ReactDOM", fromModule = "react-dom")) private static native void preloadNative(String href, JSObject options);
    @JSBody(params = {"href", "options"}, script = "ReactDOM.preinit(href, options);", imports = @JSBodyImport(alias = "ReactDOM", fromModule = "react-dom")) private static native void preinitNative(String href, JSObject options);
    @JSBody(params = {"href", "options"}, script = "ReactDOM.preloadModule(href, options);", imports = @JSBodyImport(alias = "ReactDOM", fromModule = "react-dom")) private static native void preloadModuleNative(String href, JSObject options);
    @JSBody(params = {"href", "options"}, script = "ReactDOM.preinitModule(href, options);", imports = @JSBodyImport(alias = "ReactDOM", fromModule = "react-dom")) private static native void preinitModuleNative(String href, JSObject options);
}
