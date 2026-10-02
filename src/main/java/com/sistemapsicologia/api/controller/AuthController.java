package com.sistemapsicologia.api.controller;

import com.sistemapsicologia.api.dto.LoginRequest;
import com.sistemapsicologia.api.dto.LoginResponse;
import com.sistemapsicologia.api.repository.AuditoriaRepository;
import com.sistemapsicologia.api.repository.UsuarioRepository;
import com.sistemapsicologia.api.security.JwtService;
import com.sistemapsicologia.api.security.PasswordUtil;
import com.sistemapsicologia.api.security.PermisoCrearUsuarios;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tras 5 intentos fallidos SEGUIDOS la cuenta queda bloqueada 5 minutos; si después vuelve a fallar
 * 5 veces más, 10 minutos (y así cada vez, hasta que entre bien). Un usuario inexistente recibe
 * exactamente la misma respuesta que una cuenta real con contraseña incorrecta, para no revelar qué
 * nombres de usuario existen.
 *
 * Todos los usuarios son profesionales de psicología (cada uno ve solo a sus estudiantes). No hay
 * registro libre: las cuentas nuevas las crea josechavez desde Configuración → Usuarios.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final int MAX_INTENTOS_FALLIDOS = 5;
    private static final int MINUTOS_PRIMER_BLOQUEO = 5;
    private static final int MINUTOS_BLOQUEO_REPETIDO = 10;

    /** Intentos contra usuarios que NO existen (no hay fila donde guardarlos): {intentos, bloqueado hasta ms, ya bloqueado}. */
    private static final Map<String, long[]> INTENTOS_USUARIOS_INEXISTENTES = new HashMap<>();

    private final UsuarioRepository usuarioRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final JwtService jwtService;
    private final PermisoCrearUsuarios permisoCrearUsuarios;

    public AuthController(UsuarioRepository usuarioRepository, AuditoriaRepository auditoriaRepository, JwtService jwtService,
            PermisoCrearUsuarios permisoCrearUsuarios) {
        this.usuarioRepository = usuarioRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.jwtService = jwtService;
        this.permisoCrearUsuarios = permisoCrearUsuarios;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        String usuario = req.getUsuario() != null ? req.getUsuario().trim() : "";
        String password = req.getPassword() != null ? req.getPassword() : "";
        if (usuario.isEmpty() || password.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ingresá usuario y contraseña"));
        }

        Optional<UsuarioRepository.UsuarioLogin> filaOpt = usuarioRepository.buscarParaLogin(usuario);
        if (filaOpt.isEmpty()) {
            return simularIntentoFallido(usuario);
        }
        UsuarioRepository.UsuarioLogin u = filaOpt.get();
        LocalDateTime ahora = LocalDateTime.now();

        if (u.bloqueadoHasta() != null && u.bloqueadoHasta().isAfter(ahora)) {
            return bloqueada(minutosHasta(u.bloqueadoHasta(), ahora));
        }

        boolean correcta = u.activo() && PasswordUtil.verificar(password, u.salt(), u.passwordHash());
        if (correcta) {
            usuarioRepository.registrarIntentos(u.id(), 0, null);
            auditoriaRepository.registrar(u.id(), u.nombre(), "LOGIN", "usuarios", u.id(), "Desde la app móvil");
            String token = jwtService.generarToken(u.id(), u.usuario(), u.nombre(), "psicologo");
            return ResponseEntity.ok(new LoginResponse(token, u.id(), u.usuario(), u.nombre(), "psicologo",
                permisoCrearUsuarios.permite(u.usuario())));
        }

        int nuevos = u.intentosFallidos() + 1;
        if (nuevos >= MAX_INTENTOS_FALLIDOS) {
            // bloqueado_hasta solo se limpia al entrar bien: si tiene una fecha (ya vencida), la cuenta
            // ya estuvo bloqueada desde su último ingreso correcto y esta vez el bloqueo es más largo.
            int minutos = u.bloqueadoHasta() != null ? MINUTOS_BLOQUEO_REPETIDO : MINUTOS_PRIMER_BLOQUEO;
            // Hora local desde Java, nunca NOW() de MySQL (el servidor corre en UTC).
            usuarioRepository.registrarIntentos(u.id(), 0, ahora.plusMinutes(minutos));
            auditoriaRepository.registrar(u.id(), u.nombre(), "CUENTA_BLOQUEADA", "usuarios", u.id(),
                MAX_INTENTOS_FALLIDOS + " intentos fallidos seguidos (" + minutos + " min)");
            return bloqueada(minutos);
        }
        usuarioRepository.registrarIntentos(u.id(), nuevos, u.bloqueadoHasta());
        return incorrecta(MAX_INTENTOS_FALLIDOS - nuevos);
    }

    private static ResponseEntity<?> simularIntentoFallido(String usuario) {
        long ahora = System.currentTimeMillis();
        synchronized (INTENTOS_USUARIOS_INEXISTENTES) {
            long[] estado = INTENTOS_USUARIOS_INEXISTENTES.computeIfAbsent(usuario.toLowerCase(Locale.ROOT), k -> new long[3]);
            if (estado[1] > ahora) {
                return bloqueada(Math.max(1, (estado[1] - ahora + 59_999) / 60_000));
            }
            estado[0]++;
            if (estado[0] >= MAX_INTENTOS_FALLIDOS) {
                int minutos = estado[2] > 0 ? MINUTOS_BLOQUEO_REPETIDO : MINUTOS_PRIMER_BLOQUEO;
                estado[0] = 0;
                estado[1] = ahora + minutos * 60_000L;
                estado[2] = 1;
                return bloqueada(minutos);
            }
            return incorrecta(MAX_INTENTOS_FALLIDOS - (int) estado[0]);
        }
    }

    private static ResponseEntity<?> incorrecta(int restantes) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        String mensaje = "Usuario o contraseña incorrectos";
        // Igual que el escritorio: solo se avisa en los últimos intentos antes del bloqueo.
        if (restantes <= 2) {
            mensaje += " (" + restantes + " intento(s) más antes de bloquear la cuenta)";
            cuerpo.put("intentosRestantes", restantes);
        }
        cuerpo.put("error", mensaje);
        return ResponseEntity.status(401).body(cuerpo);
    }

    private static ResponseEntity<?> bloqueada(long minutos) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", "Esta cuenta quedó bloqueada temporalmente por varios intentos fallidos seguidos. "
            + "Volvé a intentarlo en " + minutos + " minuto(s).");
        cuerpo.put("bloqueada", true);
        cuerpo.put("minutosRestantes", minutos);
        return ResponseEntity.status(423).body(cuerpo);
    }

    private static long minutosHasta(LocalDateTime hasta, LocalDateTime ahora) {
        return Math.max(1, (Duration.between(ahora, hasta).toMillis() + 59_999) / 60_000);
    }
}
