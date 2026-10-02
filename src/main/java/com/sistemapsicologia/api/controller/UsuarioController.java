package com.sistemapsicologia.api.controller;

import com.sistemapsicologia.api.dto.UsuarioDTO;
import com.sistemapsicologia.api.repository.AuditoriaRepository;
import com.sistemapsicologia.api.repository.UsuarioRepository;
import com.sistemapsicologia.api.security.PermisoCrearUsuarios;
import com.sistemapsicologia.api.security.UsuarioAutenticado;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * "Mi cuenta": cada profesional cambia su propio nombre y contraseña. Ver y crear cuentas nuevas
 * (siempre de profesional de psicología) solo lo puede quien indique PermisoCrearUsuarios.
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    /** Mismo mínimo que Configuracion.MIN_LARGO_PASSWORD del escritorio. */
    private static final int MIN_LARGO_PASSWORD = 8;
    private static final Pattern USUARIO_VALIDO = Pattern.compile("[A-Za-z0-9._-]{3,30}");

    private final UsuarioRepository usuarioRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final PermisoCrearUsuarios permisoCrearUsuarios;

    public UsuarioController(UsuarioRepository usuarioRepository, AuditoriaRepository auditoriaRepository,
            PermisoCrearUsuarios permisoCrearUsuarios) {
        this.usuarioRepository = usuarioRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.permisoCrearUsuarios = permisoCrearUsuarios;
    }

    @GetMapping("/me")
    public UsuarioDTO yo(Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        return usuarioRepository.obtenerPorId(u.getUsuarioId()).orElseThrow();
    }

    @PutMapping("/me/nombre")
    public ResponseEntity<?> cambiarMiNombre(@RequestBody Map<String, String> body, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        String nombre = body.get("nombre");
        if (nombre == null || nombre.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El nombre no puede estar vacío"));
        }
        usuarioRepository.actualizarNombre(u.getUsuarioId(), nombre.trim());
        return ResponseEntity.ok(usuarioRepository.obtenerPorId(u.getUsuarioId()).orElseThrow());
    }

    /** La app se bloqueó (inactividad o a pedido): mismo registro que BloqueoPantalla del escritorio. */
    @PostMapping("/me/bloqueo")
    public ResponseEntity<?> registrarBloqueo(Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "BLOQUEO_SESION", "usuarios", u.getUsuarioId(), "App móvil");
        return ResponseEntity.noContent().build();
    }

    /** Desbloquear pide la contraseña de la cuenta; tras 5 fallos la app cierra la sesión. */
    @PostMapping("/me/desbloqueo")
    public ResponseEntity<?> desbloquear(@RequestBody Map<String, String> body, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        if (!usuarioRepository.verificarPassword(u.getUsuarioId(), body.get("password"))) {
            return ResponseEntity.status(401).body(Map.of("error", "Contraseña incorrecta"));
        }
        auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "DESBLOQUEO_SESION", "usuarios", u.getUsuarioId(), "App móvil");
        return ResponseEntity.ok(Map.of("mensaje", "Desbloqueado"));
    }

    @PutMapping("/me/password")
    public ResponseEntity<?> cambiarMiPassword(@RequestBody Map<String, String> body, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        String actual = body.get("passwordActual");
        String nueva = body.get("passwordNueva");
        if (nueva == null || nueva.length() < MIN_LARGO_PASSWORD) {
            return ResponseEntity.badRequest().body(Map.of("error",
                "La nueva contraseña debe tener al menos " + MIN_LARGO_PASSWORD + " caracteres"));
        }
        if (!usuarioRepository.verificarPassword(u.getUsuarioId(), actual)) {
            return ResponseEntity.status(401).body(Map.of("error", "La contraseña actual no es correcta"));
        }
        usuarioRepository.cambiarPassword(u.getUsuarioId(), nueva);
        return ResponseEntity.ok(Map.of("mensaje", "Contraseña actualizada"));
    }

    @GetMapping
    public ResponseEntity<?> listar(Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        if (!permisoCrearUsuarios.permite(u.getUsuario())) {
            return ResponseEntity.status(403).body(Map.of("error", "No tenés permiso para ver las cuentas"));
        }
        return ResponseEntity.ok(usuarioRepository.obtenerTodos());
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Map<String, String> body, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        if (!permisoCrearUsuarios.permite(u.getUsuario())) {
            return ResponseEntity.status(403).body(Map.of("error", "No tenés permiso para crear cuentas"));
        }
        String usuarioNuevo = body.get("usuario") != null ? body.get("usuario").trim() : "";
        String nombre = body.get("nombre") != null ? body.get("nombre").trim() : "";
        String password = body.get("password") != null ? body.get("password") : "";
        if (nombre.isEmpty() || nombre.length() > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "Escribí el nombre y apellido"));
        }
        if (!USUARIO_VALIDO.matcher(usuarioNuevo).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error",
                "El usuario debe tener entre 3 y 30 caracteres: letras, números, punto, guion o guion bajo (sin espacios)"));
        }
        if (password.length() < MIN_LARGO_PASSWORD) {
            return ResponseEntity.badRequest().body(Map.of("error",
                "La contraseña debe tener al menos " + MIN_LARGO_PASSWORD + " caracteres"));
        }
        if (usuarioRepository.existeUsuario(usuarioNuevo)) {
            return ResponseEntity.status(409).body(Map.of("error", "Ese nombre de usuario ya está en uso. Elegí otro."));
        }
        int id;
        try {
            id = usuarioRepository.crear(usuarioNuevo, nombre, password, "psicologo");
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(409).body(Map.of("error", "Ese nombre de usuario ya está en uso. Elegí otro."));
        }
        auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "CREAR_USUARIO", "usuarios", id, usuarioNuevo);
        return ResponseEntity.status(201).body(usuarioRepository.obtenerPorId(id).orElseThrow());
    }

    private UsuarioAutenticado usuario(Authentication auth) {
        return (UsuarioAutenticado) auth.getPrincipal();
    }
}
