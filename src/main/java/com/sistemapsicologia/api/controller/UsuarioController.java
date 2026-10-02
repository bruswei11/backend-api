package com.sistemapsicologia.api.controller;

import com.sistemapsicologia.api.dto.UsuarioDTO;
import com.sistemapsicologia.api.repository.AuditoriaRepository;
import com.sistemapsicologia.api.repository.UsuarioRepository;
import com.sistemapsicologia.api.security.UsuarioAutenticado;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * "Mi cuenta": cada profesional cambia su propio nombre y contraseña. No hay administración de
 * cuentas: cada profesional se registra solo desde el login (AuthController /registro).
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

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

    private UsuarioAutenticado usuario(Authentication auth) {
        return (UsuarioAutenticado) auth.getPrincipal();
    }
}
