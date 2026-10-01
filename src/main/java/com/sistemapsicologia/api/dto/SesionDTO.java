package com.sistemapsicologia.api.dto;

import java.time.LocalDateTime;

/**
 * Una atención (nota SOAP), tabla `sesiones`. Ojo: en el escritorio la lógica real de esto vive
 * como SQL inline en Vista/DetallePaciente.java, no en DAO.SesionDAO (que tiene varios campos
 * rotos/sin usar) -- este DTO se armó contra la tabla real y esa lógica real.
 */
public class SesionDTO {
    private Integer id;
    private int pacienteId;
    private int psicologoId;
    private String psicologoNombre;
    private Integer turnoId;
    private String subjetivo;
    private String objetivo;
    private String analisis;
    private String plan;
    private String notasPrivadas;
    private Integer duracionMinutos;
    private Integer duracionSegundos;
    private LocalDateTime fecha;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public int getPacienteId() { return pacienteId; }
    public void setPacienteId(int pacienteId) { this.pacienteId = pacienteId; }

    public int getPsicologoId() { return psicologoId; }
    public void setPsicologoId(int psicologoId) { this.psicologoId = psicologoId; }

    public String getPsicologoNombre() { return psicologoNombre; }
    public void setPsicologoNombre(String psicologoNombre) { this.psicologoNombre = psicologoNombre; }

    public Integer getTurnoId() { return turnoId; }
    public void setTurnoId(Integer turnoId) { this.turnoId = turnoId; }

    public String getSubjetivo() { return subjetivo; }
    public void setSubjetivo(String subjetivo) { this.subjetivo = subjetivo; }

    public String getObjetivo() { return objetivo; }
    public void setObjetivo(String objetivo) { this.objetivo = objetivo; }

    public String getAnalisis() { return analisis; }
    public void setAnalisis(String analisis) { this.analisis = analisis; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public String getNotasPrivadas() { return notasPrivadas; }
    public void setNotasPrivadas(String notasPrivadas) { this.notasPrivadas = notasPrivadas; }

    public Integer getDuracionMinutos() { return duracionMinutos; }
    public void setDuracionMinutos(Integer duracionMinutos) { this.duracionMinutos = duracionMinutos; }

    public Integer getDuracionSegundos() { return duracionSegundos; }
    public void setDuracionSegundos(Integer duracionSegundos) { this.duracionSegundos = duracionSegundos; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
