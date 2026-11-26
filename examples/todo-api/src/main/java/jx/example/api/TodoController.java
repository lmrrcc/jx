package jx.example.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.security.Principal;
import jx.example.contract.Todo;
import jx.example.contract.TodoApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Session-protected API. Principal ownership is assigned by Spring, never by request JSON. */
@RestController
@RequestMapping(TodoApi.COLLECTION)
public class TodoController {
    public record Create(@NotBlank @Size(max = 200) String title) {
        public Create { if (title != null) title = title.strip(); }
    }
    public record Update(@NotBlank @Size(max = 200) String title, boolean completed, @Min(0) int version) {
        public Update { if (title != null) title = title.strip(); }
    }
    private final TodoRepository repository;
    public TodoController(TodoRepository repository) { this.repository = repository; }

    @GetMapping public List<Todo> list(Principal principal) { return repository.list(principal.getName()); }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<Todo> create(@Valid @RequestBody Create request, Principal principal) {
        var created = repository.create(principal.getName(), request.title());
        return ResponseEntity.created(URI.create(TodoApi.item(created.id()))).body(created);
    }

    @PutMapping(value = TodoApi.ITEM_TEMPLATE, consumes = "application/json")
    public Todo update(@PathVariable(TodoApi.ID_PARAMETER) UUID id, @Valid @RequestBody Update request, Principal principal) {
        return repository.update(principal.getName(), id, request.title(), request.completed(), request.version());
    }

    @DeleteMapping(TodoApi.ITEM_TEMPLATE)
    public ResponseEntity<Void> delete(@PathVariable(TodoApi.ID_PARAMETER) UUID id,
            @RequestParam(TodoApi.VERSION_PARAMETER) @Min(0) int version, Principal principal) {
        repository.delete(principal.getName(), id, version); return ResponseEntity.noContent().build();
    }
}
