package com.sistemapsicologia.api.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lee el header "Authorization: Bearer <token>", lo valida contra JwtService, y si es válido deja
 * un UsuarioAutenticado como principal en el SecurityContext para el resto del request. Si no hay
 * token o es inválido, simplemente no autentica -- las rutas protegidas van a responder 401 solas
 * vía la configuración de SecurityConfig, este filtro no rechaza nada explícitamente.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            Claims claims = jwtService.validarYObtenerClaims(token);
            if (claims != null) {
                int usuarioId = Integer.parseInt(claims.getSubject());
                String usuario = claims.get("usuario", String.class);
                String nombre = claims.get("nombre", String.class);
                String rol = claims.get("rol", String.class);
                UsuarioAutenticado principal = new UsuarioAutenticado(usuarioId, usuario, nombre, rol);

                var authoridades = List.of(new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase()));
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authoridades);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}
