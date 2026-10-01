package com.sistemapsicologia.api.dto;

import java.time.LocalDateTime;

/**
 * DTO de una cita. Incluye nombre del estudiante/psicólogo ya resueltos (denormalizado en la
 * consulta con JOIN) para que la app móvil no tenga que hacer una consulta aparte por cada fila
 * de una lista, igual que ya muestra la tabla de Agenda del escritorio.
 */
public class TurnoDTO {
    private Integer id;
    private int pacienteId;
    private String pacienteNombre;
    private String curso;
    private int psicologoId;
    private String psicologoNombre;
    private LocalDateTime fechaHora;
    private int duracionMinutos;
    private String estado;
    private String notas;
    private LocalDateTime creadoEn;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public int getPacienteId() { return pacienteId; }
    public void setPacienteId(int pacienteId) { this.pacienteId = pacienteId; }

    public String getPacienteNombre() { return pacienteNombre; }
    public void setPacienteNombre(String pacienteNombre) { this.pacienteNombre = pacienteNombre; }

    public String getCurso() { return curso; }
    public void setCurso(String curso) { this.curso = curso; }

    public int getPsicologoId() { return psicologoId; }
    public void setPsicologoId(int psicologoId) { this.psicologoId = psicologoId; }

    public String getPsicologoNombre() { return psicologoNombre; }
    public void setPsicologoNombre(String psicologoNombre) { this.psicologoNombre = psicologoNombre; }

    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }

    public int getDuracionMinutos() { return duracionMinutos; }
    public void setDuracionMinutos(int duracionMinutos) { this.duracionMinutos = duracionMinutos; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
}
