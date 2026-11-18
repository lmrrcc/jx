package jx.example.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Loopback-only demonstration backend. Browser UI and Java server have independent classpaths. */
@SpringBootApplication
public class TodoApplication {
    public static void main(String[] args) { SpringApplication.run(TodoApplication.class, args); }
}
