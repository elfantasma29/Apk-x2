package com.ivona.tts;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class Voces {

    static final String BASE = "https://raw.githubusercontent.com/elfantasma29/Apk-x2/main/Ivona%20TTS/voces/";

    static final class Voz {
        final String clave;
        final String nombre;
        final String pais2;
        final String pais3;
        final String detalle;
        final int megas;
        final String archivo;
        final long[] partes;

        Voz(String clave, String nombre, String pais2, String pais3, String detalle, int megas, String archivo, long[] partes) {
            this.clave = clave;
            this.nombre = nombre;
            this.pais2 = pais2;
            this.pais3 = pais3;
            this.detalle = detalle;
            this.megas = megas;
            this.archivo = archivo;
            this.partes = partes;
        }

        long total() {
            long t = 0;
            for (long x : partes) {
                t += x;
            }
            return t;
        }

        String completo() {
            return nombre + ", " + detalle + ", " + megas + " MB";
        }

        String idioma() {
            return "es-" + pais2;
        }

        String nombreVoz() {
            return "ivona-" + clave;
        }
    }

    static final Voz[] TODAS = new Voz[]{
            new Voz("conchita", "Conchita", "ES", "ESP", "español de España", 109, "ivona_conchita.zip", new long[]{62914560L, 51533849L}),
            new Voz("penelope", "Penélope", "US", "USA", "español de Estados Unidos", 103, "ivona_penelope.zip", new long[]{62914560L, 44854961L})
    };

    private Voces() {
    }

    static File raiz(Context c) {
        File d = new File(c.getFilesDir(), "voces");
        d.mkdirs();
        return d;
    }

    static File carpeta(Context c, Voz v) {
        return new File(raiz(c), v.clave);
    }

    static File temporal(Context c) {
        File d = new File(c.getFilesDir(), "descargas");
        d.mkdirs();
        return d;
    }

    static boolean instalada(Context c, Voz v) {
        return new File(carpeta(c, v), ".listo").exists();
    }

    static List<Voz> instaladas(Context c) {
        ArrayList<Voz> l = new ArrayList<>();
        for (Voz v : TODAS) {
            if (instalada(c, v)) {
                l.add(v);
            }
        }
        return l;
    }

    static Voz porClave(String clave) {
        for (Voz v : TODAS) {
            if (v.clave.equals(clave)) {
                return v;
            }
        }
        return null;
    }

    static boolean esEspanol(String idioma) {
        return "spa".equalsIgnoreCase(idioma) || "es".equalsIgnoreCase(idioma);
    }

    static boolean mismoPais(Voz v, String pais) {
        return pais != null && (v.pais2.equalsIgnoreCase(pais) || v.pais3.equalsIgnoreCase(pais));
    }

    static Voz elegir(Context c, String idioma, String pais, String nombreVoz) {
        List<Voz> inst = instaladas(c);
        if (inst.isEmpty()) {
            return null;
        }
        if (nombreVoz != null) {
            for (Voz v : inst) {
                if (v.nombreVoz().equalsIgnoreCase(nombreVoz)) {
                    return v;
                }
            }
        }
        if (idioma != null && !idioma.isEmpty() && !esEspanol(idioma)) {
            return null;
        }
        for (Voz v : inst) {
            if (mismoPais(v, pais)) {
                return v;
            }
        }
        String pref = Ajustes.p(c).getString("voz_preferida", "");
        for (Voz v : inst) {
            if (v.clave.equals(pref)) {
                return v;
            }
        }
        return inst.get(0);
    }
}
