package com.ivona.tts;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class Medicion {

    private static volatile int estado = -1;

    private Medicion() {
    }

    static final class Muestra {
        long hora;
        int caracteres;
        int trozos = 1;
        boolean rapido;
        boolean paradaPorCliente;
        long motorMs;
        long primerMotorMs;
        long primerSonidoMs;
        long totalMs;
        double audioMs;
    }

    private static File archivo(Context c) {
        return new File(c.getFilesDir(), "medicion.txt");
    }

    static boolean activa(Context c) {
        if (estado < 0) {
            estado = c.getSharedPreferences("estado", Context.MODE_PRIVATE).getBoolean("medicion_activa", false) ? 1 : 0;
        }
        return estado == 1;
    }

    static synchronized void iniciar(Context c) {
        archivo(c).delete();
        estado = 1;
        c.getSharedPreferences("estado", Context.MODE_PRIVATE).edit()
                .putBoolean("medicion_activa", true)
                .putLong("medicion_inicio", System.currentTimeMillis()).apply();
    }

    static synchronized void registrar(Context c, Muestra m) {
        if (!activa(c)) {
            return;
        }
        String l = String.format(Locale.US, "%s;%d;%d;%s;%s;%d;%d;%d;%d;%.0f;%.2f",
                new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(m.hora)),
                m.caracteres, m.trozos, m.rapido ? "si" : "no", m.paradaPorCliente ? "si" : "no",
                m.motorMs, m.primerMotorMs, m.primerSonidoMs, m.totalMs, m.audioMs,
                m.audioMs > 0 ? m.totalMs / m.audioMs : 0.0);
        File f = archivo(c);
        if (f.length() > 4000000) {
            return;
        }
        try (FileOutputStream o = new FileOutputStream(f, true)) {
            o.write((l + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }

    static String estadoTexto(Context c) {
        if (!activa(c)) {
            return "Detenida";
        }
        int n = 0;
        File f = archivo(c);
        if (f.exists()) {
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f))) {
                while (r.readLine() != null) {
                    n++;
                }
            } catch (Throwable ignored) {
            }
        }
        return "En marcha, " + n + (n == 1 ? " frase medida" : " frases medidas");
    }

    private static double percentil(List<Double> v, double p) {
        if (v.isEmpty()) {
            return 0;
        }
        Collections.sort(v);
        int i = (int) Math.min(v.size() - 1, Math.round(p * (v.size() - 1)));
        return v.get(i);
    }

    static synchronized String[] detener(Context c) throws Exception {
        android.content.SharedPreferences e = c.getSharedPreferences("estado", Context.MODE_PRIVATE);
        long inicio = e.getLong("medicion_inicio", System.currentTimeMillis());
        estado = 0;
        e.edit().putBoolean("medicion_activa", false).apply();
        List<String> lineas = new ArrayList<>();
        File f = archivo(c);
        if (f.exists()) {
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(f), StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) {
                    lineas.add(l);
                }
            }
        }
        f.delete();
        List<Double> sonido = new ArrayList<>();
        List<Double> motor = new ArrayList<>();
        List<Double> ritmo = new ArrayList<>();
        List<Double> sonidoRapido = new ArrayList<>();
        List<Double> sonidoNormal = new ArrayList<>();
        int paradas = 0;
        for (String l : lineas) {
            String[] p = l.split(";");
            if (p.length < 11) {
                continue;
            }
            double s = Double.parseDouble(p[7]);
            sonido.add(s);
            motor.add(Double.parseDouble(p[6]));
            if ("si".equals(p[4])) {
                paradas++;
                continue;
            }
            ritmo.add(Double.parseDouble(p[10]));
            if ("si".equals(p[3])) {
                sonidoRapido.add(s);
            } else {
                sonidoNormal.add(s);
            }
        }
        android.content.SharedPreferences a = Ajustes.p(c);
        StringBuilder sb = new StringBuilder();
        sb.append("Medición de latencia de Ivona TTS\n");
        sb.append("Versión: ").append(BuildInfo.nombreVersion(c)).append('\n');
        sb.append("Dispositivo: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(", Android ").append(Build.VERSION.RELEASE).append('\n');
        sb.append("Inicio: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(inicio))).append('\n');
        sb.append("Fin: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append('\n');
        sb.append("Aumentar respuesta del motor ahora: ").append(a.getBoolean("respuesta_rapida", false) ? "activado" : "desactivado").append('\n');
        sb.append("Evitar cortes en voces pesadas: ").append(a.getBoolean("reserva_audio", true) ? "activado" : "desactivado").append('\n');
        sb.append("Volumen multimedia: ").append(Ajustes.modoMultimedia(a)).append('\n');
        String pr = Motor.get(c).prioridadResultado();
        if (pr.length() > 0) {
            sb.append(pr).append('\n');
        }
        sb.append("\nResumen\n");
        sb.append("Frases medidas: ").append(lineas.size()).append(" (canceladas por el lector: ").append(paradas).append(")\n");
        if (!sonido.isEmpty()) {
            double suma = 0;
            for (double d : sonido) {
                suma += d;
            }
            sb.append(String.format(Locale.US, "Tiempo hasta empezar a sonar: media %.0f ms, mediana %.0f ms, 90%% de las frases por debajo de %.0f ms, máximo %.0f ms\n",
                    suma / sonido.size(), percentil(new ArrayList<>(sonido), 0.5), percentil(new ArrayList<>(sonido), 0.9), percentil(new ArrayList<>(sonido), 1.0)));
            double sm = 0;
            for (double d : motor) {
                sm += d;
            }
            sb.append(String.format(Locale.US, "Tiempo hasta el primer audio del motor: media %.0f ms, mediana %.0f ms\n", sm / motor.size(), percentil(new ArrayList<>(motor), 0.5)));
        }
        if (!ritmo.isEmpty()) {
            double sr = 0;
            for (double d : ritmo) {
                sr += d;
            }
            sb.append(String.format(Locale.US, "Ritmo del motor (tiempo de síntesis dividido entre duración del audio; menos de 1 es más rápido que la voz): media %.2f, mediana %.2f\n", sr / ritmo.size(), percentil(new ArrayList<>(ritmo), 0.5)));
        }
        if (!sonidoRapido.isEmpty() && !sonidoNormal.isEmpty()) {
            sb.append(String.format(Locale.US, "Mediana hasta sonar con la capa activa: %.0f ms; sin la capa: %.0f ms\n", percentil(new ArrayList<>(sonidoRapido), 0.5), percentil(new ArrayList<>(sonidoNormal), 0.5)));
        }
        sb.append("\nColumnas de cada frase, todo en milisegundos salvo lo indicado:\n");
        sb.append("hora;caracteres;trozos;capa_activa;cancelada;arranque_motor;primer_audio_motor;hasta_sonar;total;duracion_audio;ritmo\n");
        for (String l : lineas) {
            sb.append(l).append('\n');
        }
        sb.append("\nNo se guarda el texto leído, solo su longitud.\n");
        String nombre = "IvonaTTS_medicion_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".txt";
        Ajustes.guardarTexto(c, nombre, "text/plain", sb.toString());
        String resumen = lineas.size() + " frases. " + (sonido.isEmpty() ? "" : String.format(Locale.US, "Mediana hasta sonar: %.0f ms.", percentil(new ArrayList<>(sonido), 0.5)));
        return new String[]{nombre, resumen};
    }
}
