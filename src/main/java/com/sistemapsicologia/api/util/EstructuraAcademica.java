package com.sistemapsicologia.api.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Estructura académica oficial y cerrada del Colegio Nacional E.M.D. San Roque González de
 * Santacruz: los únicos cursos/secciones/especialidades/turnos que el sistema puede ofrecer o
 * guardar. No se agregan combinaciones por analogía — si algo no está en esta clase, no existe
 * en el colegio y la UI no debe poder construirlo (ver Vista.SelectorCursoSeccion).
 *
 * EEB: 7°, 8° y 9° grado, secciones A y B, turno Mañana/Tarde — excepto 8°B y 9°B, que solo
 * tienen turno Mañana (no existe 8°B-Tarde ni 9°B-Tarde).
 *
 * Bachillerato: 1er, 2do y 3er año, con la misma regla de turno los tres años, según
 * especialidad: Técnico en Informática (Mañana y Tarde), Técnico en Salud (solo Tarde),
 * Técnico en Contabilidad (solo Mañana), Científico en Ciencias Sociales (solo Mañana).
 */
public final class EstructuraAcademica {

    private EstructuraAcademica() {
    }

    public static final String NIVEL_EEB = "Educación Escolar Básica";
    public static final String NIVEL_BACHILLERATO = "Bachillerato";
    public static final String[] NIVELES = {NIVEL_EEB, NIVEL_BACHILLERATO};

    public static final String TURNO_MANANA = "Mañana";
    public static final String TURNO_TARDE = "Tarde";

    // ---- Educación Escolar Básica ----

    public static final String[] GRADOS_EEB = {"7°", "8°", "9°"};
    public static final String[] SECCIONES_EEB = {"A", "B"};

    /** Turnos válidos para un grado+sección de EEB. 8°B y 9°B solo tienen Mañana. */
    public static String[] turnosValidosEEB(String grado, String seccion) {
        if ("B".equals(seccion) && ("8°".equals(grado) || "9°".equals(grado))) {
            return new String[]{TURNO_MANANA};
        }
        return new String[]{TURNO_MANANA, TURNO_TARDE};
    }

    // ---- Bachillerato ----

    public static final String[] ANIOS_BACHILLERATO = {"1er año", "2do año", "3er año"};

    public static final String MODALIDAD_TECNICO = "Técnico";
    public static final String MODALIDAD_CIENTIFICO = "Científico";
    public static final String[] MODALIDADES = {MODALIDAD_TECNICO, MODALIDAD_CIENTIFICO};

    public static final String ESP_INFORMATICA = "Informática";
    public static final String ESP_SALUD = "Salud";
    public static final String ESP_CONTABILIDAD = "Contabilidad";
    public static final String ESP_CIENCIAS_SOCIALES = "Ciencias Sociales";

    /** Especialidades que existen dentro de una modalidad de Bachillerato. */
    public static String[] especialidadesPorModalidad(String modalidad) {
        if (MODALIDAD_TECNICO.equals(modalidad)) {
            return new String[]{ESP_INFORMATICA, ESP_SALUD, ESP_CONTABILIDAD};
        }
        if (MODALIDAD_CIENTIFICO.equals(modalidad)) {
            return new String[]{ESP_CIENCIAS_SOCIALES};
        }
        return new String[0];
    }

    /** Turnos válidos para una especialidad de Bachillerato (igual los 3 años). */
    public static String[] turnosValidosBachillerato(String especialidad) {
        if (ESP_INFORMATICA.equals(especialidad)) {
            return new String[]{TURNO_MANANA, TURNO_TARDE};
        }
        if (ESP_SALUD.equals(especialidad)) {
            return new String[]{TURNO_TARDE};
        }
        if (ESP_CONTABILIDAD.equals(especialidad)) {
            return new String[]{TURNO_MANANA};
        }
        if (ESP_CIENCIAS_SOCIALES.equals(especialidad)) {
            return new String[]{TURNO_MANANA};
        }
        return new String[0];
    }

    // ---- Etiqueta canónica (lo único que se persiste en pacientes.curso) ----

    public static String construirEtiquetaEEB(String grado, String seccion, String turno) {
        return grado + " EEB - Sección " + seccion + " - Turno " + turno;
    }

