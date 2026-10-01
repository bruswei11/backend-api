package com.sistemapsicologia.api.util;

/** Copia exacta de util.MotivosAtencion del escritorio -- mismos textos, para que el conteo por motivo coincida. */
public final class MotivosAtencion {

    private MotivosAtencion() {
    }

    public static final String[] PRESETS = {
        "Incumplimiento de las normas de convivencia",
        "Agresión física a un compañero/a",
        "Agresión verbal o insultos",
        "Bullying o acoso escolar",
        "Conflicto entre compañeros",
        "Falta de respeto a un docente o autoridad",
        "Conducta disruptiva en el aula",
        "Uso inadecuado de dispositivos electrónicos",
        "Inasistencias o impuntualidad reiteradas",
        "Incumplimiento de tareas o materiales escolares",
        "Daño a bienes o materiales del colegio",
        "Uso de vocabulario inapropiado",
        "Consumo o posesión de sustancias prohibidas",
        "Situación familiar o emocional que afecta al estudiante"
    };

    public static final String OTRO = "Otro motivo";
}
