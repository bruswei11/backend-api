package com.sistemapsicologia.api.security;

/**
 * Identidad del usuario autenticado para el request actual (extraída del JWT), inyectable en
 * cualquier controller vía `Authentication.getPrincipal()`. Es el equivalente, por request, de lo
 * que `util.Sesion` es para todo el proceso en el escritorio.
 */
public class UsuarioAutenticado {
    private final int usuarioId;
    private final String usuario;
    private final String nombre;
    private final String rol;

    public UsuarioAutenticado(int usuarioId, String usuario, String nombre, String rol) {
        this.usuarioId = usuarioId;
        this.usuario = usuario;
        this.nombre = nombre;
        this.rol = rol;
    }

    public int getUsuarioId() { return usuarioId; }
    public String getUsuario() { return usuario; }
    public String getNombre() { return nombre; }
    public String getRol() { return rol; }

    public boolean esAdmin() { return "admin".equals(rol); }
    public boolean esPsicologo() { return "psicologo".equals(rol); }
    public boolean esSecretaria() { return "secretaria".equals(rol); }
}
