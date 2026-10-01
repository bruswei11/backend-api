-- Esquema para PostgreSQL (Supabase): equivalente exacto al de MySQL después de aplicar
-- SistemaPsicologia/db-init/01..11. Las fechas son TIMESTAMP sin zona horaria porque el sistema
-- guarda la hora local de Paraguay que manda Java (misma regla que en MySQL).

CREATE TABLE IF NOT EXISTS usuarios (
    id SERIAL PRIMARY KEY,
    usuario VARCHAR(50) NOT NULL UNIQUE,
    password_hash CHAR(64) NOT NULL,
    salt CHAR(32) NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    rol VARCHAR(20) NOT NULL DEFAULT 'psicologo',
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    intentos_fallidos INT NOT NULL DEFAULT 0,
    bloqueado_hasta TIMESTAMP NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pacientes (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    fecha_nacimiento DATE NULL,
    genero VARCHAR(20) NULL,
    nombre_tutor VARCHAR(150) NULL,
    ci_tutor VARCHAR(20) NULL,
    telefono VARCHAR(20) NULL,
    ci VARCHAR(20) NULL,
    direccion VARCHAR(200) NULL,
    motivo_consulta VARCHAR(255) NULL,
    curso VARCHAR(100) NULL,
    psicologo_id INT NULL REFERENCES usuarios(id),
    antecedentes_personales TEXT NULL,
    antecedentes_familiares TEXT NULL,
    anamnesis TEXT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    consentimiento_tutor BOOLEAN NOT NULL DEFAULT FALSE,
    consentimiento_fecha TIMESTAMP NULL
);
CREATE INDEX IF NOT EXISTS idx_pacientes_psicologo ON pacientes(psicologo_id);

CREATE TABLE IF NOT EXISTS auditoria (
    id SERIAL PRIMARY KEY,
    usuario_id INT NULL REFERENCES usuarios(id),
    usuario_nombre VARCHAR(100) NOT NULL,
    accion VARCHAR(50) NOT NULL,
    entidad VARCHAR(50) NOT NULL,
    entidad_id INT NULL,
    detalle VARCHAR(255) NULL,
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_auditoria_fecha ON auditoria(fecha);

CREATE TABLE IF NOT EXISTS turnos (
    id SERIAL PRIMARY KEY,
    paciente_id INT NOT NULL REFERENCES pacientes(id),
    psicologo_id INT NOT NULL REFERENCES usuarios(id),
    fecha_hora TIMESTAMP NOT NULL,
    duracion_minutos INT NOT NULL DEFAULT 45,
    estado VARCHAR(20) NOT NULL DEFAULT 'programado',
    notas VARCHAR(255) NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_turnos_paciente ON turnos(paciente_id);
CREATE INDEX IF NOT EXISTS idx_turnos_psicologo_fecha ON turnos(psicologo_id, fecha_hora);

CREATE TABLE IF NOT EXISTS sesiones (
    id SERIAL PRIMARY KEY,
    paciente_id INT NOT NULL REFERENCES pacientes(id),
    psicologo_id INT NOT NULL REFERENCES usuarios(id),
    turno_id INT NULL REFERENCES turnos(id),
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    subjetivo TEXT NULL,
    objetivo TEXT NULL,
    analisis TEXT NULL,
    plan TEXT NULL,
    notas_privadas TEXT NULL,
    duracion_minutos INT NULL,
    duracion_segundos SMALLINT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sesiones_paciente ON sesiones(paciente_id);

CREATE TABLE IF NOT EXISTS documentos_paciente (
    id SERIAL PRIMARY KEY,
    paciente_id INT NOT NULL REFERENCES pacientes(id),
    nombre_archivo VARCHAR(255) NOT NULL,
    ruta_archivo VARCHAR(500) NOT NULL,
    tipo VARCHAR(50) NULL,
    subido_por INT NULL REFERENCES usuarios(id),
    subido_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_documentos_paciente ON documentos_paciente(paciente_id);

-- Contenido de los adjuntos cuando el servidor no tiene disco permanente (Render gratis):
-- ADJUNTOS_EN_BASE=true guarda los archivos acá en vez de en una carpeta.
CREATE TABLE IF NOT EXISTS documentos_contenido (
    documento_id INT PRIMARY KEY REFERENCES documentos_paciente(id) ON DELETE CASCADE,
    contenido BYTEA NOT NULL
);

CREATE TABLE IF NOT EXISTS historia_psicologica (
    id SERIAL PRIMARY KEY,
    paciente_id INT NOT NULL REFERENCES pacientes(id) ON DELETE CASCADE,
    psicologo_id INT NULL,
    antecedentes TEXT NULL,
    motivo_consulta TEXT NULL,
    observaciones_generales TEXT NULL,
    diagnostico TEXT NULL,
    tratamiento TEXT NULL,
    fecha_creacion TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    ultima_actualizacion TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    codigo_cie10 VARCHAR(10) NULL,
    tipo_acoso VARCHAR(20) NULL,
    es_reiterado BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_historia_paciente ON historia_psicologica(paciente_id);

-- Supabase publica automáticamente las tablas del esquema "public" en su API REST, accesible con
-- la clave pública del proyecto. Con RLS activado y SIN políticas, esa API no puede leer ni
-- escribir nada; el servidor del sistema se conecta como dueño de las tablas y no se ve afectado.
ALTER TABLE usuarios ENABLE ROW LEVEL SECURITY;
ALTER TABLE pacientes ENABLE ROW LEVEL SECURITY;
ALTER TABLE auditoria ENABLE ROW LEVEL SECURITY;
ALTER TABLE turnos ENABLE ROW LEVEL SECURITY;
ALTER TABLE sesiones ENABLE ROW LEVEL SECURITY;
ALTER TABLE documentos_paciente ENABLE ROW LEVEL SECURITY;
ALTER TABLE documentos_contenido ENABLE ROW LEVEL SECURITY;
ALTER TABLE historia_psicologica ENABLE ROW LEVEL SECURITY;
