package jx.example.api;

import java.security.Principal;
import jx.example.contract.SessionApi;
import jx.example.contract.SessionInfo;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/** Session identity comes only from Spring authentication; masked CSRF tokens remain session-bound. */
@RestController
public class SessionController {
    @GetMapping(SessionApi.CURRENT)
    public ResponseEntity<SessionInfo> session(Principal principal, CsrfToken csrf) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new SessionInfo(principal == null ? "" : principal.getName(), csrf.getToken()));
    }
    /** Ordinary server-rendered HTML form: credentials are handled by Spring's native
     * authentication filter, never copied into a JavaScript store or URL. */
    @GetMapping(value = SessionApi.LOGIN, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> login(CsrfToken csrf, @RequestParam(name = "error", required = false) String error) {
        String html = """
                <!doctype html><html lang="en"><head><meta charset="UTF-8"><title>Sign in · JX</title>
                <meta name="viewport" content="width=device-width,initial-scale=1"><link rel="icon" href="data:,"></head>
                <body><main><h1>Sign in</h1>%s<form method="post" action="%s">
                <input type="hidden" name="%s" value="%s">
                <label>Username <input name="username" autocomplete="username" required maxlength="64"></label>
                <label>Password <input name="password" type="password" autocomplete="current-password" required maxlength="1024"></label>
                <button type="submit">Sign in</button></form></main></body></html>
                """.formatted(error == null ? "" : "<p role=\"alert\">Sign in failed.</p>", SessionApi.LOGIN,
                HtmlUtils.htmlEscape(csrf.getParameterName()), HtmlUtils.htmlEscape(csrf.getToken()));
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(html);
    }
}
