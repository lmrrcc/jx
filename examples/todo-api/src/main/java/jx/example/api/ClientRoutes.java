package jx.example.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Only declared browser routes fall back to the SPA; unknown APIs/assets retain their HTTP errors. */
@Controller
public class ClientRoutes {
    @GetMapping({"/", "/todos", "/todos/{id}", "/about"})
    public String application() { return "forward:/index.html"; }
}
