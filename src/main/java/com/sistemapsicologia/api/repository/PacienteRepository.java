package com.sistemapsicologia.api.repository;

import com.sistemapsicologia.api.dto.PacienteDTO;
import java.sql.Date;
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

/**
 * Mismas consultas/columnas que DAO.PacienteDAO del escritorio (misma tabla `pacientes`), para que
 * ambos caminos (JDBC directo del escritorio, esta API para el celular) escriban exactamente la
 * misma forma de fila -- ningún campo queda solo del lado de uno de los dos.
 */
@Repository
public class PacienteRepository {

    private final JdbcTemplate jdbcTemplate;

    public PacienteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** psicologoId null = sin filtro (admin/secretaria); no null = solo esos estudiantes (psicologo). */
    public List<PacienteDTO> listar(Integer psicologoId) {
        String sql = "SELECT * FROM pacientes" + (psicologoId != null ? " WHERE psicologo_id = ?" : "") + " ORDER BY nombre";
        return psicologoId != null
            ? jdbcTemplate.query(sql, PacienteRepository::mapear, psicologoId)
            : jdbcTemplate.query(sql, PacienteRepository::mapear);
    }

    public List<PacienteDTO> buscar(String termino, Integer psicologoId) {
        String like = "%" + termino.toLowerCase(java.util.Locale.ROOT) + "%";
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM pacientes WHERE (LOWER(nombre) LIKE ? OR LOWER(apellido) LIKE ? OR telefono LIKE ? OR ci LIKE ?)");
        List<Object> params = new java.util.ArrayList<>(List.of(like, like, like, like));
        if (psicologoId != null) {
            sql.append(" AND psicologo_id = ?");
            params.add(psicologoId);
        }
        sql.append(" ORDER BY nombre");
        return jdbcTemplate.query(sql.toString(), PacienteRepository::mapear, params.toArray());
    }

