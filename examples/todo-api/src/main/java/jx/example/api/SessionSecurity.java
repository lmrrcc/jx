package jx.example.api;

import java.util.ArrayList;
import jx.example.contract.SessionApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/** Example-owned session authentication. The framework core does not choose host security policy. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DemoUsers.class)
public class SessionSecurity {
    @Bean PasswordEncoder passwordEncoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }
    @Bean UserDetailsService users(DemoUsers configuration) {
        var users = new ArrayList<UserDetails>();
        users.add(User.withUsername("demo").password(configuration.passwordHash()).roles("USER").build());
        if (configuration.otherPasswordHash() != null && !configuration.otherPasswordHash().isBlank()) {
            users.add(User.withUsername("other").password(configuration.otherPasswordHash()).roles("USER").build());
        }
        return new InMemoryUserDetailsManager(users);
    }
    @Bean SecurityFilterChain sessionChain(HttpSecurity http,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) throws Exception {
        return http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(SessionApi.CURRENT, SessionApi.LOGIN, "/application.js", "/styles.css", "/error").permitAll()
                        .anyRequest().authenticated())
                .csrf(Customizer.withDefaults())
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                        (request, response, failure) -> response.sendError(401),
                        request -> request.getServletPath().startsWith("/api/")))
                .formLogin(form -> form.loginPage(SessionApi.LOGIN).loginProcessingUrl(SessionApi.LOGIN)
                        .successHandler((request, response, authentication) -> {
                            response.setStatus(303); response.setHeader("Location", "/todos");
                        }).failureHandler((request, response, failure) -> {
                            response.setStatus(303); response.setHeader("Location", SessionApi.LOGIN + "?error");
                        }).permitAll())
                .logout(logout -> logout.logoutUrl(SessionApi.LOGOUT)
                        .addLogoutHandler((request, response, authentication) -> {
                            var expired = new jakarta.servlet.http.Cookie(SessionApi.COOKIE, "");
                            expired.setPath("/"); expired.setMaxAge(0); expired.setHttpOnly(true);
                            expired.setSecure(secureCookie); expired.setAttribute("SameSite", "Lax");
                            response.addCookie(expired);
                        })
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(303); response.setHeader("Location", SessionApi.LOGIN + "?logout");
                        }))
                .build();
    }
}
