package com.sistemapsicologia.api.util;

import com.sistemapsicologia.api.dto.PacienteDTO;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lee una planilla de estudiantes guardada desde Excel como CSV y la convierte en filas listas
 * para dar de alta, con el resultado de la validación de cada una. Sin UI y sin tocar la base:
 * quien la usa decide qué importar.
 *
 * Acepta lo que Excel produce en la práctica: separador ";" o "," (se detecta solo), UTF-8 (con o
 * sin BOM) o la codificación de Windows (lo que guarda "CSV" a secas en Excel en español),
 * campos entre comillas y encabezados con o sin tildes/mayúsculas. Es compatible con el CSV que
 * exporta el propio sistema, así que se puede exportar, corregir en Excel y volver a importar.
 */
/** Copia de util.ImportadorEstudiantes del escritorio; en el servidor recibe el contenido del archivo subido. */
public final class ImportadorEstudiantes {

    public enum Estado {
        /** Se puede importar tal cual. */
        OK,
        /** Se puede importar, pero algo se descarta (curso no reconocido, fecha inválida…). */
        ADVERTENCIA,
        /** No se importa (falta nombre/apellido, CI repetida…). */
        ERROR
    }

    public static final class Fila {
        public final int numeroLinea;
        public final PacienteDTO paciente;
        public Estado estado = Estado.OK;
        public final List<String> observaciones = new ArrayList<>();

        Fila(int numeroLinea, PacienteDTO paciente) {
            this.numeroLinea = numeroLinea;
            this.paciente = paciente;
        }

        void advertir(String texto) {
            observaciones.add(texto);
            if (estado == Estado.OK) {
                estado = Estado.ADVERTENCIA;
            }
        }

        void error(String texto) {
            observaciones.add(texto);
            estado = Estado.ERROR;
        }

        public String resumen() {
            return observaciones.isEmpty() ? "Listo para importar" : String.join(" · ", observaciones);
        }
    }

    public static final class Resultado {
        public final List<Fila> filas = new ArrayList<>();
        /** Columnas reconocidas, para mostrarle al usuario qué se va a tomar del archivo. */
        public final List<String> columnasReconocidas = new ArrayList<>();
        public final List<String> columnasIgnoradas = new ArrayList<>();

        public int cantidad(Estado estado) {
            int n = 0;
            for (Fila f : filas) {
                if (f.estado == estado) {
                    n++;
                }
            }
            return n;
        }
    }

    private ImportadorEstudiantes() {
    }

    // Nombres de columna aceptados (sin tildes, en minúscula) → campo.
    private static final Map<String, String> ALIAS = new HashMap<>();

    static {
        alias("nombre", "nombre", "nombres");
        alias("apellido", "apellido", "apellidos");
        alias("ci", "ci", "cedula", "c.i.", "c.i", "documento", "nro de documento", "numero de documento");
        alias("telefono", "telefono", "tel", "celular", "tel.");
        alias("genero", "genero", "sexo");
        alias("nacimiento", "fecha de nacimiento", "nacimiento", "fecha nacimiento", "fecha_nacimiento");
        alias("curso", "curso", "curso y seccion", "grado");
        alias("tutor", "tutor/a", "tutor", "nombre del tutor", "padre/madre/tutor", "encargado", "nombre tutor");
        alias("ciTutor", "ci del tutor/a", "ci del tutor", "ci tutor", "cedula del tutor", "ci padre/madre/tutor");
    }

    private static void alias(String campo, String... nombres) {
        for (String n : nombres) {
            ALIAS.put(n, campo);
        }
    }

