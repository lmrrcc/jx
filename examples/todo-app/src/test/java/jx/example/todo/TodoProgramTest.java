package jx.example.todo;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import jx.elm.Command;
import jx.elm.Store;
import org.junit.jupiter.api.Test;

class TodoProgramTest {
    @Test void immutableTransitionsValidateInputAndDescribeEffectsWithoutRunningThem() {
        var effects = new AtomicInteger();
        TodoProgram.Gateway gateway = new TodoProgram.Gateway() {
            Command<TodoProgram.Message> command() { return new Command<>("test", ignored -> { effects.incrementAndGet(); return () -> {}; }); }
            public Command<TodoProgram.Message> load() { return command(); }
            public Command<TodoProgram.Message> create(String title) { return command(); }
            public Command<TodoProgram.Message> update(Todo todo) { return command(); }
            public Command<TodoProgram.Message> remove(Todo todo) { return command(); }
        };
        var program = new TodoProgram(gateway);
        var rows = new ArrayList<Todo>();
        var model = new TodoProgram.Model(rows, TodoProgram.Phase.READY, "", 0);
        rows.add(new Todo("x", "Mutable caller", false, 0));
        assertTrue(model.todos().isEmpty());
        var invalid = program.update(model, new TodoProgram.Create("   "));
        assertEquals(TodoProgram.Phase.ERROR, invalid.model().phase());
        assertTrue(invalid.commands().isEmpty());
        var saving = program.update(model, new TodoProgram.Create("Write Java"));
        assertEquals(TodoProgram.Phase.SAVING, saving.model().phase());
        assertEquals(1, saving.commands().size());
        assertEquals(0, effects.get());
        var saved = program.update(saving.model(), new TodoProgram.Saved(new Todo("1", "Write Java", false, 0), true));
        assertEquals(1, saved.model().todos().size());
        assertEquals(1, saved.model().createdCount());
        assertTrue(model.todos().isEmpty());
        try (var store = new Store<>(program)) {
            assertEquals(0, effects.get());
            store.start();
            assertEquals(1, effects.get());
            store.dispatch(new TodoProgram.Loaded(List.of()));
            store.dispatch(new TodoProgram.Create("Second effect"));
            assertEquals(2, effects.get());
        }
    }
}
