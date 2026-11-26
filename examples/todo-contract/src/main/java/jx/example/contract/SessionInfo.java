package jx.example.contract;

import java.util.Objects;

/** Current verified identity (empty when anonymous) and a masked, session-bound CSRF
 * token. This is not an authorization grant and must not be persisted or logged. */
public record SessionInfo(String username, String csrfToken) {
    public SessionInfo {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(csrfToken, "csrfToken");
        if (username.length() > 64 || csrfToken.isEmpty() || csrfToken.length() > 1024) {
            throw new IllegalArgumentException("Invalid session response");
        }
    }
    @Override public String toString() { return "SessionInfo[username=" + username + ", csrfToken=<redacted>]"; }
}
