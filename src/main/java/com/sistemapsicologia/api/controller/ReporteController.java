package com.sistemapsicologia.api.controller;

import com.sistemapsicologia.api.dto.HistoriaDTO;
import com.sistemapsicologia.api.dto.PacienteDTO;
import com.sistemapsicologia.api.dto.SesionDTO;
import com.sistemapsicologia.api.repository.AuditoriaRepository;
import com.sistemapsicologia.api.repository.HistoriaRepository;
import com.sistemapsicologia.api.repository.PacienteRepository;
import com.sistemapsicologia.api.repository.SesionRepository;
import com.sistemapsicologia.api.security.UsuarioAutenticado;
import com.sistemapsicologia.api.util.Reportes;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * "Reportes y archivos" del escritorio: exportar la lista a Excel, la ficha del estudiante y la nota
 * de una atención, más el historial de lo exportado. En la web el archivo se descarga en el
 * dispositivo (no hay carpeta reportes/ en el servidor); cada exportación queda en auditoría con
 * los mismos códigos que el escritorio (EXPORTAR_ESTUDIANTES / EXPORTAR_FICHA / EXPORTAR_NOTA).
 */
@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private final PacienteRepository pacientes;
    private final HistoriaRepository historias;
    private final SesionRepository sesiones;
    private final AuditoriaRepository auditoria;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public ReporteController(PacienteRepository pacientes, HistoriaRepository historias, SesionRepository sesiones,
            AuditoriaRepository auditoria, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.pacientes = pacientes;
        this.historias = historias;
        this.sesiones = sesiones;
        this.auditoria = auditoria;
        this.jdbc = jdbc;
    }

    /**
     * Tablas del respaldo (en orden de dependencias), cada una filtrada a los datos del profesional
     * que lo pide: sus estudiantes y lo que cuelga de ellos, y su propio registro de actividad.
     */
    private static final String[][] TABLAS_RESPALDO = {
        {"pacientes", "SELECT * FROM pacientes WHERE psicologo_id = ? ORDER BY id"},
        {"turnos", "SELECT * FROM turnos WHERE paciente_id IN (SELECT id FROM pacientes WHERE psicologo_id = ?) ORDER BY id"},
        {"sesiones", "SELECT * FROM sesiones WHERE paciente_id IN (SELECT id FROM pacientes WHERE psicologo_id = ?) ORDER BY id"},
        {"historia_psicologica", "SELECT * FROM historia_psicologica WHERE paciente_id IN (SELECT id FROM pacientes WHERE psicologo_id = ?) ORDER BY id"},
        {"documentos_paciente", "SELECT * FROM documentos_paciente WHERE paciente_id IN (SELECT id FROM pacientes WHERE psicologo_id = ?) ORDER BY id"},
        {"auditoria", "SELECT * FROM auditoria WHERE usuario_id = ? ORDER BY id"}};

    /**
     * "Respaldar mis datos": un .zip con una planilla CSV por tabla, para guardar una copia propia
     * cuando el sistema vive en la nube. El contenido de los adjuntos no se incluye (solo sus
     * datos), para que el archivo no sea enorme.
     */
    @GetMapping("/respaldo")
    public ResponseEntity<?> respaldo(Authentication auth) throws java.io.IOException {
        UsuarioAutenticado u = usuario(auth);
        java.io.ByteArrayOutputStream salida = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(salida, StandardCharsets.UTF_8)) {
            for (String[] consulta : TABLAS_RESPALDO) {
                String tabla = consulta[0];
                StringBuilder csv = new StringBuilder(Reportes.BOM);
                jdbc.query(consulta[1], (java.sql.ResultSet rs) -> {
                    java.sql.ResultSetMetaData md = rs.getMetaData();
                    int n = md.getColumnCount();
                    for (int i = 1; i <= n; i++) {
                        csv.append(i > 1 ? ";" : "").append(md.getColumnLabel(i));
                    }
                    csv.append("\r\n");
                    while (rs.next()) {
                        for (int i = 1; i <= n; i++) {
                            Object v = rs.getObject(i);
                            String t = v == null ? "" : String.valueOf(v);
                            if (t.contains(";") || t.contains("\"") || t.contains("\n") || t.contains("\r")) {
                                t = "\"" + t.replace("\"", "\"\"") + "\"";
                            }
                            csv.append(i > 1 ? ";" : "").append(t);
                        }
                        csv.append("\r\n");
                    }
                    return null;
                }, u.getUsuarioId());
                zip.putNextEntry(new java.util.zip.ZipEntry(tabla + ".csv"));
                zip.write(csv.toString().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        String nombre = "Respaldo_psicologia_" + java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".zip";
        auditoria.registrar(u.getUsuarioId(), u.getNombre(), "EXPORTAR_RESPALDO", "auditoria", null, nombre);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(nombre, StandardCharsets.UTF_8).build().toString())
            .body(salida.toByteArray());
    }

    /**
     * @param ids los estudiantes que se ven en la lista (filtro aplicado); vacío = todos los visibles.
     * @param descripcion qué se exportó, para el registro (ej. "filtro: 9° EEB").
     */
    @GetMapping("/estudiantes")
    public ResponseEntity<?> listado(@RequestParam(required = false) List<Integer> ids,
            @RequestParam(defaultValue = "todos") String descripcion, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        List<PacienteDTO> lista = pacientes.listar(u.esPsicologo() ? u.getUsuarioId() : null);
        if (ids != null && !ids.isEmpty()) {
            Set<Integer> pedidos = Set.copyOf(ids);
            lista = lista.stream().filter(p -> pedidos.contains(p.getId())).collect(Collectors.toList());
        }
        lista.sort((a, b) -> (a.getApellido() + a.getNombre()).compareToIgnoreCase(b.getApellido() + b.getNombre()));
        String nombre = Reportes.nombreListado();
        String csv = Reportes.BOM + Reportes.csvEstudiantes(lista, !u.esSecretaria());
        auditoria.registrar(u.getUsuarioId(), u.getNombre(), "EXPORTAR_ESTUDIANTES", "pacientes", null,
            recortar(lista.size() + " estudiantes (" + descripcion + ") → " + nombre));
        return archivo(csv, nombre, "text/csv");
    }

    @GetMapping("/ficha/{pacienteId}")
    public ResponseEntity<?> ficha(@PathVariable int pacienteId, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        Optional<PacienteDTO> p = pacientes.obtenerPorId(pacienteId);
        if (p.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (u.esSecretaria() || sinPermiso(u, p.get())) {
            return ResponseEntity.status(403).body(Map.of("error", "No autorizado a la ficha de este estudiante"));
        }
        List<HistoriaDTO> entradas = historias.listarPorPaciente(pacienteId);
        String nombre = Reportes.nombreArchivo("Ficha", p.get(), "txt");
        String texto = Reportes.ficha(p.get(), entradas.isEmpty() ? null : entradas.get(0), u.getNombre());
        auditoria.registrar(u.getUsuarioId(), u.getNombre(), "EXPORTAR_FICHA", "pacientes", pacienteId, nombre);
        return archivo(texto, nombre, "text/plain");
    }

    @GetMapping("/nota/{sesionId}")
    public ResponseEntity<?> nota(@PathVariable int sesionId, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        Optional<SesionDTO> s = sesiones.obtenerPorId(sesionId);
        if (s.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PacienteDTO p = pacientes.obtenerPorId(s.get().getPacienteId()).orElseThrow();
        if (u.esSecretaria() || sinPermiso(u, p)) {
            return ResponseEntity.status(403).body(Map.of("error", "No autorizado a esta atención"));
        }
        String nombre = Reportes.nombreArchivo("Nota_atencion", p, "txt");
        auditoria.registrar(u.getUsuarioId(), u.getNombre(), "EXPORTAR_NOTA", "sesiones", sesionId, nombre);
        return archivo(Reportes.notaAtencion(p, s.get(), u.getNombre()), nombre, "text/plain");
    }

    /** Lo exportado (propio; el admin ve todo): la lista de "documentos generados" del escritorio. */
    @GetMapping("/historial")
    public List<?> historial(Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        return auditoria.exportaciones(u.esAdmin() ? null : u.getUsuarioId(), 100);
    }

    private static ResponseEntity<?> archivo(String contenido, String nombre, String tipo) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(tipo + ";charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(nombre, StandardCharsets.UTF_8).build().toString())
            .body(contenido.getBytes(StandardCharsets.UTF_8));
    }

    private static String recortar(String texto) {
        return texto.length() > 250 ? texto.substring(0, 250) : texto;
    }

    private static boolean sinPermiso(UsuarioAutenticado u, PacienteDTO p) {
        return u.esPsicologo() && !Objects.equals(p.getPsicologoId(), u.getUsuarioId());
    }

    private static UsuarioAutenticado usuario(Authentication auth) {
        return (UsuarioAutenticado) auth.getPrincipal();
    }
}
