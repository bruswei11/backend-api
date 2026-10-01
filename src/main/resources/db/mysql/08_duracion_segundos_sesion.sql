-- El cronómetro de Atenciones ahora registra minutos y segundos exactos, no solo
-- minutos redondeados. duracion_minutos pasa a ser la parte entera de minutos y
-- duracion_segundos el resto (0-59) del mismo cronometraje.
ALTER TABLE sesiones ADD COLUMN duracion_segundos TINYINT NULL AFTER duracion_minutos;
