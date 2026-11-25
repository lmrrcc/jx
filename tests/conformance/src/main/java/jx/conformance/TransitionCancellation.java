package jx.conformance;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import jx.Promise;

/** Fixture-local operation ownership, not a framework cancellation API. Each of seven gates is
 * requested once and released by a browser event. Cancelling a consumer does not stop its gate. */
public final class TransitionCancellation {
    private TransitionCancellation() {}

    public enum Request { A, B, C, OLD, FRESH, RACE_OLD, RACE_NEW }
    public record Outcome(boolean cancelled, String value) {}

    private static final class Gate {
        private Promise.Resolver<String> completion;
        private final Promise<String> promise = Promise.create(resolver -> completion = resolver);
        private boolean completed;

        private void complete(String value) {
            if (completed) throw new IllegalStateException("The transport gate was already completed");
            completed = true;
            completion.resolve(value);
        }
    }

    /** Owned by the parent fixture so an old transport can finish after the worker unmounts. */
    public static final class Transport {
        private final Map<Request, Gate> gates = new EnumMap<>(Request.class);

        private Promise<String> start(Request request) {
            if (gates.containsKey(request)) throw new IllegalStateException("Each fixture request starts once: " + request);
            Gate gate = new Gate();
            gates.put(request, gate);
            return gate.promise;
        }

        public boolean started(Request request) { return gates.containsKey(request); }
        public boolean pending(Request request) { return started(request) && !gates.get(request).completed; }
        public String status(Request request) { return !started(request) ? "not started" : pending(request) ? "pending" : "completed"; }
        public void complete(Request request) {
            Gate gate = gates.get(request);
            if (gate == null) throw new IllegalStateException("The transport gate has not started: " + request);
            gate.complete(request.name());
        }

        /** Registers after the owner's forwarding observer. At settlement its queued callback
         * runs before the consumer continuation newly queued by that first observer. */
        public void afterCompletion(Request request, Runnable action) {
            Gate gate = gates.get(request);
            if (gate == null) throw new IllegalStateException("The transport gate has not started: " + request);
            gate.promise.whenSettled(value -> action.run(),
                    failure -> { throw new AssertionError("Fixture transport cannot reject", failure); });
        }
    }

    public static final class Operation {
        private final Request request;
        private Promise.Resolver<Outcome> completion;
        private final Promise<Outcome> consumer = Promise.create(resolver -> completion = resolver);

        private Operation(Request request) { this.request = request; }
        public Request request() { return request; }
        public Promise<Outcome> consumer() { return consumer; }
    }

    /** Kept in the worker's useRef cell. Instance identity is the operation token. */
    public static final class Owner {
        private boolean mounted;
        private Operation current;

        public void activate() { mounted = true; }
        public void dispose() { mounted = false; cancel(); }

        public Operation begin(Request request, Transport transport, Consumer<String> audit) {
            if (!mounted) throw new IllegalStateException("The operation owner is not mounted");
            cancel();
            Operation operation = new Operation(request);
            current = operation;
            transport.start(request).whenSettled(value -> {
                audit.accept("transport " + request + " completed");
                if (owns(operation)) operation.completion.resolve(new Outcome(false, value));
                else audit.accept("ignored " + request);
            }, failure -> operation.completion.reject(failure));
            audit.accept("started " + request);
            return operation;
        }

        public boolean owns(Operation operation) { return mounted && current == operation; }
        public void finish(Operation operation) { if (current == operation) current = null; }

        public void cancel() {
            Operation cancelled = current;
            current = null;
            // Settle the local consumer now. Its transport remains independently pending.
            if (cancelled != null) cancelled.completion.resolve(new Outcome(true, ""));
        }
    }
}
