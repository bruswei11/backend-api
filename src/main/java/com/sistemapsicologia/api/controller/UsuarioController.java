package com.sistemapsicologia.api.controller;

import com.sistemapsicologia.api.dto.UsuarioDTO;
import com.sistemapsicologia.api.repository.AuditoriaRepository;
import com.sistemapsicologia.api.repository.UsuarioRepository;
import com.sistemapsicologia.api.security.UsuarioAutenticado;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Mitad admin-only (gestión de cuentas, igual que la pestaña "Usuarios" de Configuracion.java) y
 * mitad "Mi cuenta" (cualquier rol cambia su propio nombre/contraseña). rol es uno de
 * admin/psicologo/secretaria -- igual que ROLES en Configuracion.java del escritorio.
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private static final List<String> ROLES = List.of("admin", "psicologo", "secretaria");
    /** Mismo mínimo que Configuracion.MIN_LARGO_PASSWORD del escritorio. */
    private static final int MIN_LARGO_PASSWORD = 8;

    private final UsuarioRepository usuarioRepository;
    private final AuditoriaRepository auditoriaRepository;

    public UsuarioController(UsuarioRepository usuarioRepository, AuditoriaRepository auditoriaRepository) {
        this.usuarioRepository = usuarioRepository;
        this.auditoriaRepository = auditoriaRepository;
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
        if (!u.esAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Solo un administrador puede ver los usuarios"));
        }
        return ResponseEntity.ok(usuarioRepository.obtenerTodos());
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Map<String, String> body, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        if (!u.esAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Solo un administrador puede crear usuarios"));
        }
        String usuarioNuevo = body.get("usuario");
        String nombre = body.get("nombre");
        String password = body.get("password");
        String rol = body.get("rol");
        if (usuarioNuevo == null || usuarioNuevo.isBlank() || nombre == null || nombre.isBlank()
                || password == null || password.isBlank() || !ROLES.contains(rol)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Datos de usuario incompletos o rol inválido"));
        }
        if (password.length() < MIN_LARGO_PASSWORD) {
            return ResponseEntity.badRequest().body(Map.of("error",
                "La contraseña debe tener al menos " + MIN_LARGO_PASSWORD + " caracteres"));
        }
        if (usuarioRepository.existeUsuario(usuarioNuevo)) {
            return ResponseEntity.status(409).body(Map.of("error", "Ese nombre de usuario ya existe"));
        }

        int id = usuarioRepository.crear(usuarioNuevo, nombre, password, rol);
        auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "CREAR_USUARIO", "usuarios", id, usuarioNuevo);
        return ResponseEntity.status(201).body(usuarioRepository.obtenerPorId(id).orElseThrow());
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<?> restablecerPassword(@PathVariable int id, @RequestBody Map<String, String> body, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        if (!u.esAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Solo un administrador puede restablecer contraseñas"));
        }
        String nueva = body.get("password");
        if (nueva == null || nueva.length() < MIN_LARGO_PASSWORD) {
            return ResponseEntity.badRequest().body(Map.of("error",
                "La contraseña debe tener al menos " + MIN_LARGO_PASSWORD + " caracteres"));
        }
        usuarioRepository.cambiarPassword(id, nueva);
        auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "RESET_PASSWORD", "usuarios", id, null);
        return ResponseEntity.ok(Map.of("mensaje", "Contraseña restablecida"));
    }

    @PutMapping("/{id}/activo")
    public ResponseEntity<?> cambiarActivo(@PathVariable int id, @RequestBody Map<String, Boolean> body, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        if (!u.esAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Solo un administrador puede activar/desactivar cuentas"));
        }
        boolean activo = Boolean.TRUE.equals(body.get("activo"));
        if (!activo && id == u.getUsuarioId()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No podés desactivar tu propia cuenta"));
        }
        usuarioRepository.actualizarActivo(id, activo);
        auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), activo ? "ACTIVAR_USUARIO" : "DESACTIVAR_USUARIO", "usuarios", id, null);
        return ResponseEntity.ok(usuarioRepository.obtenerPorId(id).orElseThrow());
    }

    @PutMapping("/{id}/rol")
    public ResponseEntity<?> cambiarRol(@PathVariable int id, @RequestBody Map<String, String> body, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        if (!u.esAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Solo un administrador puede cambiar roles"));
        }
        String rol = body.get("rol");
        if (id == u.getUsuarioId()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No podés cambiar el rol de tu propia cuenta"));
        }
        if (!ROLES.contains(rol)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Rol inválido"));
        }
        usuarioRepository.actualizarRol(id, rol);
        auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "CAMBIAR_ROL", "usuarios", id, rol);
        return ResponseEntity.ok(usuarioRepository.obtenerPorId(id).orElseThrow());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable int id, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        if (!u.esAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Solo un administrador puede eliminar cuentas"));
        }
        if (id == u.getUsuarioId()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No podés eliminar tu propia cuenta"));
        }
        try {
            boolean eliminado = usuarioRepository.eliminar(id);
            if (!eliminado) {
                return ResponseEntity.notFound().build();
            }
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(409).body(Map.of("error",
                "No se puede eliminar: tiene estudiantes, citas, atenciones u otros registros asociados. "
                    + "Reasigná o eliminá esos datos primero, o desactivá la cuenta en vez de borrarla."));
        }
        auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "ELIMINAR_USUARIO", "usuarios", id, null);
        return ResponseEntity.noContent().build();
    }

    private UsuarioAutenticado usuario(Authentication auth) {
        return (UsuarioAutenticado) auth.getPrincipal();
    }
}
