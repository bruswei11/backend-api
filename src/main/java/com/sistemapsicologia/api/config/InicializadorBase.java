package com.sistemapsicologia.api.config;

import com.sistemapsicologia.api.dto.HistoriaDTO;
import com.sistemapsicologia.api.dto.PacienteDTO;
import com.sistemapsicologia.api.dto.SesionDTO;
import com.sistemapsicologia.api.repository.HistoriaRepository;
import com.sistemapsicologia.api.repository.PacienteRepository;
import com.sistemapsicologia.api.repository.SesionRepository;
import com.sistemapsicologia.api.repository.TurnoRepository;
import com.sistemapsicologia.api.repository.UsuarioRepository;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

/**
 * Para un servidor nuevo (en la nube) con una base vacía: crea las tablas con los mismos scripts de
 * SistemaPsicologia/db-init (copiados en resources/db) y, si se pide, carga datos INVENTADOS de
 * demostración. En la PC del colegio las dos opciones están apagadas y esto no hace nada.
 *
 * Nunca toca una base que ya tiene tablas, y los datos demo solo se cargan si no hay ningún usuario:
 * no hay forma de que mezcle datos inventados con datos reales.
 */
@Component
public class InicializadorBase implements InitializingBean {

    private static final Logger LOG = LoggerFactory.getLogger(InicializadorBase.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarios;
    private final PacienteRepository pacientes;
    private final TurnoRepository turnos;
    private final HistoriaRepository historias;
    private final SesionRepository sesiones;
    private final boolean crearTablas;
    private final boolean datosDemo;
    private final String passwordDemo;

    public InicializadorBase(DataSource dataSource, JdbcTemplate jdbc, UsuarioRepository usuarios,
            PacienteRepository pacientes, TurnoRepository turnos, HistoriaRepository historias,
            SesionRepository sesiones,
            @Value("${app.base.crear-tablas:false}") boolean crearTablas,
            @Value("${app.base.datos-demo:false}") boolean datosDemo,
            @Value("${APP_DEMO_PASSWORD:Demo2026!}") String passwordDemo) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.usuarios = usuarios;
        this.pacientes = pacientes;
        this.turnos = turnos;
        this.historias = historias;
        this.sesiones = sesiones;
        this.crearTablas = crearTablas;
        this.datosDemo = datosDemo;
        this.passwordDemo = passwordDemo;
    }

    /**
     * Corre mientras se arma la aplicación, ANTES de que el servidor web acepte pedidos: así nadie
     * (ni el chequeo de salud del hosting) llega a una base con las tablas a medio crear.
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        if (!crearTablas && !datosDemo) {
            return; // PC del colegio: no hace nada
        }
        esperarBase();
        if (crearTablas && !existeTabla("usuarios")) {
            crearEsquema();
        }
        if (datosDemo) {
            Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM usuarios", Integer.class);
            if (total != null && total == 0) {
                cargarDemo();
            } else {
                LOG.info("Datos demo: la base ya tiene usuarios, no se carga nada.");
            }
        }
    }

    /** En la nube la base puede tardar en aceptar conexiones al arrancar todo junto: espera hasta 90 s. */
    private void esperarBase() throws InterruptedException {
        for (int intento = 1; ; intento++) {
            try {
                jdbc.queryForObject("SELECT 1", Integer.class);
                return;
            } catch (org.springframework.dao.DataAccessException e) {
                if (intento >= 30) {
                    throw e;
                }
                LOG.info("Esperando a la base de datos ({}/30)...", intento);
                Thread.sleep(3000);
            }
        }
    }

