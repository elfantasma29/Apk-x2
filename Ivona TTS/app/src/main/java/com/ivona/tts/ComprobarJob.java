package com.ivona.tts;

import android.app.job.JobParameters;
import android.app.job.JobService;

public class ComprobarJob extends JobService {

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            try {
                Actualizador.Info i = Actualizador.consultar();
                if (Actualizador.esNueva(i, BuildInfo.version(this))) {
                    Actualizador.guardarPendiente(this, i);
                    Actualizador.avisar(this, i);
                }
            } catch (Throwable ignored) {
            } finally {
                jobFinished(params, false);
            }
        }, "tarea-actualizacion").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
