package jx;

import java.util.Objects;
import java.util.function.Function;

final class ResourceHook {
    private ResourceHook() {}
    private record Entry<T, E>(Object request, Resource<T, E> snapshot) {}

    static <K, T, E> Resource<T, E> use(K key, Function<? super K, ? extends Task<T, E>> loader) {
        Objects.requireNonNull(loader);
        Object request = Hooks.useMemo(Object::new, Dependencies.of(key));
        State<Entry<T, E>> state = Hooks.useStateLazy(() -> new Entry<>(request, new Resource.Loading<>()));
        Hooks.useEffect(() -> {
            state.set(new Entry<>(request, new Resource.Loading<>()));
            var owner = new TaskOwner<T, E>(result -> {
                Resource<T, E> snapshot = switch (result) {
                    case Task.Success<T, E> success -> new Resource.Ready<>(success.value());
                    case Task.Failure<T, E> failure -> new Resource.Failed<>(failure.error());
                };
                state.set(new Entry<>(request, snapshot));
            });
            owner.start(Objects.requireNonNull(loader.apply(key), "Resource loader must return a task"));
            return owner::cancel;
        }, Dependencies.of(request));
        // A key can render before the previous effect is cleaned up. Its old
        // completion must never appear as data for this new key, even then.
        return state.value().request() == request ? state.value().snapshot() : new Resource.Loading<>();
    }
}
