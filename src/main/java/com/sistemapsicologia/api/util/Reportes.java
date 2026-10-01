package com.sistemapsicologia.api.util;

import com.sistemapsicologia.api.dto.HistoriaDTO;
import com.sistemapsicologia.api.dto.PacienteDTO;
import com.sistemapsicologia.api.dto.SesionDTO;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Copia de util.GeneradorReportes del escritorio: mismo CSV para Excel en español (";" y BOM),
 * misma ficha y nota de atención en texto, mismo pie confidencial. Así un archivo exportado desde
 * la web es idéntico al que se exporta desde la PC.
 */
public final class Reportes {

    private Reportes() {
    }

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter ARCHIVO = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final char SEPARADOR = ';';
    public static final String BOM = "\uFEFF";

    private static final String AVISO_CONFIDENCIAL =
        "Documento confidencial. Uso exclusivo del Departamento de Psicología.\n"
        + "Contiene datos sensibles de un/a estudiante: no difundir ni compartir fuera del ámbito profesional.";

    /** @param incluirMotivo false para "Usuario autorizado" (secretaria): el motivo es clínico. */
    public static String csvEstudiantes(List<PacienteDTO> pacientes, boolean incluirMotivo) {
        StringBuilder sb = new StringBuilder();
        String[] encabezado = {"ID", "Apellido", "Nombre", "CI", "Teléfono", "Género", "Fecha de nacimiento",
            "Curso", "Tutor/a", "CI del tutor/a", "Motivo de atención"};
        fila(sb, incluirMotivo ? encabezado : Arrays.copyOf(encabezado, encabezado.length - 1));
        for (PacienteDTO p : pacientes) {
            String[] f = {
                String.valueOf(p.getId()), p.getApellido(), p.getNombre(), p.getCi(), p.getTelefono(), p.getGenero(),
                p.getFechaNacimiento() != null ? p.getFechaNacimiento().format(FECHA) : "",
                p.getCurso(), p.getNombreTutor(), p.getCiTutor(), p.getMotivoConsulta()};
            fila(sb, incluirMotivo ? f : Arrays.copyOf(f, f.length - 1));
        }
        return sb.toString();
    }

    private static void fila(StringBuilder sb, String... valores) {
        for (int i = 0; i < valores.length; i++) {
            if (i > 0) {
                sb.append(SEPARADOR);
            }
            sb.append(celda(valores[i]));
        }
        sb.append("\r\n");
    }

    private static String celda(String valor) {
        if (valor == null) {
            return "";
        }
        // = + - @ al principio: Excel lo tomaría como fórmula (inyección CSV).
        if (!valor.isEmpty() && "=+-@".indexOf(valor.charAt(0)) >= 0) {
            valor = "'" + valor;
        }
        if (valor.indexOf(SEPARADOR) >= 0 || valor.contains("\"") || valor.contains("\n") || valor.contains("\r")) {
            return "\"" + valor.replace("\"", "\"\"") + "\"";
        }
        return valor;
    }

    /** Ficha del estudiante con la entrada de seguimiento más reciente (o sin ella). */
    public static String ficha(PacienteDTO p, HistoriaDTO historia, String generadoPor) {
        StringBuilder sb = new StringBuilder();
        sb.append(linea('=')).append("\n");
        sb.append("FICHA DEL ESTUDIANTE — DEPARTAMENTO DE PSICOLOGÍA\n");
        sb.append(linea('=')).append("\n\n");
        sb.append("DATOS DEL ESTUDIANTE\n").append(linea('-')).append("\n");
        sb.append("Nombre: ").append(p.getNombre()).append(" ").append(p.getApellido()).append("\n");
        sb.append("Curso: ").append(o(p.getCurso())).append("\n");
        sb.append("CI: ").append(o(p.getCi())).append("\n");
        sb.append("Teléfono: ").append(o(p.getTelefono())).append("\n");
        sb.append("Género: ").append(o(p.getGenero())).append("\n");
        sb.append("Dirección: ").append(o(p.getDireccion())).append("\n");
        sb.append("Consentimiento del tutor: ").append(p.isConsentimientoTutor() ? "Sí" : "No registrado").append("\n\n");
        if (historia != null) {
            sb.append("INFORMACIÓN DE SEGUIMIENTO\n").append(linea('-')).append("\n");
            sb.append("\nANTECEDENTES:\n").append(o(historia.getAntecedentes())).append("\n");
            sb.append("\nMOTIVO DE LA ATENCIÓN:\n").append(o(historia.getMotivoConsulta())).append("\n");
            sb.append("\nOBSERVACIONES:\n").append(o(historia.getObservacionesGenerales())).append("\n");
            sb.append("\nDIAGNÓSTICO SITUACIONAL:\n").append(o(historia.getDiagnostico())).append("\n");
            if (historia.getCodigoCie10() != null && !historia.getCodigoCie10().isBlank()) {
                sb.append("Código CIE-10: ").append(historia.getCodigoCie10()).append("\n");
            }
            sb.append("\nPLAN DE INTERVENCIÓN:\n").append(o(historia.getTratamiento())).append("\n");
            if (historia.getTipoAcoso() != null && !historia.getTipoAcoso().isBlank()) {
                sb.append("\nCLASIFICACIÓN (Ley 4633/2012 de acoso escolar):\n");
                sb.append("Tipo: ").append(etiquetaAcoso(historia.getTipoAcoso())).append("\n");
                sb.append("¿Situación reiterada?: ").append(historia.isEsReiterado() ? "Sí" : "No").append("\n");
            }
        }
        pie(sb, generadoPor);
        return sb.toString();
    }

