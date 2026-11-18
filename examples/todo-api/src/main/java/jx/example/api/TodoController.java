package jx.example.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** JSON-only same-origin demo API. No cookies, credentials, sessions, or browser-owned authorization. */
@RestController
@RequestMapping("/api/todos")
public class TodoController {
    public record Create(@NotBlank @Size(max = 200) String title) {
        public Create { if (title != null) title = title.strip(); }
    }
    public record Update(@NotBlank @Size(max = 200) String title, boolean completed, @Min(0) int version) {
        public Update { if (title != null) title = title.strip(); }
    }
    private final TodoRepository repository;
    public TodoController(TodoRepository repository) { this.repository = repository; }

    @GetMapping public List<TodoRepository.Todo> list() { return repository.list(); }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<TodoRepository.Todo> create(@Valid @RequestBody Create request) {
        var created = repository.create(request.title());
        return ResponseEntity.created(URI.create("/api/todos/" + created.id())).body(created);
    }

    @PutMapping(value = "/{id}", consumes = "application/json")
    public TodoRepository.Todo update(@PathVariable("id") UUID id, @Valid @RequestBody Update request) {
        return repository.update(id, request.title(), request.completed(), request.version());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, @RequestParam("version") @Min(0) int version) {
        repository.delete(id, version); return ResponseEntity.noContent().build();
    }
}
