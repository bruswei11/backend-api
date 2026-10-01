package com.sistemapsicologia.api.dto;

public class LoginResponse {
    private String token;
    private int usuarioId;
    private String usuario;
    private String nombre;
    private String rol;

    public LoginResponse(String token, int usuarioId, String usuario, String nombre, String rol) {
        this.token = token;
        this.usuarioId = usuarioId;
        this.usuario = usuario;
        this.nombre = nombre;
        this.rol = rol;
    }

    public String getToken() { return token; }
    public int getUsuarioId() { return usuarioId; }
    public String getUsuario() { return usuario; }
    public String getNombre() { return nombre; }
    public String getRol() { return rol; }
}