    public Optional<PacienteDTO> obtenerPorId(int id) {
        try {
            return Optional.ofNullable(
                jdbcTemplate.queryForObject("SELECT * FROM pacientes WHERE id = ?", PacienteRepository::mapear, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public int crear(PacienteDTO p) {
        String sql = "INSERT INTO pacientes (nombre, apellido, ci, telefono, fecha_nacimiento, genero, " +
            "nombre_tutor, ci_tutor, direccion, motivo_consulta, curso, psicologo_id, antecedentes_personales, " +
            "antecedentes_familiares, anamnesis, creado_en, consentimiento_tutor, consentimiento_fecha) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime consentimientoFecha = p.isConsentimientoTutor() ? ahora : null;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, p.getNombre());
            ps.setString(2, p.getApellido());
            ps.setString(3, p.getCi());
            ps.setString(4, p.getTelefono());
            ps.setDate(5, p.getFechaNacimiento() != null ? Date.valueOf(p.getFechaNacimiento()) : null);
            ps.setString(6, p.getGenero());
            ps.setString(7, p.getNombreTutor());
            ps.setString(8, p.getCiTutor());
            ps.setString(9, p.getDireccion());
            ps.setString(10, p.getMotivoConsulta());
            ps.setString(11, p.getCurso());
            if (p.getPsicologoId() != null) {
                ps.setInt(12, p.getPsicologoId());
            } else {
                ps.setNull(12, java.sql.Types.INTEGER);
            }
            ps.setString(13, p.getAntecedentesPersonales());
            ps.setString(14, p.getAntecedentesFamiliares());
            ps.setString(15, p.getAnamnesis());
            ps.setTimestamp(16, Timestamp.valueOf(ahora));
            ps.setBoolean(17, p.isConsentimientoTutor());
            ps.setTimestamp(18, consentimientoFecha != null ? Timestamp.valueOf(consentimientoFecha) : null);
            return ps;
        }, keyHolder);

        return keyHolder.getKey().intValue();
    }

    public int contarDesde(java.time.LocalDate desde, Integer psicologoId) {
        String sql = "SELECT COUNT(*) FROM pacientes WHERE creado_en >= ?" + (psicologoId != null ? " AND psicologo_id = ?" : "");
        Integer total = psicologoId != null
            ? jdbcTemplate.queryForObject(sql, Integer.class, java.sql.Date.valueOf(desde), psicologoId)
            : jdbcTemplate.queryForObject(sql, Integer.class, java.sql.Date.valueOf(desde));
        return total != null ? total : 0;
    }

    /** Todas las CI cargadas (para detectar repetidas al importar una planilla). */
    public java.util.Set<String> obtenerTodasLasCi() {
        return new java.util.HashSet<>(jdbcTemplate.queryForList(
            "SELECT ci FROM pacientes WHERE ci IS NOT NULL AND ci <> ''", String.class));
    }

    /** true si otro estudiante (distinto de excluirId) ya tiene esa CI. */
    public boolean existeCi(String ci, Integer excluirId) {
        if (ci == null || ci.isBlank()) {
            return false;
        }
        Integer total = excluirId != null
            ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pacientes WHERE ci = ? AND id <> ?", Integer.class, ci.trim(), excluirId)
            : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pacientes WHERE ci = ?", Integer.class, ci.trim());
        return total != null && total > 0;
    }

    /** null si se eliminó; si no, el motivo (mismo texto que PacienteDAO.eliminar del escritorio). */
    public String eliminar(int id) {
        try {
            int filas = jdbcTemplate.update("DELETE FROM pacientes WHERE id = ?", id);
            return filas > 0 ? null : "No se encontró el estudiante.";
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return "No se puede eliminar: tiene citas, atenciones, historia clínica o documentos asociados. "
                + "Eliminá o reasigná esos datos primero.";
        }
    }

    /** Últimos `meses` meses, incluyendo los que tienen 0, más viejo primero -- mismo criterio que PacienteDAO.obtenerNuevosPorMes. */
    public java.util.LinkedHashMap<String, Integer> obtenerNuevosPorMes(int meses, Integer psicologoId) {
        java.util.LinkedHashMap<String, Integer> resultado = new java.util.LinkedHashMap<>();
        java.time.YearMonth actual = java.time.YearMonth.now();
        for (int i = meses - 1; i >= 0; i--) {
            resultado.put(actual.minusMonths(i).toString(), 0);
        }
        String sql = "SELECT creado_en FROM pacientes " +
            "WHERE creado_en >= ?" + (psicologoId != null ? " AND psicologo_id = ?" : "");
        java.time.LocalDate desde = actual.minusMonths(meses - 1L).atDay(1);
        Object[] params = psicologoId != null
            ? new Object[]{java.sql.Date.valueOf(desde), psicologoId}
            : new Object[]{java.sql.Date.valueOf(desde)};
        jdbcTemplate.query(sql, rs -> {
            String mes = java.time.YearMonth.from(rs.getTimestamp("creado_en").toLocalDateTime()).toString();
            resultado.computeIfPresent(mes, (k, v) -> v + 1);
        }, params);
        return resultado;
    }

    public java.util.LinkedHashMap<String, Integer> contarPorCurso(Integer psicologoId) {
        java.util.LinkedHashMap<String, Integer> resultado = new java.util.LinkedHashMap<>();
        String sql = "SELECT curso, COUNT(*) AS total FROM pacientes WHERE curso IS NOT NULL AND curso <> ''"
            + (psicologoId != null ? " AND psicologo_id = ?" : "") + " GROUP BY curso ORDER BY curso";
        if (psicologoId != null) {
            jdbcTemplate.query(sql, rs -> { resultado.put(rs.getString("curso"), rs.getInt("total")); }, psicologoId);
        } else {
            jdbcTemplate.query(sql, rs -> { resultado.put(rs.getString("curso"), rs.getInt("total")); });
        }
        return resultado;
    }

    public boolean actualizar(PacienteDTO p) {
        String sql = "UPDATE pacientes SET nombre=?, apellido=?, ci=?, telefono=?, fecha_nacimiento=?, genero=?, " +
            "nombre_tutor=?, ci_tutor=?, direccion=?, motivo_consulta=?, curso=?, psicologo_id=?, " +
            "antecedentes_personales=?, antecedentes_familiares=?, anamnesis=?, consentimiento_tutor=?, " +
            "consentimiento_fecha=? WHERE id=?";

        int filas = jdbcTemplate.update(sql,
            p.getNombre(), p.getApellido(), p.getCi(), p.getTelefono(),
            p.getFechaNacimiento() != null ? Date.valueOf(p.getFechaNacimiento()) : null,
            p.getGenero(), p.getNombreTutor(), p.getCiTutor(), p.getDireccion(), p.getMotivoConsulta(),
            p.getCurso(), p.getPsicologoId(), p.getAntecedentesPersonales(), p.getAntecedentesFamiliares(),
            p.getAnamnesis(), p.isConsentimientoTutor(),
            p.getConsentimientoFecha() != null ? Timestamp.valueOf(p.getConsentimientoFecha()) : null,
            p.getId());

        return filas > 0;
    }

    private static PacienteDTO mapear(ResultSet rs, int rowNum) throws SQLException {
        PacienteDTO p = new PacienteDTO();
        p.setId(rs.getInt("id"));
        p.setNombre(rs.getString("nombre"));
        p.setApellido(rs.getString("apellido"));
        p.setCi(rs.getString("ci"));
        p.setTelefono(rs.getString("telefono"));
        p.setFechaNacimiento(rs.getDate("fecha_nacimiento") != null ? rs.getDate("fecha_nacimiento").toLocalDate() : null);
        p.setGenero(rs.getString("genero"));
        p.setNombreTutor(rs.getString("nombre_tutor"));
        p.setCiTutor(rs.getString("ci_tutor"));
        p.setDireccion(rs.getString("direccion"));
        p.setMotivoConsulta(rs.getString("motivo_consulta"));
        p.setCurso(rs.getString("curso"));
        p.setAntecedentesPersonales(rs.getString("antecedentes_personales"));
        p.setAntecedentesFamiliares(rs.getString("antecedentes_familiares"));
        p.setAnamnesis(rs.getString("anamnesis"));
        int psicologoId = rs.getInt("psicologo_id");
        p.setPsicologoId(rs.wasNull() ? null : psicologoId);
        p.setConsentimientoTutor(rs.getBoolean("consentimiento_tutor"));
        Timestamp consentimientoFecha = rs.getTimestamp("consentimiento_fecha");
        p.setConsentimientoFecha(consentimientoFecha != null ? consentimientoFecha.toLocalDateTime() : null);
        Timestamp creadoEn = rs.getTimestamp("creado_en");
        p.setCreadoEn(creadoEn != null ? creadoEn.toLocalDateTime() : null);
        return p;
    }
}
