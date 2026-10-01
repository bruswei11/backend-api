package com.sistemapsicologia.api.controller;

import com.sistemapsicologia.api.dto.PacienteDTO;
import com.sistemapsicologia.api.repository.AuditoriaRepository;
import com.sistemapsicologia.api.repository.PacienteRepository;
import com.sistemapsicologia.api.repository.TurnoRepository;
import com.sistemapsicologia.api.security.UsuarioAutenticado;
import com.sistemapsicologia.api.util.EstructuraAcademica;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Reglas de rol re-implementadas acá tal como viven hoy en Vista/GestionPacientes.java del
 * escritorio: psicologo ve/opera solo sus propios estudiantes (filtro en la consulta SQL, no
 * traer todo y filtrar en memoria); admin/secretaria ven todos y eligen el profesional asignado
 * (o ninguno). Un psicologo no puede crear ni reasignar un estudiante a otro psicologo -- el
 * servidor pisa ese campo del body, nunca confía en lo que mande el cliente.
 */
@RestController
@RequestMapping("/api/pacientes")
public class PacienteController {

    private final PacienteRepository pacienteRepository;
    private final TurnoRepository turnoRepository;
    private final AuditoriaRepository auditoriaRepository;

    public PacienteController(PacienteRepository pacienteRepository, TurnoRepository turnoRepository,
            AuditoriaRepository auditoriaRepository) {
        this.pacienteRepository = pacienteRepository;
        this.turnoRepository = turnoRepository;
        this.auditoriaRepository = auditoriaRepository;
    }

    @GetMapping
    public List<PacienteDTO> listar(Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        return filtrarParaRol(u, pacienteRepository.listar(u.esPsicologo() ? u.getUsuarioId() : null));
    }

    @GetMapping("/search")
    public List<PacienteDTO> buscar(@RequestParam String q, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        return filtrarParaRol(u, pacienteRepository.buscar(q, u.esPsicologo() ? u.getUsuarioId() : null));
    }

