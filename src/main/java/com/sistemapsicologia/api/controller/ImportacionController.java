package com.sistemapsicologia.api.controller;

import com.sistemapsicologia.api.dto.PacienteDTO;
import com.sistemapsicologia.api.repository.AuditoriaRepository;
import com.sistemapsicologia.api.repository.PacienteRepository;
import com.sistemapsicologia.api.repository.TurnoRepository;
import com.sistemapsicologia.api.security.UsuarioAutenticado;
import com.sistemapsicologia.api.util.EstructuraAcademica;
import com.sistemapsicologia.api.util.ImportadorEstudiantes;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * "Importar desde Excel" de la lista de estudiantes (ImportarEstudiantes del escritorio), en dos
 * pasos: previsualizar (se lee el CSV y se valida fila por fila, sin guardar nada) y confirmar (se
 * crean las filas elegidas, volviendo a validar en el servidor). Cada alta queda auditada como
 * CREAR_PACIENTE "Importado desde …" más un IMPORTAR_ESTUDIANTES con el resumen.
 */
@RestController
@RequestMapping("/api/importacion")
public class ImportacionController {

    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private final PacienteRepository pacientes;
    private final TurnoRepository turnos;
    private final AuditoriaRepository auditoria;

    public ImportacionController(PacienteRepository pacientes, TurnoRepository turnos, AuditoriaRepository auditoria) {
        this.pacientes = pacientes;
        this.turnos = turnos;
        this.auditoria = auditoria;
    }

    @PostMapping("/previsualizar")
    public ResponseEntity<?> previsualizar(@RequestParam("archivo") MultipartFile archivo) throws IOException {
        if (archivo.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo está vacío"));
        }
        if (archivo.getSize() > MAX_BYTES) {
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo es demasiado grande (máximo 2 MB)"));
        }
        return ResponseEntity.ok(ImportadorEstudiantes.leer(archivo.getBytes(), pacientes.obtenerTodasLasCi()));
    }

    public record Confirmacion(String archivo, Integer psicologoId, List<PacienteDTO> pacientes) {}

    @PostMapping("/confirmar")
    public ResponseEntity<?> confirmar(@RequestBody Confirmacion pedido, Authentication auth) {
        UsuarioAutenticado u = (UsuarioAutenticado) auth.getPrincipal();
        if (pedido.pacientes() == null || pedido.pacientes().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No hay estudiantes para importar"));
        }
        Integer profesional;
        if (u.esPsicologo()) {
            profesional = u.getUsuarioId(); // un profesional solo carga estudiantes a su nombre
        } else if (pedido.psicologoId() == null || pedido.psicologoId() <= 0) {
            profesional = null;
        } else if (turnos.listarPsicologos().stream().anyMatch(ps -> ps.id() == pedido.psicologoId())) {
            profesional = pedido.psicologoId();
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "El profesional elegido no existe o está inactivo"));
        }

        String origen = pedido.archivo() != null ? pedido.archivo() : "planilla";
        Set<String> cis = new HashSet<>(pacientes.obtenerTodasLasCi());
        int importados = 0;
        List<Map<String, String>> omitidos = new ArrayList<>();
        for (PacienteDTO p : pedido.pacientes()) {
            String motivo = null;
            if (p.getNombre() == null || p.getNombre().isBlank() || p.getApellido() == null || p.getApellido().isBlank()) {
                motivo = "Falta nombre o apellido";
            } else if (p.getCi() != null && !p.getCi().isBlank() && !cis.add(p.getCi().trim())) {
                motivo = "CI " + p.getCi() + " ya registrada";
            } else if (p.getCurso() != null && !p.getCurso().isBlank() && !EstructuraAcademica.esEtiquetaValida(p.getCurso())) {
                motivo = "Curso no reconocido";
            }
            if (motivo != null) {
                Map<String, String> o = new LinkedHashMap<>();
                o.put("estudiante", (p.getNombre() == null ? "" : p.getNombre()) + " " + (p.getApellido() == null ? "" : p.getApellido()));
                o.put("motivo", motivo);
                omitidos.add(o);
                continue;
            }
            p.setId(null);
            p.setPsicologoId(profesional);
            p.setConsentimientoTutor(false);
            p.setConsentimientoFecha(null);
            p.setCreadoEn(LocalDateTime.now());
            int id = pacientes.crear(p);
            auditoria.registrar(u.getUsuarioId(), u.getNombre(), "CREAR_PACIENTE", "pacientes", id, recortar("Importado desde " + origen));
            importados++;
        }
        auditoria.registrar(u.getUsuarioId(), u.getNombre(), "IMPORTAR_ESTUDIANTES", "pacientes", null,
            recortar(importados + " estudiantes importados desde " + origen
                + (omitidos.isEmpty() ? "" : " (" + omitidos.size() + " omitidos)")));
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("importados", importados);
        respuesta.put("omitidos", omitidos);
        return ResponseEntity.ok(respuesta);
    }

    private static String recortar(String texto) {
        return texto.length() > 250 ? texto.substring(0, 250) : texto;
    }
}
