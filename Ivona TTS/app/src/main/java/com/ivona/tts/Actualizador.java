package com.ivona.tts;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class Actualizador {

    static final String URL_VERSION = "https://raw.githubusercontent.com/elfantasma29/Apk-x2/main/Ivona%20TTS/version.json";
    static final String PREFIJO_APK = "https://raw.githubusercontent.com/elfantasma29/Apk-x2/main/Ivona%20TTS/apk/";
    private static final int ID_TAREA = 7301;
    private static final int ID_AVISO = 7302;
    private static final String CANAL = "actualizaciones";
    private static final long ENTRE_CONSULTAS_MS = 15L * 60L * 1000L;

    static volatile boolean pantallaAbierta = false;
    private static volatile boolean consultando = false;
    private static volatile long ultimaConsulta = 0;

    private Actualizador() {
    }

    static final class Info {
        final long codigo;
        final String nombre;
        final String apk;
        final String sha;
        final String novedades;

        Info(long codigo, String nombre, String apk, String sha, String novedades) {
            this.codigo = codigo;
            this.nombre = nombre;
            this.apk = apk;
            this.sha = sha;
            this.novedades = novedades;
        }
    }

    static Info analizar(String json) throws IOException {
        try {
            JSONObject o = new JSONObject(json);
            long codigo = o.getLong("versionCode");
            String nombre = o.optString("versionName", "");
            String apk = o.getString("apk");
            String sha = o.getString("sha256").trim().toLowerCase(java.util.Locale.ROOT);
            String notas = o.optString("novedades", "");
            if (codigo <= 0 || !apk.startsWith(PREFIJO_APK) || !apk.endsWith(".apk") || !sha.matches("[0-9a-f]{64}")) {
                throw new IOException("Datos de actualización no válidos");
            }
            return new Info(codigo, nombre, apk, sha, notas);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Datos de actualización no válidos: " + e.getMessage());
        }
    }

    static boolean esNueva(Info i, long actual) {
        return i != null && i.codigo > actual;
    }

    static Info consultar() throws IOException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(URL_VERSION + "?t=" + System.currentTimeMillis()).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            c.setUseCaches(false);
            if (c.getResponseCode() != 200) {
                throw new IOException("Respuesta " + c.getResponseCode());
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) > 0) {
                    bo.write(b, 0, n);
                    if (bo.size() > 65536) {
                        throw new IOException("Respuesta demasiado grande");
                    }
                }
                return analizar(new String(bo.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private static SharedPreferences e(Context c) {
        return c.getSharedPreferences("estado", Context.MODE_PRIVATE);
    }

    static void guardarPendiente(Context c, Info i) {
        e(c).edit().putLong("upd_codigo", i.codigo).putString("upd_nombre", i.nombre).putString("upd_apk", i.apk)
                .putString("upd_sha", i.sha).putString("upd_notas", i.novedades).apply();
    }

    static Info pendiente(Context c) {
        SharedPreferences p = e(c);
        long codigo = p.getLong("upd_codigo", 0);
        if (codigo <= BuildInfo.version(c)) {
            return null;
        }
        return new Info(codigo, p.getString("upd_nombre", ""), p.getString("upd_apk", ""), p.getString("upd_sha", ""), p.getString("upd_notas", ""));
    }

    static void recordarNovedades(Context c, Info i) {
        e(c).edit().putLong("nov_codigo", i.codigo).putString("nov_nombre", i.nombre).putString("nov_texto", i.novedades).apply();
    }

    static String[] novedadesPorMostrar(Context c) {
        SharedPreferences p = e(c);
        long codigo = p.getLong("nov_codigo", 0);
        if (codigo <= 0 || BuildInfo.version(c) < codigo) {
            return null;
        }
        return new String[]{p.getString("nov_nombre", ""), p.getString("nov_texto", "")};
    }

    static void novedadesVistas(Context c) {
        e(c).edit().remove("nov_codigo").remove("nov_nombre").remove("nov_texto").apply();
    }

    static void lanzarPantalla(Context c) {
        if (pantallaAbierta) {
            return;
        }
        Intent i = new Intent(c, ActualizarActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        c.startActivity(i);
    }

    static void comprobarAlAbrir(final Activity a) {
        final Context app = a.getApplicationContext();
        if (pendiente(app) != null) {
            lanzarPantalla(a);
            return;
        }
        long ahora = System.currentTimeMillis();
        if (consultando || ahora - ultimaConsulta < ENTRE_CONSULTAS_MS) {
            return;
        }
        consultando = true;
        ultimaConsulta = ahora;
        new Thread(() -> {
            try {
                Info i = consultar();
                if (esNueva(i, BuildInfo.version(app))) {
                    guardarPendiente(app, i);
                    a.runOnUiThread(() -> {
                        if (!a.isFinishing() && !a.isDestroyed()) {
                            lanzarPantalla(a);
                        }
                    });
                }
            } catch (Throwable t) {
                ultimaConsulta = 0;
            } finally {
                consultando = false;
            }
        }, "buscar-actualizacion").start();
    }

    static void programar(Context c) {
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null || js.getPendingJob(ID_TAREA) != null) {
                return;
            }
            JobInfo j = new JobInfo.Builder(ID_TAREA, new ComponentName(c, ComprobarJob.class))
                    .setPeriodic(24L * 60L * 60L * 1000L)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build();
            js.schedule(j);
        } catch (Throwable ignored) {
        }
    }

    static void avisar(Context c, Info i) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(CANAL, "Actualizaciones", NotificationManager.IMPORTANCE_DEFAULT));
            Intent abrir = new Intent(c, ActualizarActivity.class);
            abrir.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, 2, abrir, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            nm.notify(ID_AVISO, new android.app.Notification.Builder(c, CANAL)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("Nueva actualización disponible")
                    .setContentText("Ivona TTS " + i.nombre)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build());
        } catch (Throwable ignored) {
        }
    }
}
