package com.sistemapsicologia.api.repository;

import com.sistemapsicologia.api.dto.PsicologoDTO;
import com.sistemapsicologia.api.dto.TurnoDTO;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Misma lógica que la agenda inline de Vista/Agenda.java del escritorio (esa pantalla no usa
 * TurnoDAO para el CRUD real, solo para contadores del Panel) -- acá se replica esa lógica real:
 * duración fija de 45 minutos, chequeo de solapamiento, marcado automático de citas vencidas como
 * "ausente".
 */
@Repository
public class TurnoRepository {

    public static final int DURACION_MINUTOS = 45;

    private final JdbcTemplate jdbcTemplate;

    public TurnoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Mismo efecto lateral que cargarTurnos() en el escritorio: pasa a "ausente" lo vencido antes de listar.
     * El inicio del día se pasa desde Java (hora local): MySQL corre en UTC y las fechas guardadas son
     * hora local de Paraguay, así que CURDATE()/NOW() marcaban mal las citas de la noche.
     */
    public void marcarVencidasComoAusente(Integer psicologoId) {
        String sql = "UPDATE turnos SET estado='ausente' WHERE estado='programado' AND fecha_hora < ?"
            + (psicologoId != null ? " AND psicologo_id = ?" : "");
        Timestamp hoy = Timestamp.valueOf(LocalDate.now().atStartOfDay());
        if (psicologoId != null) {
            jdbcTemplate.update(sql, hoy, psicologoId);
        } else {
            jdbcTemplate.update(sql, hoy);
        }
    }

    public List<TurnoDTO> listar(Integer psicologoId, LocalDate dia) {
        return listar(psicologoId, dia, null);
    }

    /** @param pacienteId opcional -- filtra a las citas de un estudiante puntual (selector "cita asociada" de Atenciones). */
    public List<TurnoDTO> listar(Integer psicologoId, LocalDate dia, Integer pacienteId) {
        StringBuilder sql = new StringBuilder(
            "SELECT t.id, t.paciente_id, CONCAT(p.nombre,' ',p.apellido) AS paciente_nombre, p.curso, " +
            "t.psicologo_id, u.nombre AS psicologo_nombre, t.fecha_hora, t.duracion_minutos, t.estado, t.notas, t.creado_en " +
            "FROM turnos t JOIN pacientes p ON p.id = t.paciente_id JOIN usuarios u ON u.id = t.psicologo_id WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (psicologoId != null) {
            sql.append("AND t.psicologo_id = ? ");
            params.add(psicologoId);
        }
        if (pacienteId != null) {
            sql.append("AND t.paciente_id = ? ");
            params.add(pacienteId);
        }
        if (dia != null) {
            sql.append("AND t.fecha_hora >= ? AND t.fecha_hora < ? ");
            params.add(Timestamp.valueOf(dia.atStartOfDay()));
            params.add(Timestamp.valueOf(dia.plusDays(1).atStartOfDay()));
        }
        sql.append("ORDER BY t.fecha_hora ASC");
        return jdbcTemplate.query(sql.toString(), TurnoRepository::mapear, params.toArray());
    }

    public Optional<TurnoDTO> obtenerPorId(int id) {
        String sql = "SELECT t.id, t.paciente_id, CONCAT(p.nombre,' ',p.apellido) AS paciente_nombre, p.curso, " +
            "t.psicologo_id, u.nombre AS psicologo_nombre, t.fecha_hora, t.duracion_minutos, t.estado, t.notas, t.creado_en " +
            "FROM turnos t JOIN pacientes p ON p.id = t.paciente_id JOIN usuarios u ON u.id = t.psicologo_id WHERE t.id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, TurnoRepository::mapear, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /** true si ya hay otra cita de ese psicólogo que se superpone con el horario dado. */
    public boolean haySolapamiento(int psicologoId, Timestamp inicio, int duracionMinutos, Integer excluirId) {
        String sql = "SELECT fecha_hora, duracion_minutos FROM turnos WHERE psicologo_id = ? AND estado <> 'cancelado' "
            + (excluirId != null ? "AND id <> ? " : "")
            + "AND fecha_hora < ? AND fecha_hora > ?";
        LocalDateTime desde = inicio.toLocalDateTime();
        LocalDateTime hasta = desde.plusMinutes(duracionMinutos);

        List<Object> params = new ArrayList<>();
        params.add(psicologoId);
        if (excluirId != null) {
            params.add(excluirId);
        }
        params.add(Timestamp.valueOf(hasta));
        params.add(Timestamp.valueOf(desde.minusHours(24)));

        List<Boolean> cruces = jdbcTemplate.query(sql, (rs, n) -> {
            LocalDateTime otroInicio = rs.getTimestamp("fecha_hora").toLocalDateTime();
            return otroInicio.plusMinutes(rs.getInt("duracion_minutos")).isAfter(desde);
        }, params.toArray());
        return cruces.contains(Boolean.TRUE);
    }

    public int crear(int pacienteId, int psicologoId, LocalDateTime fechaHora, String estado, String notas) {
        String sql = "INSERT INTO turnos (paciente_id, psicologo_id, fecha_hora, duracion_minutos, estado, notas, creado_en) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";
        LocalDateTime ahora = LocalDateTime.now();

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setInt(1, pacienteId);
            ps.setInt(2, psicologoId);
            ps.setTimestamp(3, Timestamp.valueOf(fechaHora));
            ps.setInt(4, DURACION_MINUTOS);
            ps.setString(5, estado);
            if (notas == null || notas.isBlank()) {
                ps.setNull(6, java.sql.Types.VARCHAR);
            } else {
                ps.setString(6, notas);
            }
            ps.setTimestamp(7, Timestamp.valueOf(ahora));
            return ps;
        }, keyHolder);

