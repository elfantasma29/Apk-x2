package com.ivona.tts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class ImportarDiccionarioActivity extends Activity {

    private static final int MAX_PALABRAS = 20000;
    private static final int MAX_CARACTERES = 4000000;

    private AlertDialog dialogo;
    private boolean ocupado;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Uri uri = null;
        String texto = null;
        Intent i = getIntent();
        if (i != null) {
            if (Intent.ACTION_SEND.equals(i.getAction())) {
                uri = flujo(i);
                if (uri == null) {
                    CharSequence t = i.getCharSequenceExtra(Intent.EXTRA_TEXT);
                    if (t != null) {
                        texto = t.toString();
                    }
                }
            } else {
                uri = i.getData();
                if (uri == null) {
                    uri = primeraUri(i);
                }
            }
        }
        if (uri == null && (texto == null || texto.trim().length() == 0)) {
            mostrar("No recibí ningún archivo para importar.", true);
            return;
        }
        leer(uri, texto);
    }

    private Uri flujo(Intent i) {
        Uri u = null;
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                u = i.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            } else {
                Object o = i.getParcelableExtra(Intent.EXTRA_STREAM);
                if (o instanceof Uri) {
                    u = (Uri) o;
                }
            }
        } catch (Throwable ignored) {
        }
        return u != null ? u : primeraUri(i);
    }

    private Uri primeraUri(Intent i) {
        try {
            ClipData c = i.getClipData();
            if (c != null) {
                for (int k = 0; k < c.getItemCount(); k++) {
                    Uri u = c.getItemAt(k).getUri();
                    if (u != null) {
                        return u;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void leer(final Uri uri, final String texto) {
        ocupado = true;
        new Thread(() -> {
            final ArrayList<String[]> nuevas = new ArrayList<>();
            String falla = null;
            try {
                BufferedReader lector;
                if (uri != null) {
                    InputStream in = getContentResolver().openInputStream(uri);
                    if (in == null) {
                        throw new IOException("No se pudo abrir el archivo");
                    }
                    lector = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                } else {
                    lector = new BufferedReader(new StringReader(texto));
                }
                try (BufferedReader r = lector) {
                    String linea;
                    int total = 0;
                    while ((linea = r.readLine()) != null) {
                        total += linea.length() + 1;
                        if (total > MAX_CARACTERES) {
                            throw new IllegalArgumentException("El archivo es demasiado grande para ser un diccionario");
                        }
                        String[] par = Texto.separar(linea);
                        if (par != null) {
                            nuevas.add(par);
                            if (nuevas.size() > MAX_PALABRAS) {
                                throw new IllegalArgumentException("El diccionario tiene más de " + MAX_PALABRAS + " palabras, es demasiado grande para importarlo");
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                falla = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                try {
                    Motor.get(this).guardarFallo("leer diccionario compartido", t);
                } catch (Throwable ignored) {
                }
            }
            final String error = falla;
            runOnUiThread(() -> {
                ocupado = false;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (error != null) {
                    mostrar("No se pudo leer el archivo: " + error, true);
                } else if (nuevas.isEmpty()) {
                    mostrar("Ese archivo no parece un diccionario de Ivona TTS, así que no se importó nada. Cada línea debe tener el formato original=reemplazo.", true);
                } else {
                    preguntar(nuevas);
                }
            });
        }, "leer-diccionario").start();
    }

    private void preguntar(final ArrayList<String[]> nuevas) {
        int n = nuevas.size();
        String cuantas = n == 1 ? "Tiene 1 palabra." : "Tiene " + n + " palabras.";
        cerrarDialogo();
        dialogo = new AlertDialog.Builder(this)
                .setTitle("Importar diccionario")
                .setMessage("¿Quieres importar este diccionario? " + cuantas + " Si alguna palabra ya está en tu diccionario, se reemplazará por la del archivo.")
                .setPositiveButton("Importar diccionario", (d, w) -> importar(nuevas))
                .setNegativeButton("Cancelar", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void importar(final ArrayList<String[]> nuevas) {
        ocupado = true;
        new Thread(() -> {
            int hechas = 0;
            String falla = null;
            try {
                hechas = Texto.combinar(this, nuevas);
            } catch (Throwable t) {
                falla = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                try {
                    Motor.get(this).guardarFallo("importar diccionario compartido", t);
                } catch (Throwable ignored) {
                }
            }
            final int n = hechas;
            final String error = falla;
            runOnUiThread(() -> {
                ocupado = false;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (error != null) {
                    mostrar("No se pudo importar el diccionario: " + error, true);
                } else {
                    mostrar(n == 1 ? "Listo. Se importó 1 palabra al diccionario." : "Listo. Se importaron " + n + " palabras al diccionario.", true);
                }
            });
        }, "importar-diccionario").start();
    }

    private void mostrar(String msg, boolean cerrar) {
        cerrarDialogo();
        dialogo = new AlertDialog.Builder(this)
                .setTitle("Ivona TTS")
                .setMessage(msg)
                .setPositiveButton("Aceptar", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void cerrarDialogo() {
        if (dialogo != null && dialogo.isShowing()) {
            try {
                dialogo.dismiss();
            } catch (Throwable ignored) {
            }
        }
        dialogo = null;
    }

    @Override
    protected void onDestroy() {
        cerrarDialogo();
        super.onDestroy();
    }
}
