package jx.example.todo;

import jx.example.contract.Todo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jx.View;
import jx.Views;
import jx.elm.*;

/** Pure workflow; commands describe HTTP work interpreted by the store after committing a snapshot. */
public final class TodoProgram implements Program<TodoProgram.Model, TodoProgram.Message> {
    public enum Phase { LOADING, READY, SAVING, ERROR }
    public record Model(List<Todo> todos, Phase phase, String error, int createdCount) {
        public Model { todos = List.copyOf(todos); Objects.requireNonNull(phase); Objects.requireNonNull(error); }
        public boolean busy() { return phase == Phase.LOADING || phase == Phase.SAVING; }
    }
    public sealed interface Message permits Reload, Loaded, Failed, Create, Toggle, Remove, Saved, Removed {}
    public record Reload() implements Message {}
    public record Loaded(List<Todo> todos) implements Message { public Loaded { todos = List.copyOf(todos); } }
    public record Failed(String error) implements Message {}
    public record Create(String title) implements Message {}
    public record Toggle(Todo todo) implements Message {}
    public record Remove(Todo todo) implements Message {}
    public record Saved(Todo todo, boolean created) implements Message {}
    public record Removed(String id) implements Message {}
    public interface Gateway {
        Command<Message> load();
        Command<Message> create(String title);
        Command<Message> update(Todo todo);
        Command<Message> remove(Todo todo);
    }
    private final Gateway gateway;
    public TodoProgram(Gateway gateway) { this.gateway = Objects.requireNonNull(gateway); }
    @Override public Transition<Model, Message> init() {
        return new Transition<>(new Model(List.of(), Phase.LOADING, "", 0), List.of(gateway.load()));
    }
    @Override public Transition<Model, Message> update(Model model, Message message) {
        return switch (message) {
            case Reload ignored -> model.phase() == Phase.SAVING ? Transition.stay(model)
                    : new Transition<>(new Model(model.todos(), Phase.LOADING, "", model.createdCount()), List.of(gateway.load()));
            case Loaded loaded -> Transition.stay(new Model(loaded.todos(), Phase.READY, "", model.createdCount()));
            case Failed failed -> Transition.stay(new Model(model.todos(), Phase.ERROR, failed.error(), model.createdCount()));
            case Create create -> {
                String title = create.title().strip();
                if (model.busy()) yield Transition.stay(model);
                if (title.isEmpty() || title.length() > 200) yield Transition.stay(new Model(model.todos(), Phase.ERROR, "Use a title between 1 and 200 characters", model.createdCount()));
                yield saving(model, gateway.create(title));
            }
            case Toggle toggle -> model.busy() ? Transition.stay(model) : saving(model,
                    gateway.update(new Todo(toggle.todo().id(), toggle.todo().title(), !toggle.todo().completed(), toggle.todo().version())));
            case Remove remove -> model.busy() ? Transition.stay(model) : saving(model, gateway.remove(remove.todo()));
            case Saved saved -> {
                var updated = new ArrayList<>(model.todos());
                updated.removeIf(todo -> todo.id().equals(saved.todo().id()));
                updated.add(saved.todo());
                yield Transition.stay(new Model(updated, Phase.READY, "", model.createdCount() + (saved.created() ? 1 : 0)));
            }
            case Removed removed -> Transition.stay(new Model(model.todos().stream().filter(todo -> !todo.id().equals(removed.id())).toList(), Phase.READY, "", model.createdCount()));
        };
    }
    private static Transition<Model, Message> saving(Model model, Command<Message> command) {
        return new Transition<>(new Model(model.todos(), Phase.SAVING, "", model.createdCount()), List.of(command));
    }
    @Override public View view(Model model, Dispatch<Message> dispatch) {
        return Views.component(TodoBody.JX, new TodoBody.Props(model, dispatch));
    }
}
