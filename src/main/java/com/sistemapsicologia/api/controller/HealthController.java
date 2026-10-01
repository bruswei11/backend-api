package com.sistemapsicologia.api.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de salud de la Etapa 0: confirma que el servidor arrancó y que puede consultar la
 * MISMA base MySQL que ya usa el escritorio (cuenta filas de `pacientes` como prueba concreta de
 * conectividad real, no solo "el proceso está vivo").
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("status", "ok");
        try {
            Integer totalPacientes = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pacientes", Integer.class);
            respuesta.put("db", "connected");
            respuesta.put("pacientes_en_base", totalPacientes);
        } catch (Exception e) {
            respuesta.put("db", "error");
            respuesta.put("detalle", e.getMessage());
        }
        return respuesta;
    }
}
