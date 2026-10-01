-- Bloqueo de cuenta tras varios intentos fallidos de contraseña.
-- Se guarda en la base (no en memoria de la aplicación) porque distintos usuarios inician sesión
-- desde computadoras distintas conectadas a la misma base: el conteo tiene que ser compartido.
ALTER TABLE usuarios
    ADD COLUMN intentos_fallidos INT NOT NULL DEFAULT 0 AFTER activo,
    ADD COLUMN bloqueado_hasta TIMESTAMP NULL DEFAULT NULL AFTER intentos_fallidos;
