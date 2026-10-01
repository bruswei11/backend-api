package com.sistemapsicologia.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Un solo round-trip para todo el Panel. Los campos de "seguimientos vencidos" quedan null (y por
 * lo tanto AUSENTES del JSON, no un array vacío ni un 0) para secretaria -- ni el número ni la
 * lista se calculan siquiera para ese rol, igual que ya se decidió para el Dashboard del
 * escritorio (es señal clínica identificable, no un simple conteo administrativo).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardDTO {
    public int totalEstudiantes;
    public int totalAtenciones;
    public int totalCitas;
    public int totalProfesionales;
    public int casosPendientes;
    public Map<String, Integer> nuevosPorMes;
    public Map<String, Integer> porCurso;
    public Map<String, Integer> porMotivo;
    public List<TurnoDTO> proximasCitas;
    public Integer seguimientosVencidosTotal;
    public List<CasoPendienteDTO> seguimientosVencidosLista;

    public record CasoPendienteDTO(int pacienteId, String estudiante, String curso, String ultimoSeguimiento) {}
}
