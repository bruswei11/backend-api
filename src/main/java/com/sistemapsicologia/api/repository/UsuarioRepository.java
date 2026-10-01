package com.sistemapsicologia.api.repository;

import com.sistemapsicologia.api.dto.UsuarioDTO;
import com.sistemapsicologia.api.security.PasswordUtil;
import java.security.SecureRandom;
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

@Repository
public class UsuarioRepository {

    private static final SecureRandom RANDOM = new SecureRandom();

    public record UsuarioAuth(int id, String usuario, String nombre, String rol, String salt, String passwordHash) {}

    private final JdbcTemplate jdbcTemplate;

    public UsuarioRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<UsuarioDTO> obtenerTodos() {
        return jdbcTemplate.query("SELECT id, usuario, nombre, rol, activo FROM usuarios ORDER BY nombre",
            UsuarioRepository::mapear);
    }

    public Optional<UsuarioDTO> obtenerPorId(int id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT id, usuario, nombre, rol, activo FROM usuarios WHERE id = ?", UsuarioRepository::mapear, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean existeUsuario(String usuario) {
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM usuarios WHERE usuario = ?", Integer.class, usuario);
        return total != null && total > 0;
    }

    public int crear(String usuario, String nombre, String passwordPlano, String rol) {
        String salt = generarSaltHex();
        String hash = PasswordUtil.hash(passwordPlano, salt);
        String sql = "INSERT INTO usuarios (usuario, password_hash, salt, nombre, rol, activo, creado_en) VALUES (?, ?, ?, ?, ?, TRUE, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, usuario);
            ps.setString(2, hash);
            ps.setString(3, salt);
            ps.setString(4, nombre);
            ps.setString(5, rol);
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    public void cambiarPassword(int usuarioId, String passwordPlano) {
        String salt = generarSaltHex();
        String hash = PasswordUtil.hash(passwordPlano, salt);
        jdbcTemplate.update("UPDATE usuarios SET password_hash=?, salt=? WHERE id=?", hash, salt, usuarioId);
    }

    public boolean verificarPassword(int usuarioId, String passwordPlano) {
        try {
            var fila = jdbcTemplate.queryForObject("SELECT salt, password_hash FROM usuarios WHERE id=?",
                (rs, rowNum) -> new String[]{rs.getString("salt"), rs.getString("password_hash")}, usuarioId);
            return fila != null && PasswordUtil.verificar(passwordPlano, fila[0], fila[1]);
        } catch (EmptyResultDataAccessException e) {
            return false;
        }
    }

    public void actualizarActivo(int usuarioId, boolean activo) {
        jdbcTemplate.update("UPDATE usuarios SET activo=? WHERE id=?", activo, usuarioId);
    }

    public void actualizarNombre(int usuarioId, String nombre) {
        jdbcTemplate.update("UPDATE usuarios SET nombre=? WHERE id=?", nombre, usuarioId);
    }

    public void actualizarRol(int usuarioId, String rol) {
        jdbcTemplate.update("UPDATE usuarios SET rol=? WHERE id=?", rol, usuarioId);
    }

    public boolean eliminar(int usuarioId) {
        return jdbcTemplate.update("DELETE FROM usuarios WHERE id=?", usuarioId) > 0;
    }

    private static String generarSaltHex() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        StringBuilder sb = new StringBuilder();
        for (byte b : salt) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static UsuarioDTO mapear(ResultSet rs, int rowNum) throws SQLException {
        UsuarioDTO u = new UsuarioDTO();
        u.setId(rs.getInt("id"));
        u.setUsuario(rs.getString("usuario"));
        u.setNombre(rs.getString("nombre"));
        u.setRol(rs.getString("rol"));
        u.setActivo(rs.getBoolean("activo"));
        return u;
    }

    public int contarActivosPorRol(String rol) {
        Integer total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM usuarios WHERE rol = ? AND activo = TRUE", Integer.class, rol);
        return total != null ? total : 0;
    }

    /** Fila completa para el login (incluye inactivas y el estado de bloqueo por intentos fallidos). */
    public record UsuarioLogin(int id, String usuario, String nombre, String rol, String salt, String passwordHash,
            boolean activo, int intentosFallidos, LocalDateTime bloqueadoHasta) {}

    public Optional<UsuarioLogin> buscarParaLogin(String usuario) {
        String sql = "SELECT id, usuario, nombre, rol, salt, password_hash, activo, intentos_fallidos, bloqueado_hasta "
            + "FROM usuarios WHERE usuario = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Timestamp hasta = rs.getTimestamp("bloqueado_hasta");
                return new UsuarioLogin(rs.getInt("id"), rs.getString("usuario"), rs.getString("nombre"),
                    rs.getString("rol"), rs.getString("salt"), rs.getString("password_hash"), rs.getBoolean("activo"),
                    rs.getInt("intentos_fallidos"), hasta != null ? hasta.toLocalDateTime() : null);
            }, usuario));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void registrarIntentos(int usuarioId, int intentos, LocalDateTime bloqueadoHasta) {
        jdbcTemplate.update("UPDATE usuarios SET intentos_fallidos = ?, bloqueado_hasta = ? WHERE id = ?",
            intentos, bloqueadoHasta != null ? Timestamp.valueOf(bloqueadoHasta) : null, usuarioId);
    }

    /** Mismo criterio que UsuarioDAO.autenticar() del escritorio: solo cuentas activas. */
    public Optional<UsuarioAuth> buscarActivoPorUsuario(String usuario) {
        String sql = "SELECT id, usuario, nombre, rol, salt, password_hash FROM usuarios WHERE usuario = ? AND activo = TRUE";
        try {
            UsuarioAuth resultado = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new UsuarioAuth(
                rs.getInt("id"),
                rs.getString("usuario"),
                rs.getString("nombre"),
                rs.getString("rol"),
                rs.getString("salt"),
                rs.getString("password_hash")
            ), usuario);
            return Optional.ofNullable(resultado);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
