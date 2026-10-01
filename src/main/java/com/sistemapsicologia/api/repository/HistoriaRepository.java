package com.sistemapsicologia.api.repository;

import com.sistemapsicologia.api.dto.HistoriaDTO;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** Mismas columnas que DAO.HistoriaPsicologicaDAO del escritorio. Sin método de actualizar a propósito. */
@Repository
public class HistoriaRepository {

    private final JdbcTemplate jdbcTemplate;

    public HistoriaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<HistoriaDTO> listarPorPaciente(int pacienteId) {
        String sql = "SELECT h.*, u.nombre AS psicologo_nombre FROM historia_psicologica h " +
            "LEFT JOIN usuarios u ON u.id = h.psicologo_id WHERE h.paciente_id = ? ORDER BY h.fecha_creacion DESC";
        return jdbcTemplate.query(sql, HistoriaRepository::mapear, pacienteId);
    }

    public Optional<HistoriaDTO> obtenerPorId(int id) {
        String sql = "SELECT h.*, u.nombre AS psicologo_nombre FROM historia_psicologica h " +
            "LEFT JOIN usuarios u ON u.id = h.psicologo_id WHERE h.id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, HistoriaRepository::mapear, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public int crear(HistoriaDTO h) {
        String sql = "INSERT INTO historia_psicologica (paciente_id, psicologo_id, antecedentes, motivo_consulta, " +
            "observaciones_generales, diagnostico, tratamiento, fecha_creacion, ultima_actualizacion, " +
            "codigo_cie10, tipo_acoso, es_reiterado) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        LocalDateTime ahora = LocalDateTime.now();

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setInt(1, h.getPacienteId());
            ps.setInt(2, h.getPsicologoId());
            ps.setString(3, h.getAntecedentes());
            ps.setString(4, h.getMotivoConsulta());
            ps.setString(5, h.getObservacionesGenerales());
            ps.setString(6, h.getDiagnostico());
            ps.setString(7, h.getTratamiento());
            ps.setTimestamp(8, Timestamp.valueOf(ahora));
            ps.setTimestamp(9, Timestamp.valueOf(ahora));
            if (h.getCodigoCie10() == null || h.getCodigoCie10().isBlank()) {
                ps.setNull(10, java.sql.Types.VARCHAR);
            } else {
                ps.setString(10, h.getCodigoCie10().trim());
            }
            if (h.getTipoAcoso() == null || h.getTipoAcoso().isBlank()) {
                ps.setNull(11, java.sql.Types.VARCHAR);
            } else {
                ps.setString(11, h.getTipoAcoso());
            }
            ps.setBoolean(12, h.isEsReiterado());
            return ps;
        }, keyHolder);

        return keyHolder.getKey().intValue();
    }

    /** Mismo criterio que HistoriaPsicologicaDAO.contarPorMotivo del escritorio: línea por línea contra util.MotivosAtencion. */
    public java.util.LinkedHashMap<String, Integer> contarPorMotivo(java.time.LocalDate desde, Integer psicologoId) {
        java.util.Map<String, Integer> conteo = new java.util.LinkedHashMap<>();
        String sql = "SELECT hp.motivo_consulta FROM historia_psicologica hp JOIN pacientes p ON p.id = hp.paciente_id " +
            "WHERE hp.motivo_consulta IS NOT NULL AND hp.motivo_consulta <> '' AND hp.fecha_creacion >= ? " +
            (psicologoId != null ? "AND p.psicologo_id = ? " : "");
        Object[] params = psicologoId != null
            ? new Object[]{Timestamp.valueOf(desde.atStartOfDay()), psicologoId}
            : new Object[]{Timestamp.valueOf(desde.atStartOfDay())};

        jdbcTemplate.query(sql, rs -> {
            String motivo = rs.getString("motivo_consulta");
            if (motivo == null) {
                return;
            }
            for (String linea : motivo.split("\n")) {
                String limpio = linea.trim();
                if (limpio.isEmpty()) {
                    continue;
                }
                String clave = com.sistemapsicologia.api.util.MotivosAtencion.OTRO;
                for (String preset : com.sistemapsicologia.api.util.MotivosAtencion.PRESETS) {
                    if (preset.equalsIgnoreCase(limpio)) {
                        clave = preset;
                        break;
                    }
                }
                conteo.merge(clave, 1, Integer::sum);
            }
        }, params);

        java.util.LinkedHashMap<String, Integer> resultado = new java.util.LinkedHashMap<>();
        conteo.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .forEach(e -> resultado.put(e.getKey(), e.getValue()));
        return resultado;
    }

    private String sqlCasosReiteradosSinSeguimiento(Integer psicologoId) {
        return "SELECT p.id AS paciente_id, p.nombre, p.apellido, p.curso, hp.fecha_creacion " +
            "FROM historia_psicologica hp " +
            "INNER JOIN (SELECT paciente_id, MAX(id) AS ultimo_id FROM historia_psicologica GROUP BY paciente_id) ultima " +
            "  ON hp.paciente_id = ultima.paciente_id AND hp.id = ultima.ultimo_id " +
            "INNER JOIN pacientes p ON p.id = hp.paciente_id " +
            "WHERE hp.es_reiterado = TRUE AND hp.fecha_creacion < ? " +
            (psicologoId != null ? "AND p.psicologo_id = ? " : "") +
            "ORDER BY hp.fecha_creacion ASC";
    }

    public int contarCasosReiteradosSinSeguimiento(Integer psicologoId, int diasUmbral) {
        String sql = "SELECT COUNT(*) FROM (" + sqlCasosReiteradosSinSeguimiento(psicologoId) + ") x";
        Timestamp umbral = Timestamp.valueOf(LocalDateTime.now().minusDays(diasUmbral));
        Integer total = psicologoId != null
            ? jdbcTemplate.queryForObject(sql, Integer.class, umbral, psicologoId)
            : jdbcTemplate.queryForObject(sql, Integer.class, umbral);
        return total != null ? total : 0;
    }

    public List<CasoPendiente> obtenerCasosReiteradosSinSeguimiento(int limite, Integer psicologoId, int diasUmbral) {
        String sql = sqlCasosReiteradosSinSeguimiento(psicologoId) + " LIMIT ?";
        Timestamp umbral = Timestamp.valueOf(LocalDateTime.now().minusDays(diasUmbral));
        Object[] params = psicologoId != null
            ? new Object[]{umbral, psicologoId, limite}
            : new Object[]{umbral, limite};
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CasoPendiente(
            rs.getInt("paciente_id"),
            rs.getString("nombre") + " " + rs.getString("apellido"),
            rs.getString("curso"),
            rs.getTimestamp("fecha_creacion").toLocalDateTime()
        ), params);
    }

    public record CasoPendiente(int pacienteId, String estudiante, String curso, LocalDateTime ultimoSeguimiento) {}

    private static HistoriaDTO mapear(ResultSet rs, int rowNum) throws SQLException {
        HistoriaDTO h = new HistoriaDTO();
        h.setId(rs.getInt("id"));
        h.setPacienteId(rs.getInt("paciente_id"));
        h.setPsicologoId(rs.getInt("psicologo_id"));
        h.setPsicologoNombre(rs.getString("psicologo_nombre"));
        h.setAntecedentes(rs.getString("antecedentes"));
        h.setMotivoConsulta(rs.getString("motivo_consulta"));
        h.setObservacionesGenerales(rs.getString("observaciones_generales"));
        h.setDiagnostico(rs.getString("diagnostico"));
        h.setTratamiento(rs.getString("tratamiento"));
        h.setCodigoCie10(rs.getString("codigo_cie10"));
        h.setTipoAcoso(rs.getString("tipo_acoso"));
        h.setEsReiterado(rs.getBoolean("es_reiterado"));
        Timestamp fechaCreacion = rs.getTimestamp("fecha_creacion");
        h.setFechaCreacion(fechaCreacion != null ? fechaCreacion.toLocalDateTime() : null);
        return h;
    }
}
