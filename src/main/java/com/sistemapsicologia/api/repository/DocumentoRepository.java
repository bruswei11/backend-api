package com.sistemapsicologia.api.repository;

import com.sistemapsicologia.api.dto.DocumentoDTO;
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

/** Mismas columnas que DAO.DocumentoPacienteDAO del escritorio (tabla `documentos_paciente`). */
@Repository
public class DocumentoRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DocumentoDTO> listarPorPaciente(int pacienteId) {
        String sql = "SELECT d.*, u.nombre AS subido_por_nombre FROM documentos_paciente d " +
            "LEFT JOIN usuarios u ON u.id = d.subido_por WHERE d.paciente_id = ? ORDER BY d.subido_en DESC";
        return jdbcTemplate.query(sql, DocumentoRepository::mapear, pacienteId);
    }

    public Optional<DocumentoDTO> obtenerPorId(int id) {
        String sql = "SELECT d.*, u.nombre AS subido_por_nombre FROM documentos_paciente d " +
            "LEFT JOIN usuarios u ON u.id = d.subido_por WHERE d.id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, DocumentoRepository::mapear, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public int crear(int pacienteId, String nombreArchivo, String rutaArchivo, String tipo, Integer subidoPor) {
        String sql = "INSERT INTO documentos_paciente (paciente_id, nombre_archivo, ruta_archivo, tipo, subido_por, subido_en) " +
            "VALUES (?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setInt(1, pacienteId);
            ps.setString(2, nombreArchivo);
            ps.setString(3, rutaArchivo);
            ps.setString(4, tipo);
            if (subidoPor != null) { ps.setInt(5, subidoPor); } else { ps.setNull(5, Types.INTEGER); }
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    /** Modo sin disco (ADJUNTOS_EN_BASE=true): el contenido del archivo va a documentos_contenido. */
    public void guardarContenido(int documentoId, byte[] contenido) {
        jdbcTemplate.update("INSERT INTO documentos_contenido (documento_id, contenido) VALUES (?, ?)", documentoId, contenido);
    }

    public Optional<byte[]> obtenerContenido(int documentoId) {
        List<byte[]> filas = jdbcTemplate.query("SELECT contenido FROM documentos_contenido WHERE documento_id = ?",
            (rs, n) -> rs.getBytes("contenido"), documentoId);
        return filas.isEmpty() ? Optional.empty() : Optional.of(filas.get(0));
    }

    public void eliminar(int id) {
        jdbcTemplate.update("DELETE FROM documentos_paciente WHERE id = ?", id);
    }

    private static DocumentoDTO mapear(ResultSet rs, int rowNum) throws SQLException {
        DocumentoDTO d = new DocumentoDTO();
        d.setId(rs.getInt("id"));
        d.setPacienteId(rs.getInt("paciente_id"));
        d.setNombreArchivo(rs.getString("nombre_archivo"));
        d.setRutaArchivo(rs.getString("ruta_archivo"));
        d.setTipo(rs.getString("tipo"));
        int subidoPor = rs.getInt("subido_por");
        d.setSubidoPor(rs.wasNull() ? null : subidoPor);
        d.setSubidoPorNombre(rs.getString("subido_por_nombre"));
        Timestamp subidoEn = rs.getTimestamp("subido_en");
        d.setSubidoEn(subidoEn != null ? subidoEn.toLocalDateTime() : null);
        return d;
    }
}
