package jx.example.todo;

import jx.elm.Store;
import jx.react.React;
import jx.router.Router;

public final class BrowserMain {
    private BrowserMain() {}
    public static void main(String[] args) {
        var program = new TodoProgram(new TodoGateway());
        var store = new Store<>(program);
        React.mountStrict("root", App.JX, new App.Props(new Services(store, program, new Router())));
    }
}