    public static String construirEtiquetaBachillerato(String anio, String modalidad, String especialidad, String turno) {
        return "Bachillerato " + modalidad + " en " + especialidad + " - " + anio + " - Turno " + turno;
    }

    /** Selección ya parseada de una etiqueta guardada, para repoblar el selector al editar. */
    public static final class Seleccion {
        public final boolean esBachillerato;
        public final String grado;
        public final String seccion;
        public final String anio;
        public final String modalidad;
        public final String especialidad;
        public final String turno;

        private Seleccion(boolean esBachillerato, String grado, String seccion,
                           String anio, String modalidad, String especialidad, String turno) {
            this.esBachillerato = esBachillerato;
            this.grado = grado;
            this.seccion = seccion;
            this.anio = anio;
            this.modalidad = modalidad;
            this.especialidad = especialidad;
            this.turno = turno;
        }

        static Seleccion eeb(String grado, String seccion, String turno) {
            return new Seleccion(false, grado, seccion, null, null, null, turno);
        }

        static Seleccion bachillerato(String anio, String modalidad, String especialidad, String turno) {
            return new Seleccion(true, null, null, anio, modalidad, especialidad, turno);
        }
    }

    /** Reconstruye una Seleccion a partir de la etiqueta canónica guardada, o null si no matchea el formato esperado. */
    public static Seleccion parsearEtiqueta(String etiqueta) {
        if (etiqueta == null) {
            return null;
        }
        etiqueta = etiqueta.trim();

        if (etiqueta.startsWith("Bachillerato ")) {
            for (String modalidad : MODALIDADES) {
                String prefijo = "Bachillerato " + modalidad + " en ";
                if (etiqueta.startsWith(prefijo)) {
                    String resto = etiqueta.substring(prefijo.length());
                    String[] partes = resto.split(" - ");
                    if (partes.length == 3) {
                        String especialidad = partes[0].trim();
                        String anio = partes[1].trim();
                        String turno = partes[2].replace("Turno", "").trim();
                        return Seleccion.bachillerato(anio, modalidad, especialidad, turno);
                    }
                }
            }
            return null;
        }

        for (String grado : GRADOS_EEB) {
            String prefijo = grado + " EEB - Sección ";
            if (etiqueta.startsWith(prefijo)) {
                String resto = etiqueta.substring(prefijo.length());
                String[] partes = resto.split(" - ");
                if (partes.length == 2) {
                    String seccion = partes[0].trim();
                    String turno = partes[1].replace("Turno", "").trim();
                    return Seleccion.eeb(grado, seccion, turno);
                }
            }
        }
        return null;
    }

    /** Valida que una etiqueta corresponda exactamente a una combinación académica oficial. */
    public static boolean esEtiquetaValida(String etiqueta) {
        Seleccion s = parsearEtiqueta(etiqueta);
        if (s == null) {
            return false;
        }
        if (s.esBachillerato) {
            return Arrays.asList(especialidadesPorModalidad(s.modalidad)).contains(s.especialidad)
                && Arrays.asList(ANIOS_BACHILLERATO).contains(s.anio)
                && Arrays.asList(turnosValidosBachillerato(s.especialidad)).contains(s.turno);
        }
        return Arrays.asList(GRADOS_EEB).contains(s.grado)
            && Arrays.asList(SECCIONES_EEB).contains(s.seccion)
            && Arrays.asList(turnosValidosEEB(s.grado, s.seccion)).contains(s.turno);
    }

    /** Todas las etiquetas oficiales válidas, en orden — para poblar un combo de filtro por curso exacto. */
    public static List<String> todasLasEtiquetas() {
        List<String> resultado = new ArrayList<>();
        for (String grado : GRADOS_EEB) {
            for (String seccion : SECCIONES_EEB) {
                for (String turno : turnosValidosEEB(grado, seccion)) {
                    resultado.add(construirEtiquetaEEB(grado, seccion, turno));
                }
            }
        }
        for (String modalidad : MODALIDADES) {
            for (String especialidad : especialidadesPorModalidad(modalidad)) {
                for (String anio : ANIOS_BACHILLERATO) {
                    for (String turno : turnosValidosBachillerato(especialidad)) {
                        resultado.add(construirEtiquetaBachillerato(anio, modalidad, especialidad, turno));
                    }
                }
            }
        }
        return resultado;
    }
}
