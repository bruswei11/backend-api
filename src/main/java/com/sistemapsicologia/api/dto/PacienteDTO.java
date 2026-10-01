package com.sistemapsicologia.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO armado contra la tabla `pacientes` real (ver inventario de esquema), no contra
 * modelos.Paciente del escritorio -- ese modelo tiene un typo histórico
 * (`antecedenteFamiliares`, sin la "s") que acá se corrige ya que es un DTO nuevo sin código
 * existente atado al nombre viejo.
 */
public class PacienteDTO {
    private Integer id;
    private String nombre;
    private String apellido;
    private String ci;
    private String telefono;
    private LocalDate fechaNacimiento;
    private String genero;
    private String nombreTutor;
    private String ciTutor;
    private String direccion;
    private String motivoConsulta;
    private String curso;
    private String antecedentesPersonales;
    private String antecedentesFamiliares;
    private String anamnesis;
    private Integer psicologoId;
    private boolean consentimientoTutor;
    private LocalDateTime consentimientoFecha;
    private LocalDateTime creadoEn;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

    public String getCi() { return ci; }
    public void setCi(String ci) { this.ci = ci; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public void setFechaNacimiento(LocalDate fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; }

    public String getGenero() { return genero; }
    public void setGenero(String genero) { this.genero = genero; }

    public String getNombreTutor() { return nombreTutor; }
    public void setNombreTutor(String nombreTutor) { this.nombreTutor = nombreTutor; }

    public String getCiTutor() { return ciTutor; }
    public void setCiTutor(String ciTutor) { this.ciTutor = ciTutor; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getMotivoConsulta() { return motivoConsulta; }
    public void setMotivoConsulta(String motivoConsulta) { this.motivoConsulta = motivoConsulta; }

    public String getCurso() { return curso; }
    public void setCurso(String curso) { this.curso = curso; }

    public String getAntecedentesPersonales() { return antecedentesPersonales; }
    public void setAntecedentesPersonales(String antecedentesPersonales) { this.antecedentesPersonales = antecedentesPersonales; }

    public String getAntecedentesFamiliares() { return antecedentesFamiliares; }
    public void setAntecedentesFamiliares(String antecedentesFamiliares) { this.antecedentesFamiliares = antecedentesFamiliares; }

    public String getAnamnesis() { return anamnesis; }
    public void setAnamnesis(String anamnesis) { this.anamnesis = anamnesis; }

    public Integer getPsicologoId() { return psicologoId; }
    public void setPsicologoId(Integer psicologoId) { this.psicologoId = psicologoId; }

    public boolean isConsentimientoTutor() { return consentimientoTutor; }
    public void setConsentimientoTutor(boolean consentimientoTutor) { this.consentimientoTutor = consentimientoTutor; }

    public LocalDateTime getConsentimientoFecha() { return consentimientoFecha; }
    public void setConsentimientoFecha(LocalDateTime consentimientoFecha) { this.consentimientoFecha = consentimientoFecha; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
}
