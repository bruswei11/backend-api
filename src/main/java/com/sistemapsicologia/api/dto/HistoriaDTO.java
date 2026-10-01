package com.sistemapsicologia.api.dto;

import java.time.LocalDateTime;

/**
 * Una entrada de Seguimiento del Estudiante (tabla historia_psicologica -- una fila por atención
 * registrada, no una por estudiante). Solo lectura + creación en esta API: una vez guardada, no
 * hay endpoint de edición (ver HistoriaController), reforzando del lado del servidor lo que en el
 * escritorio hoy es solo una restricción de interfaz.
 */
public class HistoriaDTO {
    private Integer id;
    private int pacienteId;
    private int psicologoId;
    private String psicologoNombre;
    private String antecedentes;
    private String motivoConsulta;
    private String observacionesGenerales;
    private String diagnostico;
    private String tratamiento;
    private String codigoCie10;
    private String tipoAcoso;
    private boolean esReiterado;
    private LocalDateTime fechaCreacion;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public int getPacienteId() { return pacienteId; }
    public void setPacienteId(int pacienteId) { this.pacienteId = pacienteId; }

    public int getPsicologoId() { return psicologoId; }
    public void setPsicologoId(int psicologoId) { this.psicologoId = psicologoId; }

    public String getPsicologoNombre() { return psicologoNombre; }
    public void setPsicologoNombre(String psicologoNombre) { this.psicologoNombre = psicologoNombre; }

    public String getAntecedentes() { return antecedentes; }
    public void setAntecedentes(String antecedentes) { this.antecedentes = antecedentes; }

    public String getMotivoConsulta() { return motivoConsulta; }
    public void setMotivoConsulta(String motivoConsulta) { this.motivoConsulta = motivoConsulta; }

    public String getObservacionesGenerales() { return observacionesGenerales; }
    public void setObservacionesGenerales(String observacionesGenerales) { this.observacionesGenerales = observacionesGenerales; }

    public String getDiagnostico() { return diagnostico; }
    public void setDiagnostico(String diagnostico) { this.diagnostico = diagnostico; }

    public String getTratamiento() { return tratamiento; }
    public void setTratamiento(String tratamiento) { this.tratamiento = tratamiento; }

    public String getCodigoCie10() { return codigoCie10; }
    public void setCodigoCie10(String codigoCie10) { this.codigoCie10 = codigoCie10; }

    public String getTipoAcoso() { return tipoAcoso; }
    public void setTipoAcoso(String tipoAcoso) { this.tipoAcoso = tipoAcoso; }

    public boolean isEsReiterado() { return esReiterado; }
    public void setEsReiterado(boolean esReiterado) { this.esReiterado = esReiterado; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}
