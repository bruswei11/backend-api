package com.sistemapsicologia.api.controller;

import com.sistemapsicologia.api.dto.DashboardDTO;
import com.sistemapsicologia.api.repository.HistoriaRepository;
import com.sistemapsicologia.api.repository.PacienteRepository;
import com.sistemapsicologia.api.repository.SesionRepository;
import com.sistemapsicologia.api.repository.TurnoRepository;
import com.sistemapsicologia.api.repository.UsuarioRepository;
import com.sistemapsicologia.api.security.UsuarioAutenticado;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mismos agregados que Vista/Dashboard.java del escritorio, en un solo endpoint. `desde=todo`
 * (default) o `desde=mes` replican el combo "Período" (Todo / Este mes) del escritorio.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final int DIAS_UMBRAL_SEGUIMIENTO = 15;
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final PacienteRepository pacienteRepository;
    private final TurnoRepository turnoRepository;
    private final SesionRepository sesionRepository;
    private final HistoriaRepository historiaRepository;
    private final UsuarioRepository usuarioRepository;

    public DashboardController(PacienteRepository pacienteRepository, TurnoRepository turnoRepository,
            SesionRepository sesionRepository, HistoriaRepository historiaRepository, UsuarioRepository usuarioRepository) {
        this.pacienteRepository = pacienteRepository;
        this.turnoRepository = turnoRepository;
        this.sesionRepository = sesionRepository;
        this.historiaRepository = historiaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    public DashboardDTO obtener(@RequestParam(defaultValue = "todo") String periodo, Authentication auth) {
        UsuarioAutenticado u = (UsuarioAutenticado) auth.getPrincipal();
        LocalDate desde = "mes".equalsIgnoreCase(periodo) ? LocalDate.now().withDayOfMonth(1) : LocalDate.of(2000, 1, 1);
        Integer psicologoId = u.esPsicologo() ? u.getUsuarioId() : null;

        DashboardDTO d = new DashboardDTO();
        // Igual que el Panel del escritorio: un profesional ve solo los números de sus estudiantes.
        d.totalEstudiantes = pacienteRepository.contarDesde(desde, psicologoId);
        d.totalAtenciones = sesionRepository.contarDesde(desde, psicologoId);
        d.totalCitas = turnoRepository.contarConfirmadosDesde(desde, psicologoId);
        d.totalProfesionales = usuarioRepository.contarActivosPorRol("psicologo");
        d.nuevosPorMes = pacienteRepository.obtenerNuevosPorMes(6, psicologoId);
        d.porCurso = pacienteRepository.contarPorCurso(psicologoId);
        d.casosPendientes = turnoRepository.contarProgramadosVencidos(psicologoId);
        d.proximasCitas = turnoRepository.obtenerProximas(5, psicologoId);

        // Ausente del todo para secretaria -- ni se calcula -- no solo oculto en el cliente.
        if (!u.esSecretaria()) {
            // Los motivos salen de las entradas de seguimiento (dato clínico): no para "Usuario autorizado".
            d.porMotivo = historiaRepository.contarPorMotivo(desde, psicologoId);
            d.seguimientosVencidosTotal = historiaRepository.contarCasosReiteradosSinSeguimiento(psicologoId, DIAS_UMBRAL_SEGUIMIENTO);
            List<HistoriaRepository.CasoPendiente> casos =
                historiaRepository.obtenerCasosReiteradosSinSeguimiento(5, psicologoId, DIAS_UMBRAL_SEGUIMIENTO);
            d.seguimientosVencidosLista = casos.stream()
                .map(c -> new DashboardDTO.CasoPendienteDTO(c.pacienteId(), c.estudiante(), c.curso(), c.ultimoSeguimiento().format(FORMATO_FECHA)))
                .toList();
        }

        return d;
    }
}
