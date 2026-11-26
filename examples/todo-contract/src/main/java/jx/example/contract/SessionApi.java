package jx.example.contract;

/** Session endpoint and header names shared by the optional Spring example and browser. */
public final class SessionApi {
    public static final String CURRENT = "/api/session";
    public static final String LOGIN = CURRENT + "/login";
    public static final String LOGOUT = CURRENT + "/logout";
    public static final String CSRF_HEADER = "X-CSRF-TOKEN";
    public static final String COOKIE = "JXSESSION";
    private SessionApi() {}
}
