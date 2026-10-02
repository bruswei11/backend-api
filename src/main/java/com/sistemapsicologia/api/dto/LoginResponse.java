package com.sistemapsicologia.api.dto;

public class LoginResponse {
    private String token;
    private int usuarioId;
    private String usuario;
    private String nombre;
    private String rol;
    /** Si puede crear cuentas nuevas (Configuración → Usuarios). */
    private boolean puedeCrearUsuarios;

    public LoginResponse(String token, int usuarioId, String usuario, String nombre, String rol, boolean puedeCrearUsuarios) {
        this.token = token;
        this.usuarioId = usuarioId;
        this.usuario = usuario;
        this.nombre = nombre;
        this.rol = rol;
        this.puedeCrearUsuarios = puedeCrearUsuarios;
    }

    public String getToken() { return token; }
    public int getUsuarioId() { return usuarioId; }
    public String getUsuario() { return usuario; }
    public String getNombre() { return nombre; }
    public String getRol() { return rol; }
    public boolean isPuedeCrearUsuarios() { return puedeCrearUsuarios; }
}
