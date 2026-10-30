package com.ivona.tts;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Texto {

    private static final HashMap<String, String> EMOJI = new HashMap<>();
    private static int maxLen = 1;
    private static boolean emojiListo = false;

    private static final List<String[]> palabras = new ArrayList<>();
    private static final List<Pattern> patrones = new ArrayList<>();
    private static long marcaDic = -1;

    private Texto() {
    }

    static synchronized void cargar(Context c) {
        cargarEmoji(c);
        cargarDiccionario(c);
    }

    private static void cargarEmoji(Context c) {
        if (emojiListo) {
            return;
        }
        try (InputStream in = c.getAssets().open("emoji_es.txt");
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null) {
                int i = l.indexOf('=');
                if (i < 1) {
                    continue;
                }
                StringBuilder k = new StringBuilder();
                int n = 0;
                for (String h : l.substring(0, i).split("_")) {
                    k.appendCodePoint(Integer.parseInt(h, 16));
                    n++;
                }
                maxLen = Math.max(maxLen, n);
                EMOJI.put(k.toString(), l.substring(i + 1).trim());
            }
            emojiListo = true;
        } catch (Throwable t) {
            Motor.get(c).guardarFallo("cargar emoji", t);
        }
    }

    private static File archivoDic(Context c) {
        return new File(c.getFilesDir(), "diccionario.txt");
    }

    private static void cargarDiccionario(Context c) {
        File f = archivoDic(c);
        long marca = f.exists() ? f.lastModified() + f.length() : 0;
        if (marca == marcaDic) {
            return;
        }
        palabras.clear();
        patrones.clear();
        if (f.exists()) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(f), StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) {
                    String[] p = separar(l);
                    if (p != null) {
                        palabras.add(p);
                    }
                }
            } catch (Throwable t) {
                Motor.get(c).guardarFallo("cargar diccionario", t);
            }
        }
        List<String[]> orden = new ArrayList<>(palabras);
        Collections.sort(orden, (a, b) -> b[0].length() - a[0].length());
        List<String[]> ordenadas = new ArrayList<>();
        for (String[] p : orden) {
            try {
                patrones.add(Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(p[0]) + "(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
                ordenadas.add(p);
            } catch (Throwable ignored) {
            }
        }
        ordenPalabras = ordenadas;
        marcaDic = marca;
    }

    private static List<String[]> ordenPalabras = new ArrayList<>();

    static String[] separar(String l) {
        String s = (l.length() > 0 && l.charAt(0) == '﻿' ? l.substring(1) : l).trim();
        if (s.length() == 0 || s.startsWith("#")) {
            return null;
        }
        int i = s.indexOf('\t');
        if (i < 0) {
            i = s.indexOf('=');
        }
        if (i < 1) {
            return null;
        }
        String a = s.substring(0, i).trim();
        String b = s.substring(i + 1).trim();
        return a.length() == 0 ? null : new String[]{a, b};
    }

    static synchronized List<String[]> lista(Context c) {
        cargarDiccionario(c);
        return new ArrayList<>(palabras);
    }

    static synchronized boolean guardar(Context c, List<String[]> lista) {
        try (FileOutputStream o = new FileOutputStream(archivoDic(c))) {
            StringBuilder sb = new StringBuilder();
            for (String[] p : lista) {
                sb.append(p[0]).append('=').append(p[1]).append('\n');
            }
            o.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return false;
        }
        marcaDic = -1;
        cargarDiccionario(c);
        return true;
    }

    static synchronized int combinar(Context c, List<String[]> nuevas) {
        List<String[]> actual = lista(c);
        int agregadas = 0;
        for (String[] n : nuevas) {
            boolean reemplazada = false;
            for (int i = 0; i < actual.size(); i++) {
                if (actual.get(i)[0].equalsIgnoreCase(n[0])) {
                    actual.set(i, n);
                    reemplazada = true;
                    break;
                }
            }
            if (!reemplazada) {
                actual.add(n);
            }
            agregadas++;
        }
        guardar(c, actual);
        return agregadas;
    }

    static synchronized String aplicarDiccionario(Context c, String t) {
        cargarDiccionario(c);
        String r = t;
        for (int i = 0; i < patrones.size(); i++) {
            Matcher m = patrones.get(i).matcher(r);
            if (m.find()) {
                r = m.replaceAll(Matcher.quoteReplacement(ordenPalabras.get(i)[1]));
            }
        }
        return r;
    }

    private static boolean candidato(int[] cp, int i) {
        int c = cp[i];
        if (c >= 0x2100 || c == 0x203c || c == 0x2049) {
            return true;
        }
        if ((c >= '0' && c <= '9') || c == '#' || c == '*') {
            int j = i + 1;
            while (j < cp.length && (cp[j] == 0xfe0f || cp[j] == 0xfe0e)) {
                j++;
            }
            return j < cp.length && cp[j] == 0x20e3;
        }
        return false;
    }

    private static final String SIMB = "!¡?¿.,;:-_*#=+~/\\|@$%&^<>()\"'…";

    private static String nombreSimbolo(int c) {
        switch (c) {
            case '!': return "signos de exclamación";
            case '¡': return "signos de exclamación de apertura";
            case '?': return "signos de interrogación";
            case '¿': return "signos de interrogación de apertura";
            case '.': return "puntos";
            case ',': return "comas";
            case ';': return "puntos y coma";
            case ':': return "dos puntos";
            case '-': return "guiones";
            case '_': return "guiones bajos";
            case '*': return "asteriscos";
            case '#': return "almohadillas";
            case '=': return "signos de igual";
            case '+': return "signos de más";
            case '~': return "virgulillas";
            case '/': return "barras";
            case '\\': return "barras invertidas";
            case '|': return "barras verticales";
            case '@': return "arrobas";
            case '$': return "signos de pesos";
            case '%': return "signos de porcentaje";
            case '&': return "signos y";
            case '^': return "acentos circunflejos";
            case '<': return "signos de menor que";
            case '>': return "signos de mayor que";
            case '(': return "paréntesis de apertura";
            case ')': return "paréntesis de cierre";
            case '"': return "comillas";
            case '\'': return "apóstrofos";
            case '…': return "puntos suspensivos";
            default: return "signos";
        }
    }

    private static int unidadEmoji(int[] cp, int i, String[] nombre) {
        int n = cp.length;
        int[] fin = new int[maxLen];
        int[] largos = new int[maxLen];
        StringBuilder norm = new StringBuilder();
        int cuenta = 0;
        int j = i;
        while (j < n && cuenta < maxLen) {
            if (cp[j] != 0xfe0f && cp[j] != 0xfe0e) {
                norm.appendCodePoint(cp[j]);
                fin[cuenta] = j;
                largos[cuenta] = norm.length();
                cuenta++;
            }
            j++;
        }
        for (int l = cuenta; l >= 1; l--) {
            String v = EMOJI.get(norm.substring(0, largos[l - 1]));
            if (v != null) {
                nombre[0] = v;
                return fin[l - 1];
            }
        }
        return -1;
    }

    static synchronized String leerEmoji(Context c, String t) {
        return leerSimbolos(c, t, true, false, 0);
    }

    static synchronized String leerSimbolos(Context c, String t, boolean emoji, boolean agrupar, int umbral) {
        if (emoji) {
            cargarEmoji(c);
        }
        boolean usaEmoji = emoji && !EMOJI.isEmpty();
        if (!usaEmoji && !agrupar) {
            return t;
        }
        int[] cp = t.codePoints().toArray();
        StringBuilder out = new StringBuilder(t.length() + 16);
        int n = cp.length;
        int i = 0;
        String[] nom = new String[1];
        String[] nom2 = new String[1];
        while (i < n) {
            if (cp[i] == 0xfe0f || cp[i] == 0xfe0e) {
                i++;
                continue;
            }
            if (usaEmoji && candidato(cp, i)) {
                int hasta = unidadEmoji(cp, i, nom);
                if (hasta >= 0) {
                    String nombre = nom[0];
                    int fin = hasta;
                    int veces = 1;
                    if (agrupar) {
                        int j = hasta + 1;
                        while (true) {
                            int k = j;
                            while (k < n && (cp[k] == ' ' || cp[k] == 0xfe0f || cp[k] == 0xfe0e)) {
                                k++;
                            }
                            if (k >= n || !candidato(cp, k)) {
                                break;
                            }
                            int h2 = unidadEmoji(cp, k, nom2);
                            if (h2 < 0 || !nombre.equals(nom2[0])) {
                                break;
                            }
                            veces++;
                            fin = h2;
                            j = h2 + 1;
                        }
                    }
                    if (agrupar && veces >= umbral) {
                        out.append(" grupo de ").append(nombre).append(", ");
                        i = fin + 1;
                    } else {
                        out.append(' ').append(nombre).append(", ");
                        i = hasta + 1;
                    }
                    continue;
                }
            }
            if (agrupar && cp[i] < 0x2100 && SIMB.indexOf(cp[i]) >= 0) {
                int k = i;
                while (k < n && cp[k] == cp[i]) {
                    k++;
                }
                if (k - i >= umbral) {
                    out.append(" grupo de ").append(nombreSimbolo(cp[i])).append(", ");
                    i = k;
                    continue;
                }
                int m = i;
                while (m < n && cp[m] < 0x2100 && SIMB.indexOf(cp[m]) >= 0) {
                    m++;
                }
                if (m - i >= umbral) {
                    out.append(" grupo de signos, ");
                    i = m;
                    continue;
                }
            }
            out.appendCodePoint(cp[i]);
            i++;
        }
        return out.toString();
    }

    static String procesar(Context c, String texto, SharedPreferences p, String pais, boolean sinDiccionario) {
        String r = texto;
        if (!sinDiccionario && p.getBoolean("default_activate_dictionary", false)) {
            r = aplicarDiccionario(c, r);
        }
        Normal.Opc o = new Normal.Opc();
        o.inteligente = p.getBoolean("lectura_inteligente", false);
        String modo = p.getString("numeros_modo", "normal");
        o.digitos = "digito".equals(modo) ? 1 : "pares".equals(modo) ? 2 : 0;
        o.enlaces = p.getBoolean("lectura_enlaces", false);
        o.pais = pais;
        if (o.enlaces) {
            r = Normal.enlaces(r, o);
        }
        if (o.inteligente) {
            r = Normal.inteligente(r, o);
        }
        if (o.digitos != 0) {
            r = Normal.digitos(r, o);
        }
        boolean emoji = p.getBoolean("default_emoji_reading", false);
        boolean agrupar = "agrupacion".equals(p.getString("emoji_modo", "normal"));
        int umbral = 5;
        try {
            umbral = Math.max(2, Math.min(10, Integer.parseInt(p.getString("agrupar_desde", "5"))));
        } catch (NumberFormatException ignored) {
        }
        if (emoji || agrupar) {
            r = leerSimbolos(c, r, emoji, agrupar, umbral);
        }
        return r;
    }
}
