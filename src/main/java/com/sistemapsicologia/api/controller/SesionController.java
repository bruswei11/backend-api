package com.sistemapsicologia.api.controller;

import com.sistemapsicologia.api.dto.PacienteDTO;
import com.sistemapsicologia.api.dto.SesionDTO;
import com.sistemapsicologia.api.repository.AuditoriaRepository;
import com.sistemapsicologia.api.repository.PacienteRepository;
import com.sistemapsicologia.api.repository.SesionRepository;
import com.sistemapsicologia.api.repository.TurnoRepository;
import com.sistemapsicologia.api.security.UsuarioAutenticado;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Atenciones (notas SOAP). Mismas reglas que Seguimiento: secretaria sin acceso, psicologo solo a
 * sus propios estudiantes, sin endpoint de edición (una vez guardada, queda fija). Guardar una
 * atención ligada a una cita "programada" la completa automáticamente, igual que en el escritorio.
 */
@RestController
@RequestMapping("/api")
public class SesionController {

    private final SesionRepository sesionRepository;
    private final PacienteRepository pacienteRepository;
    private final TurnoRepository turnoRepository;
    private final AuditoriaRepository auditoriaRepository;

    public SesionController(SesionRepository sesionRepository, PacienteRepository pacienteRepository,
            TurnoRepository turnoRepository, AuditoriaRepository auditoriaRepository) {
        this.sesionRepository = sesionRepository;
        this.pacienteRepository = pacienteRepository;
        this.turnoRepository = turnoRepository;
        this.auditoriaRepository = auditoriaRepository;
    }

    @GetMapping("/estudiantes/{pacienteId}/atenciones")
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
        return ResponseEntity.ok(sesionRepository.listarPorPaciente(pacienteId));
    }

    @GetMapping("/atenciones/{id}")
    public ResponseEntity<?> obtener(@PathVariable int id, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        if (u.esSecretaria()) {
            return ResponseEntity.status(403).body(Map.of("error", "No autorizado a datos clínicos"));
        }
        Optional<SesionDTO> sesionOpt = sesionRepository.obtenerPorId(id);
        if (sesionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        SesionDTO s = sesionOpt.get();
        PacienteDTO paciente = pacienteRepository.obtenerPorId(s.getPacienteId()).orElseThrow();
        if (sinPermisoSobreEstudiante(u, paciente)) {
            return ResponseEntity.status(403).body(Map.of("error", "No autorizado para ver esta atención"));
        }
        auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "VER_SESION", "sesiones", id, null);
        return ResponseEntity.ok(s);
    }

    @PostMapping("/atenciones")
    public ResponseEntity<?> crear(@RequestBody SesionDTO s, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        if (u.esSecretaria()) {
            return ResponseEntity.status(403).body(Map.of("error", "No autorizado a datos clínicos"));
        }
        Optional<PacienteDTO> pacienteOpt = pacienteRepository.obtenerPorId(s.getPacienteId());
        if (pacienteOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El estudiante indicado no existe"));
        }
        if (sinPermisoSobreEstudiante(u, pacienteOpt.get())) {
            return ResponseEntity.status(403).body(Map.of("error", "No autorizado para registrar una atención de este estudiante"));
        }

        s.setPsicologoId(u.getUsuarioId());
        int id = sesionRepository.crear(s);
        auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "CREAR_SESION", "sesiones", id, null);

        boolean turnoCompletado = false;
        if (s.getTurnoId() != null) {
            turnoCompletado = turnoRepository.completarSiProgramado(s.getTurnoId());
            if (turnoCompletado) {
                auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "COMPLETAR_TURNO", "turnos",
                    s.getTurnoId(), "Completado automáticamente al guardar la atención");
            }
        }

        SesionDTO creada = sesionRepository.obtenerPorId(id).orElseThrow();
        return ResponseEntity.status(201).body(Map.of("sesion", creada, "turnoCompletado", turnoCompletado));
    }

    private boolean sinPermisoSobreEstudiante(UsuarioAutenticado u, PacienteDTO p) {
        return u.esPsicologo() && !Objects.equals(p.getPsicologoId(), u.getUsuarioId());
    }

    private UsuarioAutenticado usuario(Authentication auth) {
        return (UsuarioAutenticado) auth.getPrincipal();
    }
}
