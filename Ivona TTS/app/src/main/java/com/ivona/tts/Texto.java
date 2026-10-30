package com.ivona.tts;

import java.util.Locale;

final class Texto {

    private Texto() {
    }

    static String avance(long hecho, long total) {
        if (total > 0) {
            int pct = (int) Math.min(100, hecho * 100 / total);
            return String.format(Locale.ROOT, "%d por ciento, %d de %d megas", pct, hecho / 1048576, total / 1048576);
        }
        return String.format(Locale.ROOT, "%d megas", hecho / 1048576);
    }
}
