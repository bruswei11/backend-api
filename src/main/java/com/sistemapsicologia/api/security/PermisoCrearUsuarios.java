package com.sistemapsicologia.api.security;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Quién puede crear cuentas nuevas: no hay registro libre, solo los usuarios de la lista
 * (variable USUARIOS_QUE_CREAN_CUENTAS, separados por coma; por defecto josechavez). El resto de los
 * permisos es igual para todos: cada profesional ve solo a sus estudiantes.
 */
@Component
public class PermisoCrearUsuarios {

    private final Set<String> usuarios;

    public PermisoCrearUsuarios(@Value("${app.usuarios.pueden-crear-cuentas:josechavez}") String lista) {
        this.usuarios = Arrays.stream(lista.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
    }

    public boolean permite(String usuario) {
        return usuario != null && usuarios.contains(usuario.toLowerCase());
    }
}
