package jx.fixture.ssr;

import jx.Attributes;
import jx.ComponentType;
import jx.Views;
import jx.react.React;

/** Identical component/Suspense structure on the server and during hydration. */
public final class TodoRoot {
    private TodoRoot() {}
    public static final ComponentType<TodoPage.Props> JX = new ComponentType<>("ProtectedTodoRoot", props ->
            React.suspense(Views.component(TodoPage.JX, props),
                    Views.element("p", Attributes.create().attr("id", "ssr-loading"), "Loading protected data")));
}
