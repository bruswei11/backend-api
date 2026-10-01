-- Consentimiento informado del padre/madre/tutor para el seguimiento psicológico del estudiante
-- (dato ausente hasta ahora; ver justificación: Ley 1680/01 Código de la Niñez y la Adolescencia,
-- Código de Ética del psicólogo, y la nueva Ley 7593/2025 de protección de datos personales que
-- clasifica la salud como dato sensible y agrava sanciones cuando el titular es menor de edad).
ALTER TABLE pacientes
    ADD COLUMN consentimiento_tutor TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN consentimiento_fecha TIMESTAMP NULL DEFAULT NULL;

-- Código CIE-10 opcional (estándar usado por el MSPBS para salud mental) y clasificación de
-- acoso escolar alineada a la Ley 4633/2012 + Resolución MEC 8353/2020, para la entrada de
-- Seguimiento del Estudiante.
ALTER TABLE historia_psicologica
    ADD COLUMN codigo_cie10 VARCHAR(10) NULL,
    ADD COLUMN tipo_acoso VARCHAR(20) NULL,
    ADD COLUMN es_reiterado TINYINT(1) NOT NULL DEFAULT 0;
