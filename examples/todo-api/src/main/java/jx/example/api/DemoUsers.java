package jx.example.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Server-only demo account configuration; no built-in password. Use encoded values
 * understood by Spring's DelegatingPasswordEncoder. Production identity providers
 * belong to the host application's own security configuration. */
@ConfigurationProperties("jx.demo")
public record DemoUsers(String passwordHash, String otherPasswordHash) {
    public DemoUsers {
        validate(passwordHash);
        if (otherPasswordHash != null && !otherPasswordHash.isBlank()) validate(otherPasswordHash);
    }
    private static void validate(String value) {
        if (value == null || value.isBlank() || value.length() > 1024 || !value.startsWith("{") || value.indexOf('}') < 2) {
            throw new IllegalArgumentException("Configure jx.demo.password-hash with a Spring encoded password; no default credential is provided");
        }
    }
    @Override public String toString() { return "DemoUsers[passwords=<redacted>]"; }
}
