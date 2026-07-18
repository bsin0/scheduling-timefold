package org.churchband.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration: a single admin account (no per-musician
 * accounts — per current requirements, musicians never touch the app
 * directly, so every write action is an admin action).
 *
 * Username/password come from application.properties (see
 * app.admin.username / app.admin.password below) rather than being
 * hardcoded here, so credentials aren't sitting in source control.
 */
@Configuration
public class SecurityConfig {

    private final String adminUsername;
    private final String adminPassword;

    public SecurityConfig(
            @Value("${app.admin.username}") String adminUsername,
            @Value("${app.admin.password}") String adminPassword) {
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    // BCrypt hashes passwords before storing/comparing them — never
    // compares or stores plain text, even in memory. Standard practice
    // for any password, even a single shared admin one.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Defines the one admin account Spring Security checks logins against.
    // InMemoryUserDetailsManager is fine here since there's exactly one
    // account and it's config-driven, not something users self-register
    // for. If you ever need multiple admin accounts, this would become a
    // database-backed UserDetailsService instead.
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails admin = User.builder()
                .username(adminUsername)
                .password(encoder.encode(adminPassword))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    // Defines what's protected and how. Every /api/** request requires
    // login (HTTP Basic Auth — the browser/curl prompts for
    // username+password directly, no separate login page yet). The H2
    // console is left open for now since it's a dev-only tool; worth
    // locking down before this ever leaves your local network.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/h2-console/**").permitAll()
                        // EventSource (browser SSE API) can't send an
                        // Authorization header, so the stream endpoint is
                        // deliberately unauthenticated. Starting a solve
                        // (POST /api/solve/start) and stopping one still
                        // require auth; the stream id is an unguessable
                        // UUID, so this narrow exception doesn't expose
                        // anything an attacker could act on without
                        // already knowing a live session id.
                        .requestMatchers("/api/solve/stream/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .httpBasic(basic -> {})
                .csrf(csrf -> csrf
                        // CSRF protection is for browser-form-based session auth;
                        // with HTTP Basic (stateless, credentials sent every
                        // request) it doesn't apply the same way and gets in
                        // the way of simple API calls from curl/frontend fetch.
                        // Also exempt the H2 console, which has its own
                        // frame-based interactions that don't play well with it.
                        .ignoringRequestMatchers("/api/**", "/h2-console/**"))
                .headers(headers -> headers
                        // H2 console renders inside a frame; Spring Security
                        // blocks framing by default (clickjacking protection).
                        .frameOptions(frame -> frame.sameOrigin())
                );

        return http.build();
    }
}