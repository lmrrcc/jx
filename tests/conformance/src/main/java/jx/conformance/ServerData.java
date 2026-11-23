package jx.conformance;

import jx.Promise;

/** The data the postponing page waits for; the server cases replace it between the two steps. */
public final class ServerData {
    private ServerData() {}

    /** Never settled until a case replaces it. */
    public static Promise<String> current = Promise.create(resolver -> {});
}
