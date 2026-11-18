package jx.elm;

import jx.View;
import java.util.List;

/** Elm-style application contract. init/update/subscriptions/view are pure; effects live in descriptions. */
public interface Program<S, M> {
    Transition<S, M> init();
    Transition<S, M> update(S model, M message);
    View view(S model, Dispatch<M> dispatch);
    default List<Subscription<M>> subscriptions(S model) { return List.of(); }
}
