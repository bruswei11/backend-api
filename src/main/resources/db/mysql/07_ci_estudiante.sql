-- El formulario "Nuevo Estudiante" reemplaza el campo Email (innecesario para este contexto)
-- por CI (cédula de identidad / número de identificación).
ALTER TABLE pacientes CHANGE COLUMN email ci VARCHAR(20) NULL;
