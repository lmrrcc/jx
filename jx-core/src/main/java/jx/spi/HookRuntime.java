package jx.spi;

import jx.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Browser renderer contract, installed once per Java runtime by the renderer entry point. */
public interface HookRuntime {
    <T> State<T> state(Supplier<T> initial, StateCodec<T> codec);
    <S, A> ReducerState<S, A> reducer(BiFunction<S, A, S> reducer, S initial, StateCodec<S> codec);
    void effect(Effect effect, Dependencies dependencies, boolean layout);
    <S, A> ReducerState<S, A> reducerLazy(BiFunction<S, A, S> reducer, Supplier<S> initial, StateCodec<S> codec);
    void insertionEffect(Effect effect, Dependencies dependencies);
    <A> Consumer<A> effectEvent(Consumer<A> callback);
    /** {@code format} is null when the value is shown as it is. */
    <T> void debugValue(T value, Function<T, String> format);
    <T> Ref<T> ref(T initial);
    <H> void imperativeHandle(Ref<H> ref, Supplier<H> create, Dependencies dependencies);
    <T> T memo(Supplier<T> calculation, Dependencies dependencies);
    <T> T context(Context<T> context);
    <T> T externalStore(ExternalStore<T> store);
    String id();
}
