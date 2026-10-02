package com.sistemapsicologia.api.config;

import com.sistemapsicologia.api.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * API sin sesión (stateless): cada request se autentica por su cuenta vía el JWT en el header
 * Authorization, no hay cookie de sesión ni CSRF que proteger. /api/health y /api/auth/login son
 * las únicas rutas públicas -- todo lo demás exige un token válido.
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health", "/api/auth/login").permitAll()
                .requestMatchers("/api/**").authenticated()
                // Todo lo que no es /api es la versión web de la app (archivos estáticos): pública,
                // igual que la pantalla de login; los datos siguen exigiendo token.
                .anyRequest().permitAll()
            )
            // Sin token o con token vencido: 401 (antes Spring respondía 403 vacío y la app no
            // podía distinguirlo de un "no autorizado para este estudiante").
            .exceptionHandling(e -> e.authenticationEntryPoint(
                new org.springframework.security.web.authentication.HttpStatusEntryPoint(
                    org.springframework.http.HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