    /** Catálogo oficial de cursos (el mismo util.EstructuraAcademica del escritorio). */
    @GetMapping("/cursos")
    public List<String> cursos() {
        return EstructuraAcademica.todasLasEtiquetas();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@PathVariable int id, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        Optional<PacienteDTO> pacienteOpt = pacienteRepository.obtenerPorId(id);
        if (pacienteOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PacienteDTO p = pacienteOpt.get();
        if (sinPermiso(u, p)) {
            return ResponseEntity.status(403).body(Map.of("error", "No autorizado para ver este estudiante"));
        }
        auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "VER_PACIENTE", "pacientes", id, null);
        return ResponseEntity.ok(filtrarParaRol(u, p));
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody PacienteDTO p, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        String error = validar(p, null);
        if (error == null) {
            error = asignarProfesional(u, p, p.getPsicologoId());
        }
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("error", error));
        }
        p.setConsentimientoFecha(p.isConsentimientoTutor() ? LocalDateTime.now() : null);
        int id = pacienteRepository.crear(p);
        auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "CREAR_PACIENTE", "pacientes", id, "Desde la app móvil");
        return ResponseEntity.status(201).body(filtrarParaRol(u, pacienteRepository.obtenerPorId(id).orElseThrow()));
    }

    /**
     * Como "Editar datos" del escritorio: parte del registro guardado y solo reemplaza los datos
     * del formulario, así los campos que el formulario no muestra (antecedentes, anamnesis,
     * fecha del consentimiento) no se borran por llegar vacíos.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable int id, @RequestBody PacienteDTO cambios, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        Optional<PacienteDTO> existenteOpt = pacienteRepository.obtenerPorId(id);
        if (existenteOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PacienteDTO p = existenteOpt.get();
        if (sinPermiso(u, p)) {
            return ResponseEntity.status(403).body(Map.of("error", "No autorizado para editar este estudiante"));
        }
        String error = validar(cambios, id);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("error", error));
        }

        p.setNombre(cambios.getNombre());
        p.setApellido(cambios.getApellido());
        p.setCi(cambios.getCi());
        p.setTelefono(cambios.getTelefono());
        p.setGenero(cambios.getGenero());
        p.setNombreTutor(cambios.getNombreTutor());
        p.setCiTutor(cambios.getCiTutor());
        p.setCurso(cambios.getCurso());
        if (cambios.isConsentimientoTutor()) {
            if (!p.isConsentimientoTutor() || p.getConsentimientoFecha() == null) {
                p.setConsentimientoFecha(LocalDateTime.now());
            }
            p.setConsentimientoTutor(true);
        } else {
            p.setConsentimientoTutor(false);
            p.setConsentimientoFecha(null);
        }
        if (!u.esPsicologo()) {
            error = asignarProfesional(u, p, cambios.getPsicologoId());
            if (error != null) {
                return ResponseEntity.badRequest().body(Map.of("error", error));
            }
        }

        pacienteRepository.actualizar(p);
        auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "EDITAR_PACIENTE", "pacientes", id, "Desde la app móvil");
        return ResponseEntity.ok(filtrarParaRol(u, pacienteRepository.obtenerPorId(id).orElseThrow()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable int id, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        Optional<PacienteDTO> existenteOpt = pacienteRepository.obtenerPorId(id);
        if (existenteOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (sinPermiso(u, existenteOpt.get())) {
            return ResponseEntity.status(403).body(Map.of("error", "No autorizado para eliminar este estudiante"));
        }
        String error = pacienteRepository.eliminar(id);
        if (error != null) {
            return ResponseEntity.status(409).body(Map.of("error", error));
        }
        auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "ELIMINAR_PACIENTE", "pacientes", id, "Desde la app móvil");
        return ResponseEntity.noContent().build();
    }

    /** Mismas validaciones que el formulario del escritorio. null = todo bien. */
    private String validar(PacienteDTO p, Integer idActual) {
        if (p.getNombre() == null || p.getNombre().isBlank()) {
            return "El nombre es obligatorio";
        }
        if (p.getApellido() == null || p.getApellido().isBlank()) {
            return "El apellido es obligatorio";
        }
        p.setNombre(p.getNombre().trim());
        p.setApellido(p.getApellido().trim());
        p.setCi(limpiarNumero(p.getCi()));
        p.setCiTutor(limpiarNumero(p.getCiTutor()));
        if (p.getCi() != null && !p.getCi().matches("[0-9]+")) {
            return "La CI solo puede tener números";
        }
        if (p.getCiTutor() != null && !p.getCiTutor().matches("[0-9]+")) {
            return "La CI del tutor/a solo puede tener números";
        }
        if (pacienteRepository.existeCi(p.getCi(), idActual)) {
            return "Ya hay un estudiante registrado con la CI " + p.getCi();
        }
        if (p.getCurso() != null && p.getCurso().isBlank()) {
            p.setCurso(null);
        }
        if (p.getCurso() != null && !EstructuraAcademica.esEtiquetaValida(p.getCurso())) {
            return "El curso no coincide con la estructura académica del colegio";
        }
        if (p.getFechaNacimiento() != null && p.getFechaNacimiento().isAfter(java.time.LocalDate.now())) {
            return "La fecha de nacimiento no puede ser futura";
        }
        return null;
    }

    private static String limpiarNumero(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.replaceAll("[.\\s-]", "");
        return limpio.isEmpty() ? null : limpio;
    }

    /** psicologo: siempre él mismo. admin/secretaria: un profesional activo o ninguno ("Sin asignar"). */
    private String asignarProfesional(UsuarioAutenticado u, PacienteDTO p, Integer pedido) {
        if (u.esPsicologo()) {
            p.setPsicologoId(u.getUsuarioId());
            return null;
        }
        if (pedido == null || pedido <= 0) {
            p.setPsicologoId(null);
            return null;
        }
        boolean existe = turnoRepository.listarPsicologos().stream().anyMatch(ps -> ps.id() == pedido);
        if (!existe) {
            return "El profesional elegido no existe o está inactivo";
        }
        p.setPsicologoId(pedido);
        return null;
    }

    /**
     * El "Usuario autorizado" (secretaria) no recibe el texto clínico heredado de la ficha
     * (motivo, antecedentes, anamnesis): en el escritorio tampoco lo ve.
     */
    private static PacienteDTO filtrarParaRol(UsuarioAutenticado u, PacienteDTO p) {
        if (u.esSecretaria()) {
            p.setMotivoConsulta(null);
            p.setAntecedentesPersonales(null);
            p.setAntecedentesFamiliares(null);
            p.setAnamnesis(null);
        }
        return p;
    }

    private static List<PacienteDTO> filtrarParaRol(UsuarioAutenticado u, List<PacienteDTO> lista) {
        lista.forEach(p -> filtrarParaRol(u, p));
        return lista;
    }

    private boolean sinPermiso(UsuarioAutenticado u, PacienteDTO p) {
        return u.esPsicologo() && !Objects.equals(p.getPsicologoId(), u.getUsuarioId());
    }

    private UsuarioAutenticado usuario(Authentication auth) {
        return (UsuarioAutenticado) auth.getPrincipal();
    }
}
