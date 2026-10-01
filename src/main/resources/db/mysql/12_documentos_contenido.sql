-- Contenido de los adjuntos cuando el servidor no tiene disco permanente (ADJUNTOS_EN_BASE=true).
-- En la PC del colegio los archivos siguen en la carpeta adjuntos/ y esta tabla queda vacía.
CREATE TABLE IF NOT EXISTS documentos_contenido (
    documento_id INT PRIMARY KEY,
    contenido LONGBLOB NOT NULL,
    CONSTRAINT fk_documentos_contenido FOREIGN KEY (documento_id)
        REFERENCES documentos_paciente(id) ON DELETE CASCADE
);
