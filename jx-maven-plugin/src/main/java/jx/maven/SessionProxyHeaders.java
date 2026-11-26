package jx.maven;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Opt-in, single host-only session cookie policy for the local development proxy.
 * Does not rewrite security attributes or forward arbitrary credentials. */
final class SessionProxyHeaders {
    private final String name;
    SessionProxyHeaders(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid development session cookie name");
        this.name = name;
    }
    String requestCookie(List<String> lines) {
        if (lines == null) return null;
        String found = null;
        int size = 0;
        for (String line : lines) {
            size += line.length();
            if (size > 8192) throw new IllegalArgumentException("Cookie header exceeds limit");
            for (String item : line.split(";")) {
                int equals = item.indexOf('=');
                if (equals < 0 || !item.substring(0, equals).strip().equals(name)) continue;
                String value = item.substring(equals + 1).strip();
                if (found != null || !cookieValue(value)) throw new IllegalArgumentException("Invalid or duplicate session cookie");
                found = name + "=" + value;
            }
        }
        return found;
    }
    String responseCookie(List<String> lines) {
        if (lines.size() > 32 || lines.stream().mapToInt(String::length).sum() > 32768) throw new IllegalArgumentException("Backend cookie headers exceed limit");
        String found = null;
        for (String line : lines) {
            String[] fields = line.split(";", -1);
            int equals = fields[0].indexOf('=');
            if (equals < 0 || !fields[0].substring(0, equals).strip().equals(name)) continue;
            if (found != null || line.length() > 8192 || line.chars().anyMatch(c -> c < 32 || c > 126)
                    || !cookieValue(fields[0].substring(equals + 1).strip())) throw new IllegalArgumentException("Invalid backend session cookie");
            boolean path = false, httpOnly = false, sameSite = false;
            var attributes = new HashSet<String>();
            for (int i = 1; i < fields.length; i++) {
                String field = fields[i].strip();
                int separator = field.indexOf('=');
                String key = (separator < 0 ? field : field.substring(0, separator)).toLowerCase(Locale.ROOT);
                String value = separator < 0 ? "" : field.substring(separator + 1);
                if (!attributes.add(key)) throw new IllegalArgumentException("Duplicate backend cookie attribute");
                switch (key) {
                    case "path" -> path = value.equals("/");
                    case "httponly" -> httpOnly = separator < 0;
                    case "samesite" -> sameSite = value.equalsIgnoreCase("Lax") || value.equalsIgnoreCase("Strict");
                    case "secure" -> { if (separator >= 0) throw new IllegalArgumentException("Invalid Secure attribute"); }
                    case "max-age" -> { if (!value.matches("-?[0-9]{1,12}")) throw new IllegalArgumentException("Invalid cookie age"); }
                    case "expires" -> { if (value.length() > 128 || value.isBlank()) throw new IllegalArgumentException("Invalid cookie expiry"); }
                    default -> throw new IllegalArgumentException("Unsupported backend session cookie attribute");
                }
            }
            if (!path || !httpOnly || !sameSite) throw new IllegalArgumentException("Session cookie must be host-only, Path=/, HttpOnly and SameSite=Lax or Strict");
            found = line;
        }
        return found;
    }
    static String csrf(List<String> values) {
        if (values == null) return null;
        if (values.size() != 1 || values.getFirst().isEmpty() || values.getFirst().length() > 1024
                || values.getFirst().chars().anyMatch(c -> c < 33 || c > 126)) throw new IllegalArgumentException("Invalid CSRF header");
        return values.getFirst();
    }
    static String location(List<String> values) {
        if (values.isEmpty()) return null;
        if (values.size() != 1) throw new IllegalArgumentException("Duplicate backend location");
        String value = values.getFirst();
        if (value.length() > 8192 || !value.startsWith("/") || value.startsWith("//") || value.contains("\\")
                || value.chars().anyMatch(c -> c < 33 || c > 126)) throw new IllegalArgumentException("Backend location must remain on the development origin");
        URI uri = URI.create(value);
        String path = uri.getPath();
        if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawFragment() != null || path.contains("%")
                || path.contains("\\") || path.contains("//") || path.chars().anyMatch(Character::isISOControl)
                || !uri.normalize().getRawPath().equals(uri.getRawPath())) throw new IllegalArgumentException("Backend location must be normalized");
        for (String segment : path.split("/")) if (segment.equals(".") || segment.equals("..")) throw new IllegalArgumentException("Backend location must be normalized");
        return value;
    }
    private static boolean cookieValue(String value) {
        return value.length() <= 4096 && value.chars().allMatch(c -> c >= 33 && c <= 126 && c != '"' && c != ',' && c != ';' && c != '\\');
    }
}
