ALTER TABLE usuarios
    ADD COLUMN rol VARCHAR(20) NOT NULL DEFAULT 'psicologo' AFTER nombre;

UPDATE usuarios SET rol = 'admin' WHERE usuario = 'admin';

ALTER TABLE pacientes
    ADD COLUMN psicologo_id INT NULL AFTER motivo_consulta,
    ADD COLUMN antecedentes_personales TEXT NULL AFTER psicologo_id,
    ADD COLUMN antecedentes_familiares TEXT NULL AFTER antecedentes_personales,
    ADD COLUMN anamnesis TEXT NULL AFTER antecedentes_familiares,
    ADD CONSTRAINT fk_pacientes_psicologo FOREIGN KEY (psicologo_id) REFERENCES usuarios(id);

CREATE TABLE IF NOT EXISTS auditoria (
    id INT AUTO_INCREMENT PRIMARY KEY,
    usuario_id INT NULL,
    usuario_nombre VARCHAR(100) NOT NULL,
    accion VARCHAR(50) NOT NULL,
    entidad VARCHAR(50) NOT NULL,
    entidad_id INT NULL,
    detalle VARCHAR(255) NULL,
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);