        return keyHolder.getKey().intValue();
    }

    public boolean actualizar(int id, int pacienteId, int psicologoId, LocalDateTime fechaHora, String estado, String notas) {
        String sql = "UPDATE turnos SET paciente_id=?, psicologo_id=?, fecha_hora=?, duracion_minutos=?, estado=?, notas=? WHERE id=?";
        int filas = jdbcTemplate.update(sql, pacienteId, psicologoId, Timestamp.valueOf(fechaHora),
            DURACION_MINUTOS, estado, (notas == null || notas.isBlank()) ? null : notas, id);
        return filas > 0;
    }

    public void eliminar(int id) {
        jdbcTemplate.update("DELETE FROM turnos WHERE id = ?", id);
    }

    /**
     * Mismo comportamiento que guardarSesionAtencion() en el escritorio: si la nota queda ligada
     * a una cita que seguía "programada", se asume que la atención se realizó y pasa a
     * "completada" (no pisa cancelada/ausente). @return true si efectivamente la cambió.
     */
    public boolean completarSiProgramado(int turnoId) {
        int filas = jdbcTemplate.update(
            "UPDATE turnos SET estado='completado' WHERE id=? AND estado='programado'", turnoId);
        return filas > 0;
    }

    public int contarProgramadosVencidos(Integer psicologoId) {
        String sql = "SELECT COUNT(*) AS total FROM turnos WHERE estado = 'programado' AND fecha_hora < ?"
            + (psicologoId != null ? " AND psicologo_id = ?" : "");
        Timestamp ahora = Timestamp.valueOf(LocalDateTime.now());
        Integer total = psicologoId != null
            ? jdbcTemplate.queryForObject(sql, Integer.class, ahora, psicologoId)
            : jdbcTemplate.queryForObject(sql, Integer.class, ahora);
        return total != null ? total : 0;
    }

    public int contarConfirmadosDesde(java.time.LocalDate desde, Integer psicologoId) {
        String sql = "SELECT COUNT(*) FROM turnos WHERE estado = 'programado' AND fecha_hora >= ?"
            + (psicologoId != null ? " AND psicologo_id = ?" : "");
        Timestamp inicio = Timestamp.valueOf(desde.atStartOfDay());
        Integer total = psicologoId != null
            ? jdbcTemplate.queryForObject(sql, Integer.class, inicio, psicologoId)
            : jdbcTemplate.queryForObject(sql, Integer.class, inicio);
        return total != null ? total : 0;
    }

    public List<TurnoDTO> obtenerProximas(int limite, Integer psicologoId) {
        StringBuilder sql = new StringBuilder(
            "SELECT t.id, t.paciente_id, CONCAT(p.nombre,' ',p.apellido) AS paciente_nombre, p.curso, " +
            "t.psicologo_id, u.nombre AS psicologo_nombre, t.fecha_hora, t.duracion_minutos, t.estado, t.notas, t.creado_en " +
            "FROM turnos t JOIN pacientes p ON p.id = t.paciente_id JOIN usuarios u ON u.id = t.psicologo_id " +
            "WHERE t.estado = 'programado' AND t.fecha_hora >= ? ");
        List<Object> params = new java.util.ArrayList<>();
        params.add(Timestamp.valueOf(LocalDateTime.now()));
        if (psicologoId != null) {
            sql.append("AND t.psicologo_id = ? ");
            params.add(psicologoId);
        }
        sql.append("ORDER BY t.fecha_hora ASC LIMIT ?");
        params.add(limite);
        return jdbcTemplate.query(sql.toString(), TurnoRepository::mapear, params.toArray());
    }

    public List<PsicologoDTO> listarPsicologos() {
        String sql = "SELECT id, nombre FROM usuarios WHERE rol='psicologo' AND activo = TRUE ORDER BY nombre";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new PsicologoDTO(rs.getInt("id"), rs.getString("nombre")));
    }

    private static TurnoDTO mapear(ResultSet rs, int rowNum) throws SQLException {
        TurnoDTO t = new TurnoDTO();
        t.setId(rs.getInt("id"));
        t.setPacienteId(rs.getInt("paciente_id"));
        t.setPacienteNombre(rs.getString("paciente_nombre"));
        t.setCurso(rs.getString("curso"));
        t.setPsicologoId(rs.getInt("psicologo_id"));
        t.setPsicologoNombre(rs.getString("psicologo_nombre"));
        Timestamp fechaHora = rs.getTimestamp("fecha_hora");
        t.setFechaHora(fechaHora != null ? fechaHora.toLocalDateTime() : null);
        t.setDuracionMinutos(rs.getInt("duracion_minutos"));
        t.setEstado(rs.getString("estado"));
        t.setNotas(rs.getString("notas"));
        Timestamp creadoEn = rs.getTimestamp("creado_en");
        t.setCreadoEn(creadoEn != null ? creadoEn.toLocalDateTime() : null);
        return t;
    }
}
