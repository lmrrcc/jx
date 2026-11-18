package jx.example.todo;

import jx.Context;
import jx.elm.Store;
import jx.router.Router;

/** One model owner per application, exposed to descendants through React context. */
public record Services(Store<TodoProgram.Model, TodoProgram.Message> store, TodoProgram program, Router router) {
    public static final Context<Services> CONTEXT = new Context<>(null);
}
