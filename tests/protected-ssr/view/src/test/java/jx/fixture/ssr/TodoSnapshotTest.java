package jx.fixture.ssr;

import java.util.ArrayList;
import java.util.List;
import jx.example.contract.Todo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TodoSnapshotTest {
    @Test void requestOwnsItsImmutableListAndIdentitiesDoNotShareIt() {
        var source = new ArrayList<>(List.of(new Todo("a", "alpha only", false, 0)));
        var alpha = new TodoSnapshot("alpha", source);
        source.clear(); source.add(new Todo("b", "beta only", true, 1));
        var beta = new TodoSnapshot("beta", source);
        assertEquals("alpha only", alpha.todos().getFirst().title());
        assertEquals("beta only", beta.todos().getFirst().title());
        assertThrows(UnsupportedOperationException.class, () -> alpha.todos().clear());
        assertThrows(UnsupportedOperationException.class, () -> beta.todos().add(alpha.todos().getFirst()));
    }

    @Test void anonymousOrUnboundedSnapshotsFailBeforeRendering() {
        assertThrows(IllegalArgumentException.class, () -> new TodoSnapshot(" ", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TodoSnapshot("x".repeat(129), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TodoSnapshot("alpha",
                java.util.Collections.nCopies(501, new Todo("a", "title", false, 0))));
        assertThrows(IllegalArgumentException.class, () -> new TodoSnapshot("alpha",
                List.of(new Todo("a", "x".repeat(2001), false, 0))));
    }
}
