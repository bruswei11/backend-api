package com.sistemapsicologia.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Duration;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Emite y valida los JWT que reemplazan a util.Sesion (el holder estático de un solo proceso del
 * escritorio) para la API/celular. El token lleva usuarioId + rol como claims -- cada request se
 * autoriza contra lo que dice el token firmado por el servidor, nunca contra un rol que mande el
 * cliente por su cuenta.
 */
@Service
public class JwtService {

    private static final Duration VIGENCIA = Duration.ofHours(12);

    private final Key clave;

    /** La clave de ejemplo que viene en application.properties (está en el código, no es secreta). */
    private static final String CLAVE_DE_EJEMPLO = "7753bd7496f7b4b00f613dc91dcb7c41f404a594b45eab638b8017e016415e49";

    public JwtService(@Value("${app.jwt.secret}") String secreto,
            @Value("${spring.datasource.url}") String urlBase) {
        // Fuera de la PC del colegio (base que no es localhost) la clave de ejemplo permitiría a
        // cualquiera fabricar tokens: el servidor no arranca hasta que se defina JWT_SECRET.
        boolean baseLocal = urlBase.contains("//localhost") || urlBase.contains("//127.0.0.1");
        if (!baseLocal && CLAVE_DE_EJEMPLO.equals(secreto)) {
            throw new IllegalStateException("Definí la variable de entorno JWT_SECRET (al menos 32 caracteres al azar) "
                + "antes de usar el servidor fuera de la red del colegio.");
        }
        if (secreto.length() < 32) {
            throw new IllegalStateException("JWT_SECRET tiene que tener al menos 32 caracteres.");
        }
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public String generarToken(int usuarioId, String usuario, String nombre, String rol) {
        Date ahora = new Date();
        Date expira = new Date(ahora.getTime() + VIGENCIA.toMillis());
        return Jwts.builder()
            .subject(String.valueOf(usuarioId))
            .claim("usuario", usuario)
            .claim("nombre", nombre)
            .claim("rol", rol)
            .issuedAt(ahora)
            .expiration(expira)
            .signWith(clave)
            .compact();
    }

    /** @return los claims si el token es válido y no expiró, o null si es inválido/expiró. */
    public Claims validarYObtenerClaims(String token) {
        try {
            return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) clave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (Exception e) {
            return null;
        }
    }
}
