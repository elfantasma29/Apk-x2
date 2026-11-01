package com.ivona.tts;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class Avisos {

    static final String URL_MENSAJES = "https://raw.githubusercontent.com/elfantasma29/Apk-x2/main/Ivona%20TTS/mensajes.json";
    static final int MAXIMO = 5;
    private static final long ENTRE_CONSULTAS_MS = 2L * 60L * 1000L;
    private static volatile boolean consultando = false;
    private static volatile long ultimaConsulta = 0;

    private Avisos() {
    }

    static final class Aviso {
        final long id;
        final String fecha;
        final String titulo;
        final String texto;
        final boolean importante;

        Aviso(long id, String fecha, String titulo, String texto, boolean importante) {
            this.id = id;
            this.fecha = fecha;
            this.titulo = titulo;
            this.texto = texto;
            this.importante = importante;
        }
    }

    private static SharedPreferences e(Context c) {
        return c.getSharedPreferences("estado", Context.MODE_PRIVATE);
    }

    static List<Aviso> analizar(String json) throws IOException {
        try {
            JSONArray a = new JSONObject(json).getJSONArray("mensajes");
            List<Aviso> r = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                long id = o.getLong("id");
                String texto = o.getString("texto").trim();
                if (id <= 0 || texto.isEmpty()) {
                    continue;
                }
                r.add(new Aviso(id, o.optString("fecha", ""), o.optString("titulo", "").trim(), texto, o.optBoolean("importante", false)));
            }
            Collections.sort(r, (x, y) -> Long.compare(y.id, x.id));
            if (r.size() > MAXIMO) {
                r = new ArrayList<>(r.subList(0, MAXIMO));
            }
            return r;
        } catch (Exception ex) {
            throw new IOException("Avisos no válidos: " + ex.getMessage());
        }
    }

    private static String integrado(Context c) {
        try (InputStream in = c.getAssets().open("mensajes.json")) {
            return leer(in);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String leer(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) > 0) {
            bo.write(b, 0, n);
            if (bo.size() > 65536) {
                throw new IOException("Respuesta demasiado grande");
            }
        }
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    static List<Aviso> lista(Context c) {
        String[] fuentes = {e(c).getString("avisos_json", ""), integrado(c)};
        for (String f : fuentes) {
            if (f.isEmpty()) {
                continue;
            }
            try {
                return analizar(f);
            } catch (Throwable ignored) {
            }
        }
        return new ArrayList<>();
    }

    static long maximo(Context c) {
        List<Aviso> l = lista(c);
        return l.isEmpty() ? 0 : l.get(0).id;
    }

    static int nuevos(Context c) {
        long visto = e(c).getLong("avisos_visto", 0);
        int n = 0;
        for (Aviso a : lista(c)) {
            if (a.id > visto) {
                n++;
            }
        }
        return n;
    }

    static boolean hayParaNotificar(Context c) {
        return maximo(c) > e(c).getLong("avisos_notificado", 0);
    }

    static void notificado(Context c) {
        e(c).edit().putLong("avisos_notificado", maximo(c)).apply();
    }

    static void vistos(Context c, long hasta) {
        SharedPreferences p = e(c);
        long visto = Math.max(hasta, p.getLong("avisos_visto", 0));
        p.edit().putLong("avisos_visto", visto).putLong("avisos_notificado", Math.max(visto, p.getLong("avisos_notificado", 0))).apply();
    }

    static boolean consultar(Context c) {
        HttpURLConnection h = null;
        try {
            h = (HttpURLConnection) new URL(URL_MENSAJES + "?t=" + System.currentTimeMillis()).openConnection();
            h.setConnectTimeout(15000);
            h.setReadTimeout(15000);
            h.setUseCaches(false);
            if (h.getResponseCode() != 200) {
                return false;
            }
            String json;
            try (InputStream in = h.getInputStream()) {
                json = leer(in);
            }
            analizar(json);
            e(c).edit().putString("avisos_json", json).apply();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (h != null) {
                h.disconnect();
            }
        }
    }

    interface AlTerminar {
        void listo(boolean conexion);
    }

    static void consultarAsync(final Context c, boolean forzar, final AlTerminar al) {
        final Context app = c.getApplicationContext();
        long ahora = System.currentTimeMillis();
        if (consultando || (!forzar && ahora - ultimaConsulta < ENTRE_CONSULTAS_MS)) {
            if (al != null) {
                al.listo(true);
            }
            return;
        }
        consultando = true;
        ultimaConsulta = ahora;
        new Thread(() -> {
            boolean ok = false;
            try {
                ok = consultar(app);
                if (!ok) {
                    ultimaConsulta = 0;
                }
            } finally {
                consultando = false;
            }
            if (al != null) {
                al.listo(ok);
            }
        }, "buscar-avisos").start();
    }
}
