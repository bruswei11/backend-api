package com.sistemapsicologia.api.controller;

import com.sistemapsicologia.api.dto.DocumentoDTO;
import com.sistemapsicologia.api.dto.PacienteDTO;
import com.sistemapsicologia.api.repository.AuditoriaRepository;
import com.sistemapsicologia.api.repository.DocumentoRepository;
import com.sistemapsicologia.api.repository.PacienteRepository;
import com.sistemapsicologia.api.security.UsuarioAutenticado;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Adjuntos del estudiante ("Adjuntos (n)" de DetallePaciente en el escritorio). Dos formas de
 * guardar el archivo:
 * - En la PC del colegio: la MISMA carpeta que usa el escritorio (adjuntos/&lt;id&gt;/&lt;timestamp&gt;_&lt;nombre&gt;),
 *   con la ruta guardada RELATIVA a la carpeta del sistema, igual que util.Carpetas.rutaRelativa.
 * - En la nube sin disco permanente (ADJUNTOS_EN_BASE=true): el contenido va a documentos_contenido.
 * Mismas reglas de acceso que Seguimiento/Atenciones: secretaria sin acceso, psicologo solo a los suyos.
 */
@RestController
@RequestMapping("/api")
public class DocumentoController {

    /** Prefijo de ruta_archivo para los archivos guardados dentro de la base. */
    private static final String PREFIJO_BASE = "base:";

    private final DocumentoRepository documentoRepository;
    private final PacienteRepository pacienteRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final Path carpetaAdjuntos;
    private final boolean enBase;

    public DocumentoController(DocumentoRepository documentoRepository, PacienteRepository pacienteRepository,
            AuditoriaRepository auditoriaRepository, @Value("${app.adjuntos.carpeta}") String carpetaAdjuntos,
            @Value("${app.adjuntos.en-base:false}") boolean enBase) {
        this.documentoRepository = documentoRepository;
        this.pacienteRepository = pacienteRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.carpetaAdjuntos = Path.of(carpetaAdjuntos).toAbsolutePath().normalize();
        this.enBase = enBase;
    }

    @GetMapping("/estudiantes/{pacienteId}/documentos")
    public ResponseEntity<?> listar(@PathVariable int pacienteId, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        ResponseEntity<?> denegado = verificarAcceso(u, pacienteId);
        if (denegado != null) {
            return denegado;
        }
        return ResponseEntity.ok(documentoRepository.listarPorPaciente(pacienteId));
    }

