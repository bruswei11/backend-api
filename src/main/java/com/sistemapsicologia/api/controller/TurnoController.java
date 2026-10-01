package com.sistemapsicologia.api.controller;

import com.sistemapsicologia.api.dto.PsicologoDTO;
import com.sistemapsicologia.api.dto.TurnoDTO;
import com.sistemapsicologia.api.repository.AuditoriaRepository;
import com.sistemapsicologia.api.repository.TurnoRepository;
import com.sistemapsicologia.api.security.UsuarioAutenticado;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Misma lógica que Vista/Agenda.java del escritorio (esa pantalla corre SQL propio, no un DAO
 * limpio -- ver TurnoRepository para el detalle). Reglas iguales a pacientes: psicologo solo
 * ve/opera sus propias citas y no puede asignarlas a otro profesional; el chequeo de
 * solapamiento devuelve 409 con el mismo mensaje del diálogo de confirmación del escritorio, y el
 * cliente puede reintentar con ?forzar=true (equivalente a apretar "Sí" en esa confirmación).
 */
@RestController
@RequestMapping("/api/turnos")
public class TurnoController {

    private final TurnoRepository turnoRepository;
    private final AuditoriaRepository auditoriaRepository;

    public TurnoController(TurnoRepository turnoRepository, AuditoriaRepository auditoriaRepository) {
        this.turnoRepository = turnoRepository;
        this.auditoriaRepository = auditoriaRepository;
    }

    @GetMapping
    public List<TurnoDTO> listar(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso =
                org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Integer pacienteId,
            Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        Integer psicologoId = u.esPsicologo() ? u.getUsuarioId() : null;
        turnoRepository.marcarVencidasComoAusente(psicologoId);
        return turnoRepository.listar(psicologoId, fecha, pacienteId);
    }

    @GetMapping("/psicologos")
    public List<PsicologoDTO> listarPsicologos() {
        return turnoRepository.listarPsicologos();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@PathVariable int id, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        Optional<TurnoDTO> turnoOpt = turnoRepository.obtenerPorId(id);
        if (turnoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (sinPermiso(u, turnoOpt.get())) {
            return ResponseEntity.status(403).body(Map.of("error", "No autorizado para ver esta cita"));
        }
        return ResponseEntity.ok(turnoOpt.get());
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody TurnoRequest req,
            @RequestParam(defaultValue = "false") boolean forzar, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        if (!u.esPsicologo() && req.psicologoId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Falta indicar el profesional (psicologoId)"));
        }
        int psicologoId = u.esPsicologo() ? u.getUsuarioId() : req.psicologoId();
        String estado = req.estado() != null ? req.estado() : "programado";

        if (!forzar && turnoRepository.haySolapamiento(psicologoId,
                Timestamp.valueOf(req.fechaHora()), TurnoRepository.DURACION_MINUTOS, null)) {
            return ResponseEntity.status(409).body(Map.of(
                "advertencia", "Este horario se superpone con otra cita del mismo profesional",
                "requiereConfirmacion", true));
        }

        int id = turnoRepository.crear(req.pacienteId(), psicologoId, req.fechaHora(), estado, req.notas());
        auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "CREAR_TURNO", "turnos", id, null);
        return ResponseEntity.status(201).body(turnoRepository.obtenerPorId(id).orElseThrow());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable int id, @RequestBody TurnoRequest req,
            @RequestParam(defaultValue = "false") boolean forzar, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        Optional<TurnoDTO> existenteOpt = turnoRepository.obtenerPorId(id);
        if (existenteOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (sinPermiso(u, existenteOpt.get())) {
            return ResponseEntity.status(403).body(Map.of("error", "No autorizado para editar esta cita"));
        }

        if (!u.esPsicologo() && req.psicologoId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Falta indicar el profesional (psicologoId)"));
        }
        int psicologoId = u.esPsicologo() ? u.getUsuarioId() : req.psicologoId();
        String estado = req.estado() != null ? req.estado() : existenteOpt.get().getEstado();

        if (!forzar && turnoRepository.haySolapamiento(psicologoId,
                Timestamp.valueOf(req.fechaHora()), TurnoRepository.DURACION_MINUTOS, id)) {
            return ResponseEntity.status(409).body(Map.of(
                "advertencia", "Este horario se superpone con otra cita del mismo profesional",
                "requiereConfirmacion", true));
        }

        turnoRepository.actualizar(id, req.pacienteId(), psicologoId, req.fechaHora(), estado, req.notas());
        auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "EDITAR_TURNO", "turnos", id, null);
        return ResponseEntity.ok(turnoRepository.obtenerPorId(id).orElseThrow());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable int id, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        Optional<TurnoDTO> existenteOpt = turnoRepository.obtenerPorId(id);
        if (existenteOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (sinPermiso(u, existenteOpt.get())) {
            return ResponseEntity.status(403).body(Map.of("error", "No autorizado para eliminar esta cita"));
        }

        try {
            turnoRepository.eliminar(id);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(409).body(Map.of("error",
                "No se puede eliminar: esta cita tiene una atención registrada. Cambiá el estado a \"cancelado\" en vez de borrarla."));
        }

        auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "ELIMINAR_TURNO", "turnos", id, null);
        return ResponseEntity.noContent().build();
    }

    private boolean sinPermiso(UsuarioAutenticado u, TurnoDTO t) {
        return u.esPsicologo() && t.getPsicologoId() != u.getUsuarioId();
    }

    private UsuarioAutenticado usuario(Authentication auth) {
        return (UsuarioAutenticado) auth.getPrincipal();
    }

    public record TurnoRequest(int pacienteId, Integer psicologoId, LocalDateTime fechaHora, String estado, String notas) {}
}
