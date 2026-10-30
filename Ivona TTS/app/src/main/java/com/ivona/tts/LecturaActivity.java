package com.ivona.tts;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LecturaActivity extends Pantalla {

    private SharedPreferences p;
    private Fila filaLista;
    private TextToSpeech oyente;
    private boolean oyenteListo = false;
    private String oyentePendiente = null;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = Ajustes.p(this);
        construir();
    }

    @Override
    protected void onDestroy() {
        if (oyente != null) {
            oyente.shutdown();
            oyente = null;
        }
        super.onDestroy();
    }

    private void lista(String titulo, String dialogo, String clave, String def, String[] valores, String[] textos) {
        String actual = p.getString(clave, def);
        int idx = 0;
        for (int i = 0; i < valores.length; i++) {
            if (valores[i].equals(actual)) {
                idx = i;
            }
        }
        Fila f = filaAbierta(titulo, textos[idx]);
        f.vista.setOnClickListener(v -> {
            String a = p.getString(clave, def);
            int m = 0;
            for (int i = 0; i < valores.length; i++) {
                if (valores[i].equals(a)) {
                    m = i;
                }
            }
            dialogoLista(dialogo, textos, m, x -> {
                int k = (int) x;
                p.edit().putString(clave, valores[k]).apply();
                f.resumen(textos[k]);
            });
        });
    }

    private void construir() {
        armar("Ajustes de texto de lectura", "Cómo se lee lo que escribes");

        seccion("Lectura");
        final Fila[] registro = new Fila[1];
        registro[0] = interruptor("¿Registrar el texto pronunciado?", p.getBoolean("default_log_text", false) ? "Registro de texto activado" : "Registro de texto desactivado", p.getBoolean("default_log_text", false), v -> {
            p.edit().putBoolean("default_log_text", v).apply();
            if (registro[0] != null) {
                registro[0].resumen(v ? "Registro de texto activado" : "Registro de texto desactivado");
            }
        });
        interruptor("Lectura de emoji", p.getBoolean("default_emoji_reading", false) ? "Los emoji se leen por su nombre en español" : "La opción de lectura viene desactivado por defecto", p.getBoolean("default_emoji_reading", false), v -> p.edit().putBoolean("default_emoji_reading", v).apply());
        lista("Lectura de emoji y símbolos repetidos", "Lectura de emoji y símbolos repetidos", "emoji_modo", "normal",
                new String[]{"normal", "agrupacion"},
                new String[]{"Normal: leer cada uno", "Por agrupación: leer el grupo una sola vez"});
        String[] veces = new String[9];
        String[] vecesTxt = new String[9];
        for (int i = 0; i < 9; i++) {
            veces[i] = String.valueOf(i + 2);
            vecesTxt[i] = (i + 2) + " repeticiones";
        }
        lista("Agrupar a partir de", "Agrupar a partir de cuántas repeticiones", "agrupar_desde", "5", veces, vecesTxt);
        interruptor("Lectura inteligente de números, fechas y monedas", p.getBoolean("lectura_inteligente", false)
                ? "Activada. Las fechas, las monedas con su código y las cifras con separadores se leen de forma natural"
                : "Desactivada. Los números los lee el motor tal cual", p.getBoolean("lectura_inteligente", false), v -> {
            p.edit().putBoolean("lectura_inteligente", v).apply();
            construir();
        });
        lista("Lectura de números", "Lectura de números", "numeros_modo", "normal",
                new String[]{"normal", "digito", "pares"},
                new String[]{"Normal: como lo decida el motor", "Dígito por dígito: 56 se lee cinco seis", "De dos en dos: 5632 se lee cincuenta y seis, treinta y dos"});
        interruptor("Lectura de enlaces, correos y hashtags", p.getBoolean("lectura_enlaces", false)
                ? "Activada. Un enlace de YouTube se lee enlace a YouTube; los correos y los hashtags se leen completos"
                : "Desactivada. Se leen como cualquier otro texto", p.getBoolean("lectura_enlaces", false), v -> {
            p.edit().putBoolean("lectura_enlaces", v).apply();
            construir();
        });

        seccion("Personalizar diccionario");
        interruptor("Activar diccionario", p.getBoolean("default_activate_dictionary", false) ? "Las palabras del diccionario se reemplazan antes de hablar" : "Diccionario desactivado", p.getBoolean("default_activate_dictionary", false), v -> {
            p.edit().putBoolean("default_activate_dictionary", v).apply();
            construir();
        });
        fila("Añadir palabra", "Añade una palabra al diccionario", this::anadirPalabra);
        filaLista = fila("Lista de palabras", textoLista(), this::verPalabras);
        fila("Importar diccionario", "Elige un archivo de texto con una palabra por línea, en formato original=reemplazo", () -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, 101);
        });
        fila("Exportar diccionario", "Guarda el diccionario en un archivo de texto", () -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TITLE, "diccionario_ivona.txt");
            startActivityForResult(i, 102);
        });
        fila("Compartir diccionario", "Envía el diccionario como archivo de texto (.txt) a otra aplicación", this::compartirDiccionario);

        seccion("Restablecer");
        fila("Restablecer ajustes de texto de lectura", "Vuelve la lectura de emoji, números y enlaces a su valor de fábrica", () ->
                confirmar("Restablecer ajustes de texto de lectura", "Los ajustes de lectura volverán a sus valores de fábrica. No se tocan tu diccionario ni tus voces instaladas.", "Restablecer", () -> {
                    Ajustes.restablecer(this, Ajustes.LECTURA);
                    breve("Ajustes de lectura restablecidos");
                    construir();
                }));
        botonSecundario("Volver", v -> finish());
    }

    private String textoLista() {
        int n = Texto.lista(this).size();
        return n == 1 ? "Hay 1 palabra en el diccionario" : "Hay " + n + " palabras en el diccionario";
    }

    private void anadirPalabra() {
        Context c = this;
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(10), dp(20), 0);
        EditText o = new EditText(c);
        o.setHint("Escribe texto original");
        o.setContentDescription("Escribe texto original");
        o.setSingleLine(true);
        EditText r = new EditText(c);
        r.setHint("Escribe texto reemplazar");
        r.setContentDescription("Escribe texto reemplazar");
        r.setSingleLine(true);
        l.addView(o);
        l.addView(r);
        Button oir = new Button(c);
        oir.setText("Escuchar cómo suena");
        oir.setAllCaps(false);
        oir.setContentDescription("Escuchar cómo suena el texto de reemplazo antes de guardarlo");
        oir.setOnClickListener(v -> {
            String b = r.getText().toString().trim();
            if (b.length() == 0) {
                Toast.makeText(c, "Escribe primero el texto de reemplazo", Toast.LENGTH_SHORT).show();
                return;
            }
            escuchar(b);
        });
        l.addView(oir);
        new AlertDialog.Builder(c)
                .setTitle("Añadir palabra")
                .setView(l)
                .setPositiveButton("Aceptar", (d, w) -> {
                    String a = o.getText().toString().trim();
                    String b = r.getText().toString().trim();
                    if (a.length() == 0 || a.indexOf('=') >= 0 || a.indexOf('\n') >= 0 || b.indexOf('\n') >= 0) {
                        breve("La palabra original no puede estar vacía ni llevar el signo igual");
                        return;
                    }
                    ArrayList<String[]> una = new ArrayList<>();
                    una.add(new String[]{a, b});
                    Texto.combinar(c, una);
                    filaLista.resumen(textoLista());
                    breve("Palabra añadida: " + a);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void escuchar(String texto) {
        if (oyente != null && oyenteListo) {
            decir(texto);
            return;
        }
        oyentePendiente = texto;
        if (oyente != null) {
            return;
        }
        oyente = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS || oyente == null) {
                ui.post(() -> breve("No se pudo iniciar el motor de voz"));
                return;
            }
            try {
                int r = oyente.setLanguage(new Locale("es", "AR"));
                if (r < 0) {
                    oyente.setLanguage(new Locale("es"));
                }
            } catch (Throwable ignored) {
            }
            oyenteListo = true;
            String t = oyentePendiente;
            oyentePendiente = null;
            if (t != null) {
                ui.post(() -> decir(t));
            }
        }, getPackageName());
    }

    private void decir(String texto) {
        try {
            Bundle b = new Bundle();
            b.putBoolean("vx_sin_diccionario", true);
            oyente.speak(texto, TextToSpeech.QUEUE_FLUSH, b, "dic");
        } catch (Throwable t) {
            Motor.get(this).guardarFallo("escuchar", t);
        }
    }

    private void verPalabras() {
        List<String[]> l = Texto.lista(this);
        if (l.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Lista de palabras").setMessage("Hay 0 palabras en el diccionario").setPositiveButton("Aceptar", null).show();
            return;
        }
        String[] items = new String[l.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = l.get(i)[0] + " se lee como " + (l.get(i)[1].length() == 0 ? "nada" : l.get(i)[1]);
        }
        new AlertDialog.Builder(this)
                .setTitle("Lista de palabras")
                .setItems(items, (d, i) -> confirmarBorrar(l, i))
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void confirmarBorrar(List<String[]> l, int i) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar palabra")
                .setMessage("Quieres eliminar 1 palabra del diccionario, esto no se puede deshacer: " + l.get(i)[0])
                .setPositiveButton("Eliminar", (d, w) -> {
                    List<String[]> n = new ArrayList<>(l);
                    n.remove(i);
                    Texto.guardar(this, n);
                    filaLista.resumen(textoLista());
                    breve("Palabra eliminada");
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    protected void onActivityResult(int codigo, int resultado, Intent datos) {
        super.onActivityResult(codigo, resultado, datos);
        if (resultado != RESULT_OK || datos == null || datos.getData() == null) {
            return;
        }
        Uri uri = datos.getData();
        try {
            if (codigo == 101) {
                ArrayList<String[]> nuevas = new ArrayList<>();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
                    String linea;
                    while ((linea = r.readLine()) != null) {
                        String[] par = Texto.separar(linea);
                        if (par != null) {
                            nuevas.add(par);
                        }
                    }
                }
                int n = Texto.combinar(this, nuevas);
                filaLista.resumen(textoLista());
                breve(n == 1 ? "Se importó 1 palabra" : "Se importaron " + n + " palabras");
            } else if (codigo == 102) {
                StringBuilder sb = new StringBuilder();
                for (String[] par : Texto.lista(this)) {
                    sb.append(par[0]).append('=').append(par[1]).append('\n');
                }
                try (OutputStream o = getContentResolver().openOutputStream(uri)) {
                    o.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                }
                breve("Diccionario exportado");
            }
        } catch (Throwable t) {
            Motor.get(this).guardarFallo("diccionario", t);
            breve("No se pudo leer o guardar el archivo: " + t.getMessage());
        }
    }

    private void compartirDiccionario() {
        try {
            StringBuilder sb = new StringBuilder();
            for (String[] par : Texto.lista(this)) {
                sb.append(par[0]).append('=').append(par[1]).append('\n');
            }
            if (sb.length() == 0) {
                breve("El diccionario está vacío, no hay nada que compartir");
                return;
            }
            Intent i = CompartirProvider.intentCompartir(this, "diccionario_ivona.txt", sb.toString(), "Diccionario de Ivona TTS");
            startActivity(Intent.createChooser(i, "Compartir diccionario"));
        } catch (Throwable t) {
            Motor.get(this).guardarFallo("diccionario", t);
            breve("No se pudo compartir el diccionario: " + t.getMessage());
        }
    }
}