    private boolean existeTabla(String nombre) throws java.sql.SQLException {
        // Metadatos JDBC en vez de information_schema/DATABASE(): funciona igual en MySQL y PostgreSQL.
        try (Connection cn = dataSource.getConnection();
             java.sql.ResultSet rs = cn.getMetaData().getTables(cn.getCatalog(), null, nombre, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    /** "mysql" o "postgresql": carpeta de resources/db con los scripts para esa base. */
    private String tipoDeBase() throws java.sql.SQLException {
        try (Connection cn = dataSource.getConnection()) {
            String producto = cn.getMetaData().getDatabaseProductName().toLowerCase(java.util.Locale.ROOT);
            return producto.contains("postgres") ? "postgresql" : "mysql";
        }
    }

    private void crearEsquema() throws Exception {
        String tipo = tipoDeBase();
        Resource[] scripts = new PathMatchingResourcePatternResolver().getResources("classpath:db/" + tipo + "/*.sql");
        java.util.Arrays.sort(scripts, java.util.Comparator.comparing(Resource::getFilename));
        try (Connection cn = dataSource.getConnection()) {
            for (Resource script : scripts) {
                LOG.info("Creando tablas ({}): {}", tipo, script.getFilename());
                ScriptUtils.executeSqlScript(cn, new EncodedResource(script, "UTF-8"));
            }
        }
    }

    private void cargarDemo() {
        LOG.warn("Cargando DATOS DE DEMOSTRACIÓN (inventados). No usar esta base con estudiantes reales.");
        usuarios.crear("demo_admin", "Administración (demo)", passwordDemo, "admin");
        int psico = usuarios.crear("demo_psicologa", "Lic. Ana Demo", passwordDemo, "psicologo");
        int psico2 = usuarios.crear("demo_psicologo2", "Lic. Pedro Demo", passwordDemo, "psicologo");
        usuarios.crear("demo_secretaria", "Secretaría (demo)", passwordDemo, "secretaria");

        String[][] estudiantes = {
            {"Lucía", "Ejemplo", "Femenino", "7° EEB - Sección A - Turno Mañana"},
            {"Mateo", "Prueba", "Masculino", "7° EEB - Sección B - Turno Tarde"},
            {"Sofía", "Muestra", "Femenino", "8° EEB - Sección A - Turno Mañana"},
            {"Tomás", "Ficticio", "Masculino", "9° EEB - Sección B - Turno Mañana"},
            {"Valentina", "Demo", "Femenino", "Bachillerato Técnico en Informática - 1er año - Turno Tarde"},
            {"Diego", "Inventado", "Masculino", "Bachillerato Científico en Ciencias Sociales - 2do año - Turno Mañana"},
        };
        int[] ids = new int[estudiantes.length];
        for (int i = 0; i < estudiantes.length; i++) {
            PacienteDTO p = new PacienteDTO();
            p.setNombre(estudiantes[i][0]);
            p.setApellido(estudiantes[i][1]);
            p.setGenero(estudiantes[i][2]);
            p.setCurso(estudiantes[i][3]);
            p.setCi(String.valueOf(9000001 + i)); // CIs inventadas
            p.setNombreTutor("Tutor/a de " + estudiantes[i][0] + " (demo)");
            p.setConsentimientoTutor(i % 3 != 2);
            p.setConsentimientoFecha(p.isConsentimientoTutor() ? LocalDateTime.now() : null);
            p.setPsicologoId(i == estudiantes.length - 1 ? null : (i % 2 == 0 ? psico : psico2));
            ids[i] = pacientes.crear(p);
        }

        LocalDate hoy = LocalDate.now();
        turnos.crear(ids[0], psico, hoy.atTime(LocalTime.of(8, 0)), "completado", null);
        turnos.crear(ids[2], psico, hoy.atTime(LocalTime.of(15, 30)), "programado", null);
        turnos.crear(ids[4], psico, hoy.plusDays(1).atTime(LocalTime.of(10, 0)), "programado", null);
        turnos.crear(ids[1], psico2, hoy.plusDays(2).atTime(LocalTime.of(9, 15)), "programado", null);

        HistoriaDTO h = new HistoriaDTO();
        h.setPacienteId(ids[0]);
        h.setPsicologoId(psico);
        h.setMotivoConsulta("Conflicto entre compañeros");
        h.setObservacionesGenerales("Entrada de ejemplo para la demostración.");
        h.setTipoAcoso("verbal");
        h.setEsReiterado(true);
        historias.crear(h);

        SesionDTO s = new SesionDTO();
        s.setPacienteId(ids[0]);
        s.setPsicologoId(psico);
        s.setSubjetivo("Texto de ejemplo (demo).");
        s.setPlan("Reunión con el tutor (demo).");
        s.setDuracionMinutos(30);
        s.setDuracionSegundos(0);
        sesiones.crear(s);
        LOG.warn("Datos demo cargados. Usuarios: demo_admin, demo_psicologa, demo_psicologo2, demo_secretaria.");
    }
}
