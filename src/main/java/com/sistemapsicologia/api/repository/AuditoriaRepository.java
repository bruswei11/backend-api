package com.sistemapsicologia.api.repository;

import com.sistemapsicologia.api.dto.AuditoriaDTO;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Mismo esquema que util.Auditoria.registrar() del escritorio -- misma tabla `auditoria`, mismos códigos de acción. */
@Repository
public class AuditoriaRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuditoriaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void registrar(Integer usuarioId, String usuarioNombre, String accion, String entidad, Integer entidadId, String detalle) {
        String sql = "INSERT INTO auditoria (usuario_id, usuario_nombre, accion, entidad, entidad_id, detalle, fecha) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, usuarioId, usuarioNombre, accion, entidad, entidadId, detalle,
            Timestamp.valueOf(LocalDateTime.now()));
    }

    /** Exportaciones (EXPORTAR_*) de un usuario, o de todos si usuarioId es null. Más recientes primero. */
    public List<AuditoriaDTO> exportaciones(Integer usuarioId, int limite) {
        String sql = "SELECT * FROM auditoria WHERE accion LIKE 'EXPORTAR%' "
            + (usuarioId != null ? "AND usuario_id = ? " : "") + "ORDER BY fecha DESC LIMIT ?";
        Object[] params = usuarioId != null ? new Object[]{usuarioId, limite} : new Object[]{limite};
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            int entidadIdRaw = rs.getInt("entidad_id");
            return new AuditoriaDTO(rs.getInt("id"), rs.getString("usuario_nombre"), rs.getString("accion"),
                rs.getString("entidad"), rs.wasNull() ? null : entidadIdRaw, rs.getString("detalle"),
                rs.getTimestamp("fecha").toLocalDateTime());
        }, params);
    }

    /** Mismo filtro de texto/fecha que la pestaña "Actividad reciente" de Configuracion.java del escritorio. */
    public List<AuditoriaDTO> buscar(String texto, LocalDateTime desde, LocalDateTime hasta, int limite) {
        StringBuilder sql = new StringBuilder("SELECT * FROM auditoria WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (texto != null && !texto.isBlank()) {
            sql.append("AND (LOWER(usuario_nombre) LIKE ? OR LOWER(accion) LIKE ? OR LOWER(entidad) LIKE ? "
                + "OR LOWER(detalle) LIKE ?) ");
            String like = "%" + texto.toLowerCase(java.util.Locale.ROOT) + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (desde != null) {
            sql.append("AND fecha >= ? ");
            params.add(Timestamp.valueOf(desde));
        }
        if (hasta != null) {
            sql.append("AND fecha <= ? ");
            params.add(Timestamp.valueOf(hasta));
        }
        sql.append("ORDER BY fecha DESC LIMIT ?");
        params.add(limite);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            int entidadIdRaw = rs.getInt("entidad_id");
            Integer entidadId = rs.wasNull() ? null : entidadIdRaw;
            return new AuditoriaDTO(
                rs.getInt("id"),
                rs.getString("usuario_nombre"),
                rs.getString("accion"),
                rs.getString("entidad"),
                entidadId,
                rs.getString("detalle"),
                rs.getTimestamp("fecha").toLocalDateTime()
            );
        }, params.toArray());
    }
}
