package com.ivona.tts;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class Ajustes {

    static final String[] VOZ = {"default_volume", "default_rate", "default_pitch"};
    static final String[] MEJORAS = {};
    static final String[] AUDIO = {"salida_multimedia", "modo_compatible", "reserva_audio"};
    static final String[] LECTURA = {"default_log_text", "default_emoji_reading", "emoji_modo", "agrupar_desde", "lectura_inteligente", "numeros_modo", "lectura_enlaces"};

    static final String FORMATO = "Ivona TTS";

    private Ajustes() {
    }

    static SharedPreferences p(Context c) {
        return c.getSharedPreferences("ajustes", Context.MODE_PRIVATE);
    }

    static void restablecer(Context c, String[] claves) {
        SharedPreferences.Editor e = p(c).edit();
        for (String k : claves) {
            e.remove(k);
        }
        e.apply();
    }

    static String modoMultimedia(SharedPreferences p) {
        String m = p.getString("salida_multimedia", "no");
        return "apps".equals(m) || "todo".equals(m) ? m : "no";
    }

    static String exportar(Context c) throws Exception {
        return aJson(p(c).getAll(), Texto.lista(c), BuildInfo.nombreVersion(c));
    }

    static String aJson(Map<String, ?> todo, List<String[]> dic, String version) throws Exception {
        JSONObject raiz = new JSONObject();
        raiz.put("aplicacion", FORMATO);
        raiz.put("formato", 1);
        raiz.put("version", version);
        raiz.put("fecha", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        JSONArray lista = new JSONArray();
        for (Map.Entry<String, ?> e : todo.entrySet()) {
            Object v = e.getValue();
            String t;
            if (v instanceof Boolean) {
                t = "b";
            } else if (v instanceof Float) {
                t = "f";
            } else if (v instanceof Integer) {
                t = "i";
            } else if (v instanceof Long) {
                t = "l";
            } else if (v instanceof String) {
                t = "s";
            } else {
                continue;
            }
            JSONObject o = new JSONObject();
            o.put("k", e.getKey());
            o.put("t", t);
            o.put("v", v instanceof Float ? Double.parseDouble(v.toString()) : v);
            lista.put(o);
        }
        raiz.put("ajustes", lista);
        JSONArray d = new JSONArray();
        for (String[] par : dic) {
            JSONObject o = new JSONObject();
            o.put("a", par[0]);
            o.put("b", par[1]);
            d.put(o);
        }
        raiz.put("diccionario", d);
        return raiz.toString(2);
    }

    static final class Copia {
        final String fecha;
        final String version;
        final int ajustes;
        final int palabras;
        final JSONObject datos;

        Copia(JSONObject datos) {
            this.datos = datos;
            this.fecha = datos.optString("fecha", "");
            this.version = datos.optString("version", "");
            this.ajustes = datos.optJSONArray("ajustes") == null ? 0 : datos.optJSONArray("ajustes").length();
            this.palabras = datos.optJSONArray("diccionario") == null ? 0 : datos.optJSONArray("diccionario").length();
        }
    }

    static Copia leer(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        String app = o.optString("aplicacion");
        if (!FORMATO.equals(app) || o.optInt("formato", 0) < 1 || o.optInt("formato", 0) > 1) {
            throw new IllegalArgumentException("Este archivo no es una copia de seguridad de Ivona TTS");
        }
        if (o.optJSONArray("ajustes") == null) {
            throw new IllegalArgumentException("La copia no contiene ajustes");
        }
        return new Copia(o);
    }

    static void aplicar(Context c, Copia copia) throws Exception {
        SharedPreferences.Editor e = p(c).edit();
        List<String[]> pares = volcar(copia, e);
        if (!e.commit()) {
            throw new IllegalStateException("No se pudieron guardar los ajustes");
        }
        Texto.guardar(c, pares);
    }

    static List<String[]> volcar(Copia copia, SharedPreferences.Editor e) throws Exception {
        JSONArray lista = copia.datos.getJSONArray("ajustes");
        e.clear();
        for (int i = 0; i < lista.length(); i++) {
            JSONObject o = lista.getJSONObject(i);
            String k = o.getString("k");
            String t = o.getString("t");
            if ("b".equals(t)) {
                e.putBoolean(k, o.getBoolean("v"));
            } else if ("f".equals(t)) {
                e.putFloat(k, (float) o.getDouble("v"));
            } else if ("i".equals(t)) {
                e.putInt(k, o.getInt("v"));
            } else if ("l".equals(t)) {
                e.putLong(k, o.getLong("v"));
            } else if ("s".equals(t)) {
                e.putString(k, o.getString("v"));
            }
        }
        JSONArray dic = copia.datos.optJSONArray("diccionario");
        List<String[]> pares = new ArrayList<>();
        if (dic != null) {
            for (int i = 0; i < dic.length(); i++) {
                JSONObject o = dic.getJSONObject(i);
                pares.add(new String[]{o.getString("a"), o.optString("b", "")});
            }
        }
        return pares;
    }

    static String leerTexto(Context c, Uri uri) throws Exception {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IllegalStateException("No se pudo abrir el archivo");
            }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            long total = 0;
            while ((n = in.read(b)) > 0) {
                total += n;
                if (total > 8L * 1024 * 1024) {
                    throw new IllegalArgumentException("El archivo es demasiado grande para ser una copia de seguridad");
                }
                bo.write(b, 0, n);
            }
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static String guardarEnDescargas(Context c, String json) throws Exception {
        String nombre = "IvonaTTS_copia_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".json";
        ContentResolver cr = c.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, nombre);
        v.put(MediaStore.Downloads.MIME_TYPE, "application/json");
        v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        v.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri u = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (u == null) {
            throw new IllegalStateException("No se pudo crear el archivo en Descargas");
        }
        try (OutputStream o = cr.openOutputStream(u)) {
            if (o == null) {
                throw new IllegalStateException("No se pudo escribir en Descargas");
            }
            o.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            try {
                cr.delete(u, null, null);
            } catch (Throwable ignored) {
            }
            throw t;
        }
        ContentValues fin = new ContentValues();
        fin.put(MediaStore.Downloads.IS_PENDING, 0);
        cr.update(u, fin, null, null);
        return nombre;
    }
}
