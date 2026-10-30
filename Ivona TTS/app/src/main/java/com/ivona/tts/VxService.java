package com.ivona.tts;

import android.media.AudioFormat;
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
        if (!Voces.esEspanol(lang)) {
            return TextToSpeech.LANG_NOT_SUPPORTED;
        }
        List<Voces.Voz> inst = Voces.instaladas(this);
        if (inst.isEmpty()) {
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
            final Voces.Voz v = Voces.elegir(this, lang, country, null);
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
        Motor m = Motor.get(this);
        m.paro = true;
        m.detener();
    }

    @Override
    protected void onSynthesizeText(SynthesisRequest r, final SynthesisCallback cb) {
        final Motor m = Motor.get(this);
        m.paro = false;
        String texto = r.getCharSequenceText() == null ? "" : r.getCharSequenceText().toString();
        texto = texto.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").trim();
        Voces.Voz v = Voces.elegir(this, r.getLanguage(), r.getCountry(), r.getVoiceName());
        if (v == null) {
            cb.error();
            return;
        }
        if (texto.isEmpty()) {
            cb.start(22050, AudioFormat.ENCODING_PCM_16BIT, 1);
            cb.done();
            return;
        }
        String err = m.asegurar(v);
        if (err != null) {
            cb.error();
            return;
        }
        final boolean[] iniciado = {false};
        final boolean[] fallo = {false};
        int codigo = m.hablar(texto, new Servidor.Salida() {
            @Override
            public void inicio(int frecuencia) {
                if (!iniciado[0]) {
                    iniciado[0] = true;
                    cb.start(frecuencia, AudioFormat.ENCODING_PCM_16BIT, 1);
                }
            }

            @Override
            public void audio(byte[] datos, int largo) {
                if (!iniciado[0]) {
                    iniciado[0] = true;
                    cb.start(22050, AudioFormat.ENCODING_PCM_16BIT, 1);
                }
                int max = Math.max(cb.getMaxBufferSize(), 1024);
                int pos = 0;
                while (pos < largo) {
                    int n = Math.min(max, largo - pos);
                    if (cb.audioAvailable(datos, pos, n) != TextToSpeech.SUCCESS) {
                        fallo[0] = true;
                        m.paro = true;
                        return;
                    }
                    pos += n;
                }
            }

            @Override
            public boolean cancelado() {
                return m.paro;
            }
        });
        if (m.paro) {
            if (iniciado[0]) {
                cb.done();
            } else {
                cb.error();
            }
            return;
        }
        if (codigo != 0 || !iniciado[0] || fallo[0]) {
            cb.error();
        } else {
            cb.done();
        }
    }
}
