package com.ivona.tts;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityManager;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.speech.tts.Voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class VxService extends TextToSpeechService {

    private static final int FRECUENCIA = 22050;
    private static final long REPOSO_MS = 10 * 60 * 1000L;
    private static final long REPOSO_BAJO_MS = 3 * 60 * 1000L;
    private static final Handler REPOSO = new Handler(Looper.getMainLooper());
    private static volatile Context contextoApp;
    private static volatile int activos = 0;
    private static final Runnable APAGAR = () -> {
        final Context c = contextoApp;
        if (c == null || activos > 0) {
            return;
        }
        new Thread(() -> {
            if (activos == 0) {
                Motor.get(c).detenerProceso();
            }
        }, "ivona-reposo").start();
    };

    private volatile boolean paradoPorCliente = false;
    private volatile Reproductor reproductor;

    private Motor m() {
        return Motor.get(this);
    }

    private SharedPreferences prefs() {
        return Ajustes.p(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        contextoApp = getApplicationContext();
        activos++;
        REPOSO.removeCallbacks(APAGAR);
        try {
            Actualizador.programar(this);
        } catch (Throwable t) {
            m().guardarFallo("programar actualizaciones", t);
        }
        calentar();
    }

    @Override
    public void onDestroy() {
        activos = Math.max(0, activos - 1);
        REPOSO.removeCallbacks(APAGAR);
        REPOSO.postDelayed(APAGAR, Motor.bajaMemoria(this) ? REPOSO_BAJO_MS : REPOSO_MS);
        super.onDestroy();
    }

    private void calentar() {
        try {
            if (m().vivo()) {
                return;
            }
            final Voces.Voz v = Voces.elegir(this, "spa", Locale.getDefault().getISO3Country(), null);
            if (v != null) {
                new Thread(() -> m().asegurar(v), "ivona-precarga").start();
            }
        } catch (Throwable t) {
            m().guardarFallo("precarga", t);
        }
    }

    private static String idiomaTelefono() {
        try {
            return Locale.getDefault().getISO3Language();
        } catch (Throwable t) {
            return "";
        }
    }

    @Override
    protected String[] onGetLanguage() {
        List<Voces.Voz> inst = Voces.instaladas(this);
        if (inst.isEmpty()) {
            return new String[]{"spa", "ESP", ""};
        }
        return new String[]{"spa", inst.get(0).pais3, ""};
    }

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        List<Voces.Voz> inst = Voces.instaladas(this);
        if (inst.isEmpty()) {
            return TextToSpeech.LANG_NOT_SUPPORTED;
        }
        if (!Voces.esEspanol(lang)) {
            if (prefs().getBoolean("modo_compatible", true) && lang != null && lang.equalsIgnoreCase(idiomaTelefono())) {
                return TextToSpeech.LANG_AVAILABLE;
            }
            return TextToSpeech.LANG_NOT_SUPPORTED;
        }
        for (Voces.Voz v : inst) {
            if (Voces.mismoPais(v, country)) {
                return TextToSpeech.LANG_COUNTRY_AVAILABLE;
            }
        }
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        int r = onIsLanguageAvailable(lang, country, variant);
        if (r >= TextToSpeech.LANG_AVAILABLE) {
            final Voces.Voz v = Voces.elegir(this, "spa", country, null);
            if (v != null) {
                new Thread(() -> Motor.get(this).asegurar(v), "ivona-precarga").start();
            }
        }
        return r;
    }

    @Override
    public List<Voice> onGetVoices() {
        ArrayList<Voice> l = new ArrayList<>();
        for (Voces.Voz v : Voces.instaladas(this)) {
            l.add(new Voice(v.nombreVoz(), new Locale("es", v.pais2), Voice.QUALITY_VERY_HIGH, Voice.LATENCY_NORMAL, false, Collections.<String>emptySet()));
        }
        return l;
    }

    @Override
    public String onGetDefaultVoiceNameFor(String lang, String country, String variant) {
        Voces.Voz v = Voces.elegir(this, lang, country, null);
        return v == null ? null : v.nombreVoz();
    }

    @Override
    public int onIsValidVoiceName(String name) {
        return Voces.elegir(this, null, null, name) != null && name != null && name.startsWith("ivona-") ? TextToSpeech.SUCCESS : TextToSpeech.ERROR;
    }

    @Override
    public int onLoadVoice(String name) {
        return onIsValidVoiceName(name);
    }

    @Override
    protected void onStop() {
        paradoPorCliente = true;
        Motor m = Motor.get(this);
        m.paro = true;
        Reproductor r = reproductor;
        if (r != null) {
            r.parar();
        }
        try {
            m.detener();
        } catch (Throwable t) {
            m.guardarFallo("onStop", t);
        }
    }

    private boolean esLectorDePantalla(SynthesisRequest request) {
        try {
            Bundle b = request.getParams();
            if (b != null && b.getBoolean("jieshuo", false)) {
                return true;
            }
            String[] paquetes = getPackageManager().getPackagesForUid(request.getCallerUid());
            if (paquetes == null) {
                return false;
            }
            AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
            for (AccessibilityServiceInfo i : am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                String sp = i.getResolveInfo().serviceInfo.packageName;
                for (String q : paquetes) {
                    if (q.equals(sp)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean usarMultimedia(SynthesisRequest request, SynthesisCallback callback, SharedPreferences p) {
        String modo = Ajustes.modoMultimedia(p);
        if ("no".equals(modo)) {
            return false;
        }
        if (!callback.getClass().getName().contains("Playback")) {
            return false;
        }
        if ("todo".equals(modo)) {
            return true;
        }
        return !esLectorDePantalla(request);
    }

    static String escapar(String t) {
        return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String envolver(String texto, Voces.Voz v, int vol, int vel, int tono) {
        StringBuilder a = new StringBuilder();
        if (vol < 100) {
            a.append(" volume=\"").append(Math.max(0, vol)).append('"');
        }
        if (vel != 100) {
            int d = Math.max(-90, Math.min(100, vel - 100));
            a.append(" rate=\"").append(d > 0 ? "+" : "").append(d).append("%\"");
        }
        if (tono > 100) {
            a.append(" pitch=\"+").append(Math.min(100, tono - 100)).append("%\"");
        }
        if (a.length() == 0) {
            return texto;
        }
        return "<speak version=\"1.0\" xml:lang=\"" + v.idioma() + "\"><prosody" + a + ">" + escapar(texto) + "</prosody></speak>";
    }

    private final class Flujo implements Servidor.Salida {
        final SynthesisCallback callback;
        final Reproductor rep;
        final int max;
        final boolean reserva;
        final double estimado;
        long bytes = 0;
        boolean iniciado = false;
        boolean fallo = false;
        private final java.util.ArrayList<byte[]> pendientes = new java.util.ArrayList<>();
        private double segPendientes = 0;
        private double segPrimero = 0;
        private long tPrimero = 0;
        private boolean liberado = false;

        Flujo(SynthesisCallback callback, Reproductor rep, int max, boolean reserva, int caracteres) {
            this.callback = callback;
            this.rep = rep;
            this.max = max;
            this.reserva = reserva;
            this.estimado = caracteres * 0.07;
        }

        private boolean salir(byte[] datos, int desde, int largo) {
            if (rep != null) {
                return rep.escribir(datos, desde, largo);
            }
            int pos = desde;
            int fin = desde + largo;
            while (pos < fin) {
                int n = Math.min(max, fin - pos);
                if (callback.audioAvailable(datos, pos, n) != TextToSpeech.SUCCESS) {
                    return false;
                }
                pos += n;
            }
            return true;
        }

        private boolean liberar() {
            liberado = true;
            boolean ok = true;
            for (byte[] b : pendientes) {
                if (ok) {
                    ok = salir(b, 0, b.length);
                }
            }
            pendientes.clear();
            return ok;
        }

        private boolean yaPuedeSonar() {
            if (segPendientes < 0.08) {
                return false;
            }
            double el = (System.nanoTime() - tPrimero) / 1e9;
            if (el < 0.05) {
                return false;
            }
            double ritmo = (segPendientes - segPrimero) / el;
            if (ritmo >= 1.5) {
                return true;
            }
            double necesario = estimado * (1 - Math.min(ritmo, 1.0)) + 0.35;
            return segPendientes >= Math.min(necesario, 2.0);
        }

        @Override
        public void inicio(int frecuencia) {
            iniciado = true;
        }

        @Override
        public void audio(byte[] datos, int largo) {
            iniciado = true;
            if (largo <= 0) {
                return;
            }
            bytes += largo;
            boolean ok;
            if (!reserva || liberado) {
                ok = salir(datos, 0, largo);
            } else {
                byte[] copia = new byte[largo];
                System.arraycopy(datos, 0, copia, 0, largo);
                double seg = largo / 2.0 / FRECUENCIA;
                if (pendientes.isEmpty()) {
                    tPrimero = System.nanoTime();
                    segPrimero = seg;
                }
                pendientes.add(copia);
                segPendientes += seg;
                ok = !yaPuedeSonar() || liberar();
            }
            if (!ok) {
                fallo = true;
                paradoPorCliente = true;
                Motor.get(VxService.this).paro = true;
            }
        }

        @Override
        public boolean cancelado() {
            return paradoPorCliente || Motor.get(VxService.this).paro;
        }

        void cerrar() {
            if (!liberado && !pendientes.isEmpty() && !paradoPorCliente) {
                liberar();
            }
            liberado = true;
        }
    }

    @Override
    protected void onSynthesizeText(SynthesisRequest r, SynthesisCallback cb) {
        try {
            sintetizar(r, cb);
        } catch (Throwable t) {
            m().guardarFallo("onSynthesizeText", t);
            try {
                cb.error();
            } catch (Throwable ignored) {
            }
        }
    }

    private void sintetizar(SynthesisRequest r, SynthesisCallback cb) {
        final Motor m = m();
        m.paro = false;
        paradoPorCliente = false;
        SharedPreferences p = prefs();
        CharSequence cs = r.getCharSequenceText();
        String texto = cs == null ? "" : cs.toString();
        texto = texto.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").trim();
        Voces.Voz v = Voces.elegir(this, r.getLanguage(), r.getCountry(), r.getVoiceName());
        if (v == null) {
            cb.error();
            return;
        }
        boolean sinDic = false;
        float volCliente = 1f;
        try {
            Bundle params = r.getParams();
            if (params != null) {
                sinDic = params.getBoolean("vx_sin_diccionario", false);
                volCliente = params.getFloat("volume", 1f);
            }
        } catch (Throwable ignored) {
        }
        texto = Texto.procesar(this, texto, p, v.pais2, sinDic).trim();
        if (texto.isEmpty()) {
            cb.start(FRECUENCIA, AudioFormat.ENCODING_PCM_16BIT, 1);
            cb.done();
            return;
        }
        if (p.getBoolean("default_log_text", false)) {
            m.log("Texto [" + v.nombre + "]: " + texto);
        }
        int vol = Math.max(0, Math.min(100, Math.round(p.getFloat("default_volume", 100f))));
        int vel = Math.max(10, Math.round(r.getSpeechRate() * p.getFloat("default_rate", 100f) / 100f));
        int tono = Math.max(0, Math.round(r.getPitch() * p.getFloat("default_pitch", 100f) / 100f));
        String envio = envolver(texto, v, vol, vel, tono);
        if (cb.start(FRECUENCIA, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) {
            return;
        }
        String err = m.asegurar(v);
        if (err != null) {
            m.log("Motor no disponible: " + err);
            cb.error();
            return;
        }
        final int max = Math.max(1024, cb.getMaxBufferSize());
        Reproductor rep = null;
        if (usarMultimedia(r, cb, p)) {
            try {
                rep = new Reproductor(FRECUENCIA, volCliente);
                reproductor = rep;
            } catch (Throwable t) {
                m.guardarFallo("volumen multimedia", t);
                rep = null;
            }
        }
        try {
            boolean reserva = p.getBoolean("reserva_audio", true);
            Flujo flujo = new Flujo(cb, rep, max, reserva, texto.length());
            int rc = m.hablar(envio, flujo);
            if (rc != 0 && flujo.bytes == 0 && !paradoPorCliente) {
                m.log("Síntesis con error " + rc + "; se reinicia el motor y se reintenta una vez");
                m.reiniciarSuave();
                String e2 = m.asegurar(v);
                if (e2 == null) {
                    flujo = new Flujo(cb, rep, max, reserva, texto.length());
                    rc = m.hablar(envio, flujo);
                }
            }
            if (paradoPorCliente) {
                flujo.cerrar();
                if (rep != null) {
                    rep.parar();
                }
                cb.done();
                return;
            }
            if (rc != 0 && flujo.bytes == 0) {
                m.log("Síntesis con error " + rc + " tras reintentar");
                cb.error();
                return;
            }
            flujo.cerrar();
            if (rep != null) {
                rep.terminar();
            }
            if (flujo.fallo) {
                cb.error();
            } else {
                cb.done();
            }
        } finally {
            if (rep != null) {
                rep.parar();
                reproductor = null;
            }
        }
    }
}
