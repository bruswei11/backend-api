-- Columna adicional para la estructura académica del estudiante (curso/sección/turno oficial
-- del colegio, ver src/util/EstructuraAcademica.java). Se guarda como una única etiqueta
-- canónica de texto (ej. "9° EEB - Sección B - Turno Mañana"); nullable y aditiva, no afecta
-- filas ni relaciones existentes.
ALTER TABLE pacientes
    ADD COLUMN curso VARCHAR(100) NULL AFTER motivo_consulta;
