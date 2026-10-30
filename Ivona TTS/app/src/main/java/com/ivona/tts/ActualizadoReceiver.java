package com.ivona.tts;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;

public class ActualizadoReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context contexto, Intent intento) {
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intento.getAction())) {
            return;
        }
        final Context app = contexto.getApplicationContext();
        final PendingResult pendiente = goAsync();
        new Thread(() -> {
            ServiceConnection conexion = null;
            try {
                final Motor m = Motor.get(app);
                if (!Voces.instaladas(app).isEmpty()) {
                    conexion = new ServiceConnection() {
                        @Override
                        public void onServiceConnected(ComponentName n, IBinder s) {
                        }

                        @Override
                        public void onServiceDisconnected(ComponentName n) {
                        }
                    };
                    Intent b = new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE).setPackage(app.getPackageName());
                    app.bindService(b, conexion, Context.BIND_AUTO_CREATE);
                    long limite = System.currentTimeMillis() + 8000;
                    while (!m.vivo() && System.currentTimeMillis() < limite) {
                        Thread.sleep(250);
                    }
                }
                pendiente.finish();
                if (!Voces.instaladas(app).isEmpty()) {
                    long limite = System.currentTimeMillis() + 90000;
                    while (!m.vivo() && System.currentTimeMillis() < limite) {
                        Thread.sleep(500);
                    }
                    if (m.vivo()) {
                        avisar(app);
                        Thread.sleep(15000);
                    }
                }
            } catch (Throwable t) {
                try {
                    Motor.get(app).guardarFallo("actualización", t);
                } catch (Throwable ignored) {
                }
                try {
                    pendiente.finish();
                } catch (Throwable ignored) {
                }
            } finally {
                if (conexion != null) {
                    try {
                        app.unbindService(conexion);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }, "tras-actualizar").start();
    }

    static void avisar(Context app) {
        try {
            app.sendBroadcast(new Intent("com.nirenr.talkman.ACTION_RE_INIT_SYSTEM_TTS").setPackage("com.nirenr.talkman"));
        } catch (Throwable ignored) {
        }
        try {
            app.sendBroadcast(new Intent(TextToSpeech.Engine.ACTION_TTS_DATA_INSTALLED));
        } catch (Throwable ignored) {
        }
    }
}
