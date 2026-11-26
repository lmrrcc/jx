package jx;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskOwnerTest {
    @Test void synchronousCompletionReleasesTheReturnedHandleExactlyOnce() {
        List<Task.Result<String, String>> results = new ArrayList<>();
        var releases = new AtomicInteger();
        var owner = new TaskOwner<String, String>(results::add);
        owner.start(done -> {
            done.accept(new Task.Success<>("first"));
            done.accept(new Task.Success<>("duplicate"));
            return releases::incrementAndGet;
        });
        owner.cancel(); owner.cancel();
        assertEquals(List.of(new Task.Success<>("first")), results);
        assertEquals(1, releases.get());
    }

    @Test void cancellationInvalidatesBeforeReleasingAndDropsLateReplies() {
        List<Task.Result<String, String>> results = new ArrayList<>();
        List<Consumer<Task.Result<String, String>>> callbacks = new ArrayList<>();
        var releases = new AtomicInteger();
        var owner = new TaskOwner<String, String>(results::add);
        owner.start(done -> {
            callbacks.add(done);
            return () -> { releases.incrementAndGet(); done.accept(new Task.Success<>("reentrant")); };
        });
        owner.cancel();
        callbacks.getFirst().accept(new Task.Failure<>("late"));
        callbacks.getFirst().accept(null);
        owner.cancel();
        assertEquals(List.of(), results);
        assertEquals(1, releases.get());
    }

    @Test void normalCompletionAlsoReleasesAndPreservesTheTypedFailureIdentity() {
        Object failure = new Object();
        List<Task.Result<String, Object>> results = new ArrayList<>();
        List<Consumer<Task.Result<String, Object>>> callbacks = new ArrayList<>();
        var releases = new AtomicInteger();
        var owner = new TaskOwner<String, Object>(results::add);
        owner.start(done -> { callbacks.add(done); return releases::incrementAndGet; });
        callbacks.getFirst().accept(new Task.Failure<>(failure));
        callbacks.getFirst().accept(new Task.Success<>("late"));
        owner.cancel();
        assertEquals(1, releases.get());
        assertEquals(1, results.size());
        assertSame(failure, ((Task.Failure<String, Object>) results.getFirst()).error());
    }

    @Test void cancellingBeforeStartPerformsNoWork() {
        var starts = new AtomicInteger();
        var owner = new TaskOwner<String, String>(ignored -> fail("Cancelled owner published"));
        owner.cancel();
        owner.start(done -> { starts.incrementAndGet(); return Cancellation.NONE; });
        assertEquals(0, starts.get());
        assertThrows(IllegalStateException.class, () -> owner.start(done -> Cancellation.NONE));
    }

    @Test void unexpectedStartFailurePropagatesAndCannotPublishALaterResult() {
        var expected = new IllegalStateException("programming failure");
        List<Consumer<Task.Result<String, String>>> callbacks = new ArrayList<>();
        var owner = new TaskOwner<String, String>(ignored -> fail("Failed start published"));
        assertSame(expected, assertThrows(IllegalStateException.class, () -> owner.start(done -> {
            callbacks.add(done);
            throw expected;
        })));
        callbacks.getFirst().accept(new Task.Success<>("late"));
    }

    @Test void nullHandleIsAnErrorAndInvalidatesItsOutstandingCallback() {
        List<Consumer<Task.Result<String, String>>> callbacks = new ArrayList<>();
        var owner = new TaskOwner<String, String>(ignored -> fail("Invalid task published"));
        assertThrows(NullPointerException.class, () -> owner.start(done -> { callbacks.add(done); return null; }));
        callbacks.getFirst().accept(new Task.Success<>("late"));
    }
}
