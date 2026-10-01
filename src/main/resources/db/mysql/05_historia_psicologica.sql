-- Historia clínica ampliada por paciente (usada por HistoriaPsicologicaDAO / HistoriaPsicologicaView).
-- Antes vivía en 02_crear_usuarios_y_tablas.sql junto con definiciones de usuarios/turnos/sesiones
-- incompatibles con el esquema real (ver 01_schema.sql, 02_pacientes.sql, 04_fase2_...); se separó
-- para no arriesgar que esas tablas ganen la carrera de CREATE TABLE IF NOT EXISTS en una base nueva.
CREATE TABLE IF NOT EXISTS historia_psicologica (
    id INT AUTO_INCREMENT PRIMARY KEY,
    paciente_id INT NOT NULL,
    psicologo_id INT,
    antecedentes LONGTEXT,
    motivo_consulta TEXT,
    observaciones_generales LONGTEXT,
    diagnostico TEXT,
    tratamiento TEXT,
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ultima_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (paciente_id) REFERENCES pacientes(id) ON DELETE CASCADE,
    FOREIGN KEY (psicologo_id) REFERENCES usuarios(id),
    INDEX idx_paciente (paciente_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
