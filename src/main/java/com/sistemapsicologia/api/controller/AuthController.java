package com.sistemapsicologia.api.controller;

import com.sistemapsicologia.api.dto.LoginRequest;
import com.sistemapsicologia.api.dto.LoginResponse;
import com.sistemapsicologia.api.repository.AuditoriaRepository;
import com.sistemapsicologia.api.repository.UsuarioRepository;
import com.sistemapsicologia.api.security.JwtService;
import com.sistemapsicologia.api.security.PasswordUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
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
 * Todos los usuarios son profesionales de psicología: no hay administrador que cree cuentas, cada
 * profesional se registra solo desde el login (y solo ve a sus propios estudiantes).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final int MAX_INTENTOS_FALLIDOS = 5;
    private static final int MINUTOS_PRIMER_BLOQUEO = 5;
    private static final int MINUTOS_BLOQUEO_REPETIDO = 10;
    private static final int MIN_LARGO_PASSWORD = 8;
    private static final Pattern USUARIO_VALIDO = Pattern.compile("[A-Za-z0-9._-]{3,30}");

    /** Cuentas nuevas por hora, por dirección y en total, para que nadie llene la base de cuentas falsas. */
    private static final int REGISTROS_POR_HORA_POR_IP = 5;
    private static final int REGISTROS_POR_HORA_TOTAL = 30;
    private static final Map<String, Deque<Long>> REGISTROS_RECIENTES = new HashMap<>();

    /** Intentos contra usuarios que NO existen (no hay fila donde guardarlos): {intentos, bloqueado hasta ms, ya bloqueado}. */
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
            String token = jwtService.generarToken(u.id(), u.usuario(), u.nombre(), "psicologo");
            return ResponseEntity.ok(new LoginResponse(token, u.id(), u.usuario(), u.nombre(), "psicologo"));
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

    /** "Crear cuenta" del login: siempre como profesional de psicología, y deja la sesión iniciada. */
    @PostMapping("/registro")
    public ResponseEntity<?> registro(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String usuario = body.get("usuario") != null ? body.get("usuario").trim() : "";
        String nombre = body.get("nombre") != null ? body.get("nombre").trim() : "";
        String password = body.get("password") != null ? body.get("password") : "";
        if (nombre.isEmpty() || nombre.length() > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "Escribí tu nombre y apellido"));
        }
        if (!USUARIO_VALIDO.matcher(usuario).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error",
                "El usuario debe tener entre 3 y 30 caracteres: letras, números, punto, guion o guion bajo (sin espacios)"));
        }
        if (password.length() < MIN_LARGO_PASSWORD) {
            return ResponseEntity.badRequest().body(Map.of("error",
                "La contraseña debe tener al menos " + MIN_LARGO_PASSWORD + " caracteres"));
        }
        if (usuarioRepository.existeUsuario(usuario)) {
            return ResponseEntity.status(409).body(Map.of("error", "Ese nombre de usuario ya está en uso. Elegí otro."));
        }
        if (!permitirRegistro(direccion(request))) {
            return ResponseEntity.status(429).body(Map.of("error",
                "Se crearon demasiadas cuentas en poco tiempo. Probá de nuevo en una hora."));
        }
        int id;
        try {
            id = usuarioRepository.crear(usuario, nombre, password, "psicologo");
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(409).body(Map.of("error", "Ese nombre de usuario ya está en uso. Elegí otro."));
        }
        auditoriaRepository.registrar(id, nombre, "REGISTRO_USUARIO", "usuarios", id, "Cuenta creada desde el login");
        String token = jwtService.generarToken(id, usuario, nombre, "psicologo");
        return ResponseEntity.status(201).body(new LoginResponse(token, id, usuario, nombre, "psicologo"));
    }

    /** IP del cliente: detrás del proxy de Render llega en X-Forwarded-For (la última es la que vio el proxy). */
    private static String direccion(HttpServletRequest request) {
        String reenviada = request.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            String[] partes = reenviada.split(",");
            return partes[partes.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    private static boolean permitirRegistro(String ip) {
        long ahora = System.currentTimeMillis();
        long haceUnaHora = ahora - 3_600_000L;
        synchronized (REGISTROS_RECIENTES) {
            for (Deque<Long> fechas : REGISTROS_RECIENTES.values()) {
                while (!fechas.isEmpty() && fechas.peekFirst() < haceUnaHora) {
                    fechas.pollFirst();
                }
            }
            REGISTROS_RECIENTES.values().removeIf(Deque::isEmpty);
            Deque<Long> total = REGISTROS_RECIENTES.computeIfAbsent("*", k -> new ArrayDeque<>());
            Deque<Long> deEstaIp = REGISTROS_RECIENTES.computeIfAbsent(ip, k -> new ArrayDeque<>());
            if (total.size() >= REGISTROS_POR_HORA_TOTAL || deEstaIp.size() >= REGISTROS_POR_HORA_POR_IP) {
                return false;
            }
            total.addLast(ahora);
            deEstaIp.addLast(ahora);
            return true;
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
