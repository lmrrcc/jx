package jx.react;

import java.util.Objects;
import java.util.function.Supplier;
import jx.Async;
import jx.Promise;
import jx.Ref;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBodyImport;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** React test utilities for browser-compiled Java. These are test-host APIs,
 * not application scheduling primitives. The host owns React's test environment setting
 * ({@code globalThis.IS_REACT_ACT_ENVIRONMENT = true}) and restores it when finished. */
public final class ReactTesting {
    private ReactTesting() {}

    /** Runs a synchronous callback through React's native synchronous {@code act} scope.
     * React can flush synchronous updates before this method returns, but work that suspends or
     * crosses an asynchronous boundary still needs the returned non-null promise to be awaited.
     * Always await it before starting another independent scope. Prefer the asynchronous
     * {@link #act(Supplier)} form for new tests; React plans to remove synchronous scopes.
     *
     * <p>The callback runs once. Thrown callback/flush failures reject the result; a production
     * build rejects before invoking the callback. Java throwable identity is preserved. Null
     * callbacks are rejected immediately. The test host owns the act environment setting and
     * all calls run on the single rendering thread. No cancellation or overlap guarantee is
     * added by this overload. Use a typed {@code Runnable} when a Java lambda is also compatible
     * with the asynchronous supplier overload. */
    public static Promise<Void> act(Runnable action) {
        Objects.requireNonNull(action);
        Async.installRuntime(AsyncBridge.INSTANCE);
        return Promise.create(resolver -> {
            JSObject completion = actNative(() -> {
                action.run();
                return null; // synchronous test scope
            });
            React.promise(completion).whenSettled(ignored -> resolver.resolve(null), resolver::reject);
        });
    }

    /** Runs an asynchronous test scope through React's {@code act} and settles after React has
     * flushed the updates and effects it schedules. Always await the returned promise before
     * asserting or starting another scope; nested scopes must also be awaited. A Java test may
     * use {@code Async.await(ReactTesting.act(() -> Async.run(() -> ...)))} inside {@code Async.run}.
     *
     * <p>The callback runs once and must return a non-null promise. Its result, including null,
     * retains Java identity. A thrown/rejected failure or React flush failure rejects the result.
     * A production React build rejects because it does not implement {@code act}. This method
     * offers the awaited asynchronous contract only; it does not provide synchronous flushing,
     * cancellation, or overlapping independent scopes. All calls use the single rendering thread.
     * A null callback is rejected immediately with {@link NullPointerException}. */
    public static <T> Promise<T> act(Supplier<Promise<T>> action) {
        Objects.requireNonNull(action);
        Async.installRuntime(AsyncBridge.INSTANCE);
        return Promise.create(resolver -> {
            Ref<T> result = new Ref<>(null);
            JSObject completion = actNative(() -> React.thenable(
                    Objects.requireNonNull(action.get(), "The act callback returned null").map(value -> {
                        result.set(value);
                        return null;
                    })));
            React.promise(completion).whenSettled(ignored -> resolver.resolve(result.current()), resolver::reject);
        });
    }

    @JSFunctor interface Scope extends JSObject { JSObject run(); }
    @JSBody(params = "scope", script = "if (typeof React.act !== 'function') throw new Error('React act requires a development build');"
            + " return Promise.resolve(React.act(function() { return scope(); }));",
            imports = @JSBodyImport(alias = "React", fromModule = "react"))
    private static native JSObject actNative(Scope scope);
}
