package com.bni.orange.wallet.config;

import com.bni.orange.wallet.model.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationEntryPoint jsonAuthEntryPoint,
            AccessDeniedHandler jsonAccessDenied,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/health", "/actuator/info",
                    "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                .authenticationEntryPoint(jsonAuthEntryPoint)
                .accessDeniedHandler(jsonAccessDenied)
            );

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint jsonAuthEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> {
            writeJson(response, 401, objectMapper, ApiResponse.err("Unauthorized"));
        };
    }

    @Bean
    public AccessDeniedHandler jsonAccessDenied(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) -> {
            writeJson(response, 403, objectMapper, ApiResponse.err("Forbidden"));
        };
    }

    private void writeJson(
            jakarta.servlet.http.HttpServletResponse response,
            int status,
            ObjectMapper om,
            ApiResponse<?> body
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        om.writeValue(response.getWriter(), body);
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new CombinedAuthoritiesConverter());
        return converter;
    }

    public static class CombinedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Set<String> scopes = new LinkedHashSet<>();

            Object scopeClaim = jwt.getClaims().get("scope");
            Object scpClaim   = jwt.getClaims().get("scp");
            if (scopeClaim instanceof String s && !s.isBlank()) {
                scopes.addAll(Arrays.asList(s.split("\\s+")));
            }
            if (scpClaim instanceof String s && !s.isBlank()) {
                scopes.addAll(Arrays.asList(s.split("\\s+")));
            } else if (scpClaim instanceof Collection<?> c) {
                for (Object o : c) if (o != null) scopes.add(o.toString());
            }

            var scopeAuthorities = scopes.stream()
                .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                .toList();

            Set<String> roles = new LinkedHashSet<>();
            Object rolesClaim = jwt.getClaims().get("roles");
            if (rolesClaim instanceof Collection<?> c) {
                for (Object o : c) if (o != null) roles.add(o.toString());
            }
            var roleAuthorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();

            return Stream.concat(scopeAuthorities.stream(), roleAuthorities.stream())
                .collect(Collectors.toUnmodifiableList());
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of("http://localhost:3000", "https://orangewallet.app"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
        cfg.setExposedHeaders(List.of("Location", "ETag"));
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
