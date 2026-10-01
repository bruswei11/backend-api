package com.sistemapsicologia.api.controller;

import com.sistemapsicologia.api.dto.LoginRequest;
import com.sistemapsicologia.api.dto.LoginResponse;
import com.sistemapsicologia.api.repository.AuditoriaRepository;
import com.sistemapsicologia.api.repository.UsuarioRepository;
import com.sistemapsicologia.api.security.JwtService;
import com.sistemapsicologia.api.security.PasswordUtil;
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
 * Mismas reglas que Vista/Login.java del escritorio: tras 5 intentos fallidos SEGUIDOS la cuenta
 * queda bloqueada 15 minutos (el conteo vive en la base, compartido entre la PC y el celular), y un
 * usuario inexistente recibe exactamente la misma respuesta que una cuenta real con contraseña
 * incorrecta, para no revelar qué nombres de usuario existen.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final int MAX_INTENTOS_FALLIDOS = 5;
    private static final int MINUTOS_BLOQUEO = 15;

    /** Intentos contra usuarios que NO existen (no hay fila donde guardarlos): {intentos, bloqueado hasta ms}. */
    private static final Map<String, long[]> INTENTOS_USUARIOS_INEXISTENTES = new HashMap<>();

    private final UsuarioRepository usuarioRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final JwtService jwtService;

    public AuthController(UsuarioRepository usuarioRepository, AuditoriaRepository auditoriaRepository, JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.jwtService = jwtService;
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
            String token = jwtService.generarToken(u.id(), u.usuario(), u.nombre(), u.rol());
            return ResponseEntity.ok(new LoginResponse(token, u.id(), u.usuario(), u.nombre(), u.rol()));
        }

        int nuevos = u.intentosFallidos() + 1;
        if (nuevos >= MAX_INTENTOS_FALLIDOS) {
            // Hora local desde Java, nunca NOW() de MySQL (el servidor corre en UTC).
            usuarioRepository.registrarIntentos(u.id(), 0, ahora.plusMinutes(MINUTOS_BLOQUEO));
            auditoriaRepository.registrar(u.id(), u.nombre(), "CUENTA_BLOQUEADA", "usuarios", u.id(),
                MAX_INTENTOS_FALLIDOS + " intentos fallidos seguidos");
            return bloqueada(MINUTOS_BLOQUEO);
        }
        usuarioRepository.registrarIntentos(u.id(), nuevos, null);
        return incorrecta(MAX_INTENTOS_FALLIDOS - nuevos);
    }

    private static ResponseEntity<?> simularIntentoFallido(String usuario) {
        long ahora = System.currentTimeMillis();
        synchronized (INTENTOS_USUARIOS_INEXISTENTES) {
            long[] estado = INTENTOS_USUARIOS_INEXISTENTES.computeIfAbsent(usuario.toLowerCase(Locale.ROOT), k -> new long[2]);
            if (estado[1] > ahora) {
                return bloqueada(Math.max(1, (estado[1] - ahora + 59_999) / 60_000));
            }
            estado[0]++;
            if (estado[0] >= MAX_INTENTOS_FALLIDOS) {
                estado[0] = 0;
                estado[1] = ahora + MINUTOS_BLOQUEO * 60_000L;
                return bloqueada(MINUTOS_BLOQUEO);
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
