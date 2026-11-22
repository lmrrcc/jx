package jx.react;

import jx.Promise;
import jx.spi.AsyncRuntime;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSExceptions;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Promises and asynchronous functions on TeaVM's suspendable threads. One such thread runs at a
 * time, so continuations are always delivered from a microtask, where none is running. */
final class AsyncBridge implements AsyncRuntime {
    static final AsyncBridge INSTANCE = new AsyncBridge();
    private static final JSObject THENABLES = weakMap();
    private AsyncBridge() {}

    @JSFunctor interface Task extends JSObject { void run(); }
    @JSFunctor interface Settle extends JSObject { void accept(Object value); }
    @JSFunctor interface Subscribe extends JSObject { void subscribe(JSObject resolve, JSObject reject); }

    @Override public void defer(Runnable task) { microtask(task::run); }

    @Override public void start(Coroutine body) {
        if (inThread()) microtask(() -> startThread(body::run));
        else startThread(body::run);
    }

    @Override public void await(Promise<?> promise) {
        if (!inThread()) throw new IllegalStateException("await is only possible inside Async.run");
        suspend(promise);
    }

    @Async private static native Object suspend(Promise<?> promise);
    private static void suspend(Promise<?> promise, AsyncCallback<Object> callback) {
        promise.whenSettled(value -> callback.complete(null), error -> callback.complete(null));
    }

    /** The JavaScript promise that settles like {@code promise}; the same object on every call,
     * as React's {@code use} requires. A Java failure rejects it with its JavaScript error. */
    static JSObject thenable(Promise<?> promise) {
        return thenable(THENABLES, promise, (resolve, reject) ->
            promise.whenSettled(value -> call(resolve, value), error -> call(reject, JSExceptions.getJSException(error))));
    }

    /** The JX promise that settles like the JavaScript promise or thenable. */
    static Promise<JSObject> promise(JSObject thenable) {
        return Promise.create(resolver -> then(thenable, value -> resolver.resolve((JSObject) value), error -> {
            Throwable java = JSExceptions.getJavaException((JSObject) error);
            resolver.reject(java != null ? java : new IllegalStateException(message(error)));
        }));
    }

    @JSBody(script = "return new WeakMap();") private static native JSObject weakMap();
    @JSBody(params = "task", script = "queueMicrotask(task);") private static native void microtask(Task task);
    @JSBody(script = "return $rt_nativeThread() !== null;") private static native boolean inThread();
    @JSBody(params = "body", script = "$rt_startThread(function() { body(); });") private static native void startThread(Task body);
    @JSBody(params = {"cache", "promise", "subscribe"}, script = "var result = cache.get(promise); if (!result) { result = new Promise(function(resolve, reject) { subscribe(resolve, reject); }); cache.set(promise, result); } return result;")
    private static native JSObject thenable(JSObject cache, Object promise, Subscribe subscribe);
    @JSBody(params = {"settle", "value"}, script = "settle(value);") private static native void call(JSObject settle, Object value);
    @JSBody(params = {"thenable", "onValue", "onError"}, script = "Promise.resolve(thenable).then(function(value) { onValue(value === undefined ? null : value); }, function(error) { onError(error); });")
    private static native void then(JSObject thenable, Settle onValue, Settle onError);
    @JSBody(params = "error", script = "return error && error.message ? String(error.message) : String(error);") private static native String message(Object error);
}