    /** Nota de una atención. Las notas privadas no se incluyen a propósito (igual que el escritorio). */
    public static String notaAtencion(PacienteDTO p, SesionDTO s, String generadoPor) {
        StringBuilder sb = new StringBuilder();
        sb.append(linea('=')).append("\n");
        sb.append("NOTA DE ATENCIÓN PSICOLÓGICA\n");
        sb.append(linea('=')).append("\n\n");
        sb.append("Estudiante: ").append(p.getNombre()).append(" ").append(p.getApellido()).append("\n");
        if (p.getCurso() != null && !p.getCurso().isEmpty()) {
            sb.append("Curso: ").append(p.getCurso()).append("\n");
        }
        if (s.getFecha() != null) {
            sb.append("Fecha de la atención: ").append(s.getFecha().format(FECHA_HORA)).append("\n");
        }
        sb.append("\n");
        seccion(sb, "RELATO DEL ESTUDIANTE (SUBJETIVO)", s.getSubjetivo());
        seccion(sb, "OBSERVACIÓN PROFESIONAL (OBJETIVO)", s.getObjetivo());
        seccion(sb, "ANÁLISIS DE LA SITUACIÓN", s.getAnalisis());
        seccion(sb, "ACUERDOS Y RECOMENDACIONES (PLAN)", s.getPlan());
        pie(sb, generadoPor);
        return sb.toString();
    }

    private static void seccion(StringBuilder sb, String titulo, String contenido) {
        sb.append(titulo).append("\n").append(linea('-')).append("\n");
        sb.append(o(contenido != null ? contenido.trim() : null)).append("\n\n");
    }

    private static void pie(StringBuilder sb, String generadoPor) {
        sb.append("\n").append(linea('=')).append("\n");
        sb.append("Generado por: ").append(generadoPor != null ? generadoPor : "—").append("\n");
        sb.append("Fecha de generación: ").append(LocalDateTime.now().format(FECHA_HORA)).append("\n\n");
        sb.append(AVISO_CONFIDENCIAL).append("\n");
        sb.append(linea('=')).append("\n");
    }

    public static String etiquetaAcoso(String codigo) {
        switch (codigo) {
            case "directo": return "Acoso directo (físico)";
            case "indirecto": return "Acoso indirecto (daño a bienes)";
            case "verbal": return "Acoso verbal";
            default: return codigo;
        }
    }

    /** "Ficha_Perez_Juan_20260924_153000.txt", sin caracteres inválidos para Windows. */
    public static String nombreArchivo(String prefijo, PacienteDTO p, String extension) {
        return prefijo + "_" + nombreSeguro(p.getApellido() + " " + p.getNombre()).replace(' ', '_') + "_"
            + LocalDateTime.now().format(ARCHIVO) + "." + extension;
    }

    public static String nombreListado() {
        return "Estudiantes_" + LocalDateTime.now().format(ARCHIVO) + ".csv";
    }

    private static String nombreSeguro(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").replaceAll("\\s+", " ").trim();
    }

    private static String o(String valor) {
        return valor != null && !valor.trim().isEmpty() ? valor : "No especificado";
    }

    private static String linea(char c) {
        return String.valueOf(c).repeat(80);
    }
}
