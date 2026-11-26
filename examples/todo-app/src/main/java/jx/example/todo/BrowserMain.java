package jx.example.todo;

import jx.elm.Store;
import jx.react.React;
import jx.react.HotReload;
import jx.router.Router;

public final class BrowserMain {
    private BrowserMain() {}
    public static void main(String[] args) {
        try (var probe = new Router()) {
            if (probe.snapshot().queryParameter("fixture").orElse("").equals("contract")) {
                React.mountStrict("root", ContractProof.JX, null);
                return;
            }
        }
        var program = new TodoProgram(new TodoGateway());
        var store = new Store<>(program);
        HotReload.trackStore("todo-application", "todo-store-v1", store, new TodoStateCodec());
        React.mountStrict("root", App.JX, new App.Props(new Services(store, program, new Router())));
    }
}
