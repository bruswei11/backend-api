package com.sistemapsicologia.api.repository;

import com.sistemapsicologia.api.dto.SesionDTO;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** Mismas columnas que la tabla `sesiones` real (ver Vista/DetallePaciente.java del escritorio). Sin actualizar a propósito. */
@Repository
public class SesionRepository {

    private final JdbcTemplate jdbcTemplate;

    public SesionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SesionDTO> listarPorPaciente(int pacienteId) {
        String sql = "SELECT s.*, u.nombre AS psicologo_nombre FROM sesiones s " +
            "JOIN usuarios u ON u.id = s.psicologo_id WHERE s.paciente_id = ? ORDER BY s.fecha DESC";
        return jdbcTemplate.query(sql, SesionRepository::mapear, pacienteId);
    }

    public Optional<SesionDTO> obtenerPorId(int id) {
        String sql = "SELECT s.*, u.nombre AS psicologo_nombre FROM sesiones s " +
            "JOIN usuarios u ON u.id = s.psicologo_id WHERE s.id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, SesionRepository::mapear, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public int crear(SesionDTO s) {
        String sql = "INSERT INTO sesiones (paciente_id, psicologo_id, subjetivo, objetivo, analisis, plan, " +
            "notas_privadas, duracion_minutos, duracion_segundos, turno_id, fecha) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setInt(1, s.getPacienteId());
            ps.setInt(2, s.getPsicologoId());
            setTextoONulo(ps, 3, s.getSubjetivo());
            setTextoONulo(ps, 4, s.getObjetivo());
            setTextoONulo(ps, 5, s.getAnalisis());
            setTextoONulo(ps, 6, s.getPlan());
            setTextoONulo(ps, 7, s.getNotasPrivadas());
            if (s.getDuracionMinutos() != null) { ps.setInt(8, s.getDuracionMinutos()); } else { ps.setNull(8, Types.INTEGER); }
            if (s.getDuracionSegundos() != null) { ps.setInt(9, s.getDuracionSegundos()); } else { ps.setNull(9, Types.INTEGER); }
            if (s.getTurnoId() != null) { ps.setInt(10, s.getTurnoId()); } else { ps.setNull(10, Types.INTEGER); }
            ps.setTimestamp(11, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);

        return keyHolder.getKey().intValue();
    }

    public int contarDesde(java.time.LocalDate desde, Integer psicologoId) {
        String sql = "SELECT COUNT(*) FROM sesiones s JOIN pacientes p ON p.id = s.paciente_id WHERE s.fecha >= ?"
            + (psicologoId != null ? " AND p.psicologo_id = ?" : "");
        Timestamp inicio = Timestamp.valueOf(desde.atStartOfDay());
        Integer total = psicologoId != null
            ? jdbcTemplate.queryForObject(sql, Integer.class, inicio, psicologoId)
            : jdbcTemplate.queryForObject(sql, Integer.class, inicio);
        return total != null ? total : 0;
    }

    private void setTextoONulo(PreparedStatement ps, int indice, String valor) throws SQLException {
        if (valor == null || valor.isEmpty()) {
            ps.setNull(indice, Types.VARCHAR);
        } else {
            ps.setString(indice, valor);
        }
    }

    private static SesionDTO mapear(ResultSet rs, int rowNum) throws SQLException {
        SesionDTO s = new SesionDTO();
        s.setId(rs.getInt("id"));
        s.setPacienteId(rs.getInt("paciente_id"));
        s.setPsicologoId(rs.getInt("psicologo_id"));
        s.setPsicologoNombre(rs.getString("psicologo_nombre"));
        int turnoId = rs.getInt("turno_id");
        s.setTurnoId(rs.wasNull() ? null : turnoId);
        s.setSubjetivo(rs.getString("subjetivo"));
        s.setObjetivo(rs.getString("objetivo"));
        s.setAnalisis(rs.getString("analisis"));
        s.setPlan(rs.getString("plan"));
        s.setNotasPrivadas(rs.getString("notas_privadas"));
        int duracionMinutos = rs.getInt("duracion_minutos");
        s.setDuracionMinutos(rs.wasNull() ? null : duracionMinutos);
        int duracionSegundos = rs.getInt("duracion_segundos");
        s.setDuracionSegundos(rs.wasNull() ? null : duracionSegundos);
        Timestamp fecha = rs.getTimestamp("fecha");
        s.setFecha(fecha != null ? fecha.toLocalDateTime() : null);
        return s;
    }
}
