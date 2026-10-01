package com.sistemapsicologia.api.controller;

import com.sistemapsicologia.api.dto.AuditoriaDTO;
import com.sistemapsicologia.api.repository.AuditoriaRepository;
import com.sistemapsicologia.api.security.UsuarioAutenticado;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Solo admin -- misma pestaña "Actividad reciente" de Configuracion.java del escritorio. */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaRepository auditoriaRepository;

    public AuditoriaController(AuditoriaRepository auditoriaRepository) {
        this.auditoriaRepository = auditoriaRepository;
    }

    @GetMapping
    public ResponseEntity<?> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(defaultValue = "200") int limite,
            Authentication auth) {
        UsuarioAutenticado u = (UsuarioAutenticado) auth.getPrincipal();
        if (!u.esAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Solo un administrador puede ver la actividad reciente"));
        }
        List<AuditoriaDTO> registros = auditoriaRepository.buscar(texto, desde, hasta, limite);
        return ResponseEntity.ok(registros);
    }
}