    /**
     * @param cisExistentes CIs que ya están en la base (se marcan como repetidas)
     */
    public static Resultado leer(byte[] contenido, Set<String> cisExistentes) {
        List<List<String>> registros = parsear(leerTexto(contenido));
        Resultado resultado = new Resultado();
        if (registros.isEmpty()) {
            return resultado;
        }

        // Encabezado → índice de cada campo reconocido.
        List<String> encabezado = registros.get(0);
        Map<String, Integer> indice = new HashMap<>();
        for (int i = 0; i < encabezado.size(); i++) {
            String original = encabezado.get(i).trim();
            String campo = ALIAS.get(normalizar(original));
            if (campo != null && !indice.containsKey(campo)) {
                indice.put(campo, i);
                resultado.columnasReconocidas.add(original);
            } else if (!original.isEmpty()) {
                resultado.columnasIgnoradas.add(original);
            }
        }

        Map<String, String> cursosValidos = new HashMap<>();
        for (String etiqueta : EstructuraAcademica.todasLasEtiquetas()) {
            cursosValidos.put(normalizar(etiqueta), etiqueta);
        }
        Set<String> cisEnArchivo = new HashSet<>();

        for (int r = 1; r < registros.size(); r++) {
            List<String> reg = registros.get(r);
            if (reg.stream().allMatch(v -> v.trim().isEmpty())) {
                continue; // filas vacías del final de la planilla
            }
            PacienteDTO p = new PacienteDTO();
            Fila fila = new Fila(r + 1, p);

            p.setNombre(valor(reg, indice, "nombre"));
            p.setApellido(valor(reg, indice, "apellido"));
            if (p.getNombre() == null) {
                fila.error("Falta el nombre");
            }
            if (p.getApellido() == null) {
                fila.error("Falta el apellido");
            }

            String ci = valor(reg, indice, "ci");
            if (ci != null) {
                ci = ci.replaceAll("[.\\s-]", "");
                if (!ci.matches("[0-9]+")) {
                    fila.advertir("CI \"" + valor(reg, indice, "ci") + "\" no es un número: se deja vacía");
                    ci = null;
                } else if (cisExistentes.contains(ci)) {
                    fila.error("Ya hay un estudiante registrado con CI " + ci);
                } else if (!cisEnArchivo.add(ci)) {
                    fila.error("CI " + ci + " repetida en el archivo");
                }
            }
            p.setCi(ci);

            p.setTelefono(valor(reg, indice, "telefono"));

            String genero = valor(reg, indice, "genero");
            if (genero != null) {
                String g = normalizar(genero);
                if (g.startsWith("m")) {
                    p.setGenero("Masculino");
                } else if (g.startsWith("f")) {
                    p.setGenero("Femenino");
                } else {
                    p.setGenero("Otro");
                }
            }

            String nacimiento = valor(reg, indice, "nacimiento");
            if (nacimiento != null) {
                LocalDate fecha = parsearFecha(nacimiento);
                if (fecha == null) {
                    fila.advertir("Fecha de nacimiento \"" + nacimiento + "\" no reconocida: se deja vacía");
                } else {
                    p.setFechaNacimiento(fecha);
                }
            }

            String curso = valor(reg, indice, "curso");
            if (curso != null) {
                String oficial = cursosValidos.get(normalizar(curso));
                if (oficial != null) {
                    p.setCurso(oficial);
                } else {
                    fila.advertir("Curso \"" + curso + "\" no coincide con la estructura oficial: se importa sin curso");
                }
            }

            p.setNombreTutor(valor(reg, indice, "tutor"));
            String ciTutor = valor(reg, indice, "ciTutor");
            p.setCiTutor(ciTutor != null ? ciTutor.replaceAll("[.\\s-]", "") : null);

            resultado.filas.add(fila);
        }
        return resultado;
    }

    private static String valor(List<String> reg, Map<String, Integer> indice, String campo) {
        Integer i = indice.get(campo);
        if (i == null || i >= reg.size()) {
            return null;
        }
        String v = reg.get(i).trim();
        // El CSV que exporta el sistema antepone ' a valores que empiezan con = + - @ (para que
        // Excel no los tome como fórmula): se quita al volver a importar.
        if (v.length() > 1 && v.charAt(0) == '\'' && "=+-@".indexOf(v.charAt(1)) >= 0) {
            v = v.substring(1);
        }
        return v.isEmpty() ? null : v;
    }

    private static LocalDate parsearFecha(String texto) {
        // STRICT: sin esto Java "acomoda" fechas imposibles (31/02 pasaba a ser 28/02 en silencio).
        String[] formatos = {"d/M/uuuu", "d-M-uuuu", "uuuu-M-d", "d/M/uu"};
        for (String formato : formatos) {
            try {
                LocalDate f = LocalDate.parse(texto.trim(),
                    DateTimeFormatter.ofPattern(formato).withResolverStyle(java.time.format.ResolverStyle.STRICT));
                if (f.isAfter(LocalDate.now()) || f.getYear() < 1950) {
                    return null;
                }
                return f;
            } catch (DateTimeParseException e) {
                // probar el siguiente formato
            }
        }
        return null;
    }

    /** Sin tildes, minúsculas, espacios colapsados: para comparar encabezados y cursos. */
    static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).replace('º', '°').replaceAll("\\s+", " ").trim();
    }

    /** UTF-8 si el archivo es UTF-8 válido (con o sin BOM); si no, la codificación de Excel en Windows. */
    private static String leerTexto(byte[] bytes) {
        int inicio = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, inicio, bytes.length - inicio)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    /** CSV con comillas ("a;b" y "" dentro), separador detectado en la primera línea. */
    static List<List<String>> parsear(String texto) {
        int finPrimera = texto.indexOf('\n');
        String primera = finPrimera >= 0 ? texto.substring(0, finPrimera) : texto;
        char sep = contar(primera, ';') >= contar(primera, ',') ? ';' : ',';
        if (contar(primera, '\t') > contar(primera, sep)) {
            sep = '\t';
        }

        List<List<String>> filas = new ArrayList<>();
        List<String> actual = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (entreComillas) {
                if (c == '"') {
                    if (i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                        campo.append('"');
                        i++;
                    } else {
                        entreComillas = false;
                    }
                } else {
                    campo.append(c);
                }
            } else if (c == '"') {
                entreComillas = true;
            } else if (c == sep) {
                actual.add(campo.toString());
                campo.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') {
                    i++;
                }
                actual.add(campo.toString());
                campo.setLength(0);
                filas.add(actual);
                actual = new ArrayList<>();
            } else {
                campo.append(c);
            }
        }
        if (campo.length() > 0 || !actual.isEmpty()) {
            actual.add(campo.toString());
            filas.add(actual);
        }
        return filas;
    }

    private static int contar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }
}
