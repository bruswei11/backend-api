package com.sistemapsicologia.api.dto;

import java.time.LocalDateTime;

public class DocumentoDTO {
    private Integer id;
    private int pacienteId;
    private String nombreArchivo;
    private String rutaArchivo;
    private String tipo;
    private Integer subidoPor;
    private String subidoPorNombre;
    private LocalDateTime subidoEn;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public int getPacienteId() { return pacienteId; }
    public void setPacienteId(int pacienteId) { this.pacienteId = pacienteId; }

    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }

    public String getRutaArchivo() { return rutaArchivo; }
    public void setRutaArchivo(String rutaArchivo) { this.rutaArchivo = rutaArchivo; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Integer getSubidoPor() { return subidoPor; }
    public void setSubidoPor(Integer subidoPor) { this.subidoPor = subidoPor; }

    public String getSubidoPorNombre() { return subidoPorNombre; }
    public void setSubidoPorNombre(String subidoPorNombre) { this.subidoPorNombre = subidoPorNombre; }

    public LocalDateTime getSubidoEn() { return subidoEn; }
    public void setSubidoEn(LocalDateTime subidoEn) { this.subidoEn = subidoEn; }
}
