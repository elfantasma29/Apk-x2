package com.ivona.tts;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

final class Reproductor {

    private final AudioTrack pista;
    private final int frecuencia;
    private long marcos = 0;
    private volatile boolean parado = false;

    Reproductor(int frecuencia, float volumen) {
        this.frecuencia = frecuencia;
        int min = AudioTrack.getMinBufferSize(frecuencia, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int tam = Math.max(min * 2, frecuencia / 4 * 2);
        AudioAttributes a = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        AudioFormat f = new AudioFormat.Builder()
                .setSampleRate(frecuencia)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();
        pista = new AudioTrack.Builder()
                .setAudioAttributes(a)
                .setAudioFormat(f)
                .setBufferSizeInBytes(tam)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        pista.setVolume(Math.max(0f, Math.min(1f, volumen)));
        pista.play();
    }

    boolean escribir(byte[] datos, int desde, int largo) {
        int pos = desde;
        int resto = largo;
        while (resto > 0 && !parado) {
            int n = pista.write(datos, pos, resto);
            if (n < 0) {
                return false;
            }
            pos += n;
            resto -= n;
            marcos += n / 2;
        }
        return !parado;
    }

    void parar() {
        parado = true;
        try {
            pista.pause();
            pista.flush();
        } catch (Throwable ignored) {
        }
    }

    void terminar() {
        try {
            if (!parado) {
                pista.stop();
                long limite = System.currentTimeMillis() + marcos * 1000L / frecuencia + 800;
                while (!parado && System.currentTimeMillis() < limite && pista.getPlaybackHeadPosition() < marcos) {
                    try {
                        Thread.sleep(8);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                pista.release();
            } catch (Throwable ignored) {
            }
        }
    }
}
