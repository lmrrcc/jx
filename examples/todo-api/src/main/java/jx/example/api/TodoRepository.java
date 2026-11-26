package jx.example.api;

import java.util.List;
import java.util.UUID;
import jx.example.contract.Todo;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

/** Persistent demo-wide todo list. Optimistic versions prevent an older tab overwriting a newer edit. */
@Repository
public class TodoRepository {
    private final JdbcClient jdbc;
    public TodoRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public List<Todo> list() {
        return jdbc.sql("select id, title, completed, version from todos order by created_at, id limit 500")
                .query(Todo.class).list();
    }

    @org.springframework.transaction.annotation.Transactional
    public Todo create(String title) {
        jdbc.sql("select id from todo_workspace where id = 1 for update").query(Integer.class).single();
        if (jdbc.sql("select count(*) from todos").query(Integer.class).single() >= 500) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Workspace holds 500 tasks; remove a task before adding another");
        }
        String id = UUID.randomUUID().toString();
        jdbc.sql("insert into todos(id, title, completed, version) values (:id, :title, false, 0)")
                .param("id", id).param("title", title).update();
        return new Todo(id, title, false, 0);
    }

    public Todo update(UUID id, String title, boolean completed, int version) {
        int changed = jdbc.sql("update todos set title=:title, completed=:completed, version=version+1 where id=:id and version=:version")
                .param("title", title).param("completed", completed).param("id", id.toString()).param("version", version).update();
        if (changed == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Todo changed or was removed; reload before retrying");
        return new Todo(id.toString(), title, completed, version + 1);
    }

    public void delete(UUID id, int version) {
        int changed = jdbc.sql("delete from todos where id=:id and version=:version").param("id", id.toString()).param("version", version).update();
        if (changed == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Todo changed or was removed; reload before retrying");
    }
}