    @PostMapping("/estudiantes/{pacienteId}/documentos")
    @Transactional
    public ResponseEntity<?> subir(@PathVariable int pacienteId, @RequestParam("archivo") MultipartFile archivo,
            Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        ResponseEntity<?> denegado = verificarAcceso(u, pacienteId);
        if (denegado != null) {
            return denegado;
        }
        if (archivo.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo está vacío"));
        }

        // El nombre original se limpia de separadores de ruta antes de usarlo (evita "../../...").
        String nombreOriginal = Path.of(Objects.requireNonNullElse(archivo.getOriginalFilename(), "archivo")
            .replace('\\', '/')).getFileName().toString();
        String timestamp = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String nombreGuardado = timestamp + "_" + nombreOriginal;
        String extension = nombreOriginal.contains(".")
            ? nombreOriginal.substring(nombreOriginal.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT) : null;

        try {
            int id;
            if (enBase) {
                id = documentoRepository.crear(pacienteId, nombreOriginal, PREFIJO_BASE + nombreGuardado, extension, u.getUsuarioId());
                documentoRepository.guardarContenido(id, archivo.getBytes());
            } else {
                Path carpetaEstudiante = carpetaAdjuntos.resolve(String.valueOf(pacienteId));
                Files.createDirectories(carpetaEstudiante);
                Path destino = carpetaEstudiante.resolve(nombreGuardado);
                archivo.transferTo(destino);
                // Relativa a la carpeta del sistema ("adjuntos\<id>\archivo"), como la guarda el escritorio.
                String relativa = carpetaAdjuntos.getParent() != null
                    ? carpetaAdjuntos.getParent().relativize(destino).toString() : destino.toString();
                id = documentoRepository.crear(pacienteId, nombreOriginal, relativa, extension, u.getUsuarioId());
            }
            auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "ADJUNTAR_DOCUMENTO", "documentos_paciente", id, nombreOriginal);
            return ResponseEntity.status(201).body(documentoRepository.obtenerPorId(id).orElseThrow());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "No se pudo guardar el archivo: " + e.getMessage()));
        }
    }

    /** @param ver true = mostrar en el navegador (imágenes, PDF); false = descargar. */
    @GetMapping("/documentos/{id}/descargar")
    public ResponseEntity<?> descargar(@PathVariable int id, @RequestParam(defaultValue = "false") boolean ver,
            Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        Optional<DocumentoDTO> docOpt = documentoRepository.obtenerPorId(id);
        if (docOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        DocumentoDTO doc = docOpt.get();
        ResponseEntity<?> denegado = verificarAcceso(u, doc.getPacienteId());
        if (denegado != null) {
            return denegado;
        }

        Resource contenido;
        String ruta = doc.getRutaArchivo();
        if (ruta.startsWith(PREFIJO_BASE)) {
            Optional<byte[]> bytes = documentoRepository.obtenerContenido(id);
            if (bytes.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "El archivo no está guardado en la base"));
            }
            contenido = new ByteArrayResource(bytes.get());
        } else {
            Path archivo = resolver(ruta);
            if (archivo == null || !Files.exists(archivo)) {
                return ResponseEntity.status(404).body(Map.of("error",
                    "El archivo no está en este servidor (puede haberse cargado desde otra computadora)"));
            }
            contenido = new FileSystemResource(archivo);
        }
        auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "VER_DOCUMENTO", "documentos_paciente", id, doc.getNombreArchivo());

        MediaType tipo = MediaTypeFactory.getMediaType(doc.getNombreArchivo()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        ContentDisposition disposicion = (ver ? ContentDisposition.inline() : ContentDisposition.attachment())
            .filename(doc.getNombreArchivo(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
            .contentType(tipo)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(contenido);
    }

    @DeleteMapping("/documentos/{id}")
    public ResponseEntity<?> eliminar(@PathVariable int id, Authentication auth) {
        UsuarioAutenticado u = usuario(auth);
        Optional<DocumentoDTO> docOpt = documentoRepository.obtenerPorId(id);
        if (docOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        DocumentoDTO doc = docOpt.get();
        ResponseEntity<?> denegado = verificarAcceso(u, doc.getPacienteId());
        if (denegado != null) {
            return denegado;
        }

        documentoRepository.eliminar(id); // en modo base, el contenido se borra en cascada
        if (!doc.getRutaArchivo().startsWith(PREFIJO_BASE)) {
            try {
                Path archivo = resolver(doc.getRutaArchivo());
                if (archivo != null) {
                    Files.deleteIfExists(archivo);
                }
            } catch (IOException ignored) {
                // Mismo criterio que el escritorio: si el archivo físico no se puede borrar, no se
                // bloquea la eliminación del registro.
            }
        }
        auditoriaRepository.registrar(u.getUsuarioId(), u.getNombre(), "ELIMINAR_DOCUMENTO", "documentos_paciente", id, doc.getNombreArchivo());
        return ResponseEntity.noContent().build();
    }

    /**
     * ruta_archivo relativa (como la guarda el escritorio: "adjuntos\18\archivo.png") se resuelve
     * contra la carpeta del sistema; las absolutas se usan tal cual. Nunca sale de la carpeta de adjuntos.
     */
    private Path resolver(String ruta) {
        Path p = Path.of(ruta.replace('\\', '/'));
        Path base = carpetaAdjuntos.getParent() != null ? carpetaAdjuntos.getParent() : carpetaAdjuntos;
        Path absoluta = (p.isAbsolute() ? p : base.resolve(p)).toAbsolutePath().normalize();
        return absoluta.startsWith(carpetaAdjuntos) ? absoluta : null;
    }

    /** null si puede; si no, la respuesta de error (secretaria nunca; psicologo solo sus estudiantes). */
    private ResponseEntity<?> verificarAcceso(UsuarioAutenticado u, int pacienteId) {
        if (u.esSecretaria()) {
            return ResponseEntity.status(403).body(Map.of("error", "No autorizado a datos clínicos"));
        }
        Optional<PacienteDTO> pacienteOpt = pacienteRepository.obtenerPorId(pacienteId);
        if (pacienteOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (u.esPsicologo() && !Objects.equals(pacienteOpt.get().getPsicologoId(), u.getUsuarioId())) {
            return ResponseEntity.status(403).body(Map.of("error", "No autorizado para este estudiante"));
        }
        return null;
    }

    private UsuarioAutenticado usuario(Authentication auth) {
        return (UsuarioAutenticado) auth.getPrincipal();
    }
}
