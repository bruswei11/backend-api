package com.sistemapsicologia.api.dto;

import java.time.LocalDateTime;

public record AuditoriaDTO(int id, String usuarioNombre, String accion, String entidad, Integer entidadId,
        String detalle, LocalDateTime fecha) {}
