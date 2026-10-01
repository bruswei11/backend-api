package com.sistemapsicologia.api.controller;

import com.sistemapsicologia.api.dto.HistoriaDTO;
import com.sistemapsicologia.api.dto.PacienteDTO;
import com.sistemapsicologia.api.repository.AuditoriaRepository;
import com.sistemapsicologia.api.repository.HistoriaRepository;
import com.sistemapsicologia.api.repository.PacienteRepository;
import com.sistemapsicologia.api.security.UsuarioAutenticado;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Seguimiento del Estudiante. Reglas iguales a DetallePaciente.java del escritorio:
 * - secretaria no tiene ningún acceso a esta clase de datos (403 siempre, sin excepción).
 * - psicologo solo accede al seguimiento de SUS estudiantes (pacientes.psicologo_id, no
 *   historia_psicologica.psicologo_id -- ese campo solo dice quién escribió cada entrada).
 * - A propósito NO hay endpoint PUT/PATCH: una vez creada, una entrada queda fija. En el
 *   escritorio esto hoy es solo una restricción de la interfaz (el botón Guardar se desactiva);
 *   acá queda reforzado del lado del servidor.
 */
@RestController
@RequestMapping("/api")
public class HistoriaController {

    private final HistoriaRepository historiaRepository;
    private final PacienteRepository pacienteRepository;
    private final AuditoriaRepository auditoriaRepository;

    public HistoriaController(HistoriaRepository historiaRepository, PacienteRepository pacienteRepository,
            AuditoriaRepository auditoriaRepository) {
        this.historiaRepository = historiaRepository;
        this.pacienteRepository = pacienteRepository;
        this.auditoriaRepository = auditoriaRepository;
    }

    @GetMapping("/estudiantes/{pacienteId}/seguimientos")
    public ResponseEntity<?> listarPorEstudiante(@PathVariable int pacienteId, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        if (u.esSecretaria()) {
            return ResponseEntity.status(403).body(Map.of("error", "No autorizado a datos clínicos"));
        }
        Optional<PacienteDTO> pacienteOpt = pacienteRepository.obtenerPorId(pacienteId);
        if (pacienteOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (sinPermisoSobreEstudiante(u, pacienteOpt.get())) {
            return ResponseEntity.status(403).body(Map.of("error", "No autorizado para ver este estudiante"));
        }
        return ResponseEntity.ok(historiaRepository.listarPorPaciente(pacienteId));
    }

    @GetMapping("/seguimientos/{id}")
    public ResponseEntity<?> obtener(@PathVariable int id, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        if (u.esSecretaria()) {
            return ResponseEntity.status(403).body(Map.of("error", "No autorizado a datos clínicos"));
        }
        Optional<HistoriaDTO> historiaOpt = historiaRepository.obtenerPorId(id);
        if (historiaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HistoriaDTO h = historiaOpt.get();
        PacienteDTO paciente = pacienteRepository.obtenerPorId(h.getPacienteId()).orElseThrow();
        if (sinPermisoSobreEstudiante(u, paciente)) {
            return ResponseEntity.status(403).body(Map.of("error", "No autorizado para ver esta entrada"));
        }
        auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "VER_HISTORIA", "historia_psicologica", id, null);
        return ResponseEntity.ok(h);
    }

    @PostMapping("/seguimientos")
    public ResponseEntity<?> crear(@RequestBody HistoriaDTO h, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        if (u.esSecretaria()) {
            return ResponseEntity.status(403).body(Map.of("error", "No autorizado a datos clínicos"));
        }
        Optional<PacienteDTO> pacienteOpt = pacienteRepository.obtenerPorId(h.getPacienteId());
        if (pacienteOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El estudiante indicado no existe"));
        }
        if (sinPermisoSobreEstudiante(u, pacienteOpt.get())) {
            return ResponseEntity.status(403).body(Map.of("error", "No autorizado para registrar seguimiento de este estudiante"));
        }

        h.setPsicologoId(u.getUsuarioId());
        int id = historiaRepository.crear(h);
        auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "CREAR_HISTORIA", "historia_psicologica", id, null);
        return ResponseEntity.status(201).body(historiaRepository.obtenerPorId(id).orElseThrow());
    }

    private boolean sinPermisoSobreEstudiante(UsuarioAutenticado u, PacienteDTO p) {
        return u.esPsicologo() && !Objects.equals(p.getPsicologoId(), u.getUsuarioId());
    }

    private UsuarioAutenticado usuario(Authentication auth) {
        return (UsuarioAutenticado) auth.getPrincipal();
    }
}
