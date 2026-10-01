-- El formulario "Nuevo Estudiante" ahora captura también los datos del padre/madre o tutor
-- responsable del estudiante.
ALTER TABLE pacientes
    ADD COLUMN nombre_tutor VARCHAR(150) NULL AFTER genero,
    ADD COLUMN ci_tutor VARCHAR(20) NULL AFTER nombre_tutor;
