package com.sistemapsicologia.api.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

/**
 * Copia exacta del esquema de hash de contraseñas del escritorio
 * (SistemaPsicologia/src/util/PasswordUtil.java): SHA-256(saltHex + password), hex, comparación en
 * tiempo constante. Se porta tal cual para que las cuentas de `usuarios` ya existentes funcionen
 * en el celular sin ninguna migración de contraseñas.
 */
public class PasswordUtil {

    public static String hash(String password, String saltHex) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((saltHex + password).getBytes(StandardCharsets.UTF_8));
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean verificar(String password, String saltHex, String hashEsperado) {
        if (password == null || saltHex == null || hashEsperado == null) {
            return false;
        }
        String hashCalculado = hash(password, saltHex);
        return constantTimeEquals(hashCalculado, hashEsperado);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
