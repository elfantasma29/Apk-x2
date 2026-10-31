package com.ivona.tts;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public class SettingsActivity extends Pantalla {

    private static final int PEDIR_COPIA = 201;

    private SharedPreferences p;
    private TextToSpeech prueba;
    private Fila filaEstado;
    private Fila filaMedicion;
    private boolean arrancando = false;
    private static final String ENLACE_PAYPAL = "https://paypal.me/TutorialesParaCiegos";
    private static final String ENLACE_WHATSAPP = "https://wa.me/529844315424";
    private static final String ENLACE_GRUPO = "https://chat.whatsapp.com/BmwyFkj8CkX6IWJpqkfTLs";
    private static final String ENLACE_YOUTUBE = "https://youtube.com/@tutorialesparaciegos?si=UuXUBW6xXygK8XX5";
    private AlertDialog bienvenida;
    private AlertDialog invitacion;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = Ajustes.p(this);
        Actualizador.programar(this);
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        construir();
        if (!Voces.instaladas(this).isEmpty()) {
            arrancar();
        }
        mostrarBienvenida();
        mostrarNovedades();
        mostrarInvitacion();
        Actualizador.comprobarAlAbrir(this);
    }

    private void abrirEnlace(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable t) {
            breve("No se pudo abrir el enlace");
        }
    }

    private void mostrarInvitacion() {
        final SharedPreferences e = getSharedPreferences("estado", MODE_PRIVATE);
        if (!e.getBoolean("bienvenida_vista", false) || e.getBoolean("grupo_unido", false)) {
            return;
        }
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if ((invitacion != null && invitacion.isShowing()) || (bienvenida != null && bienvenida.isShowing()) || (novedades != null && novedades.isShowing())) {
            return;
        }
        if (Actualizador.pendiente(this) != null) {
            return;
        }
        long ahora = System.currentTimeMillis();
        if (ahora < e.getLong("grupo_proximo", 0)) {
            return;
        }
        long dias = 10 + new java.util.Random().nextInt(6);
        e.edit().putLong("grupo_proximo", ahora + dias * 86400000L).apply();
        invitacion = new AlertDialog.Builder(this)
                .setTitle("Únete a mi grupo de WhatsApp")
                .setMessage("Únete a mi grupo de WhatsApp para enterarte de las novedades y ser uno de los primeros en probar las aplicaciones.")
                .setPositiveButton("Aceptar", (d, w) -> {
                    e.edit().putBoolean("grupo_unido", true).apply();
                    abrirEnlace(ENLACE_GRUPO);
                })
                .setNegativeButton("Cancelar", null)
                .create();
        invitacion.show();
    }

    private AlertDialog novedades;

    private void mostrarNovedades() {
        if (novedades != null && novedades.isShowing()) {
            return;
        }
        if (!getSharedPreferences("estado", MODE_PRIVATE).getBoolean("bienvenida_vista", false)) {
            return;
        }
        String[] n = Actualizador.novedadesPorMostrar(this);
        if (n == null) {
            return;
        }
        String texto = n[1].length() == 0 ? "Esta versión incluye mejoras generales." : n[1];
        TextView cuerpo = new TextView(this);
        cuerpo.setText(texto);
        cuerpo.setTextSize(16);
        cuerpo.setTextColor(color(R.color.texto));
        cuerpo.setPadding(dp(24), dp(8), dp(24), dp(8));
        ScrollView sv = new ScrollView(this);
        sv.addView(cuerpo);
        novedades = new AlertDialog.Builder(this)
                .setTitle("Novedades de la versión " + n[0])
                .setView(sv)
                .setCancelable(false)
                .setPositiveButton("Entendido", (d, w) -> Actualizador.novedadesVistas(this))
                .create();
        novedades.show();
    }

    private void mostrarBienvenida() {
        SharedPreferences e = getSharedPreferences("estado", MODE_PRIVATE);
        if (e.getBoolean("bienvenida_vista", false) || (bienvenida != null && bienvenida.isShowing())) {
            return;
        }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(8), dp(24), dp(8));
        TextView cuerpo = new TextView(this);
        cuerpo.setText(TEXTO_BIENVENIDA);
        cuerpo.setTextSize(16);
        cuerpo.setTextColor(color(R.color.texto));
        cuerpo.setLineSpacing(0, 1.15f);
        col.addView(cuerpo);
        TextView autor = new TextView(this);
        autor.setText("Idea creada, implementada y desarrollada por Emmanuel, el Fantasma, en colaboración con Tutoriales para Ciegos.");
        autor.setTextSize(15);
        autor.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        autor.setTextColor(color(R.color.acento));
        autor.setPadding(0, dp(20), 0, dp(4));
        col.addView(autor);
        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        bienvenida = new AlertDialog.Builder(this)
                .setTitle("Antes de empezar")
                .setView(sv)
                .setCancelable(false)
                .setPositiveButton("Confirmar", (d, w) -> {
                    e.edit().putBoolean("bienvenida_vista", true).apply();
                    ui.postDelayed(this::mostrarInvitacion, 400);
                })
                .setNeutralButton("Apoyar el proyecto", null)
                .create();
        bienvenida.show();
        bienvenida.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> abrirEnlace(ENLACE_PAYPAL));
    }

    private static final String TEXTO_BIENVENIDA =
            "Hola. Ivona TTS nació para que las voces de Ivona no se pierdan. Revive el antiguo motor Ivona en teléfonos actuales de 64 bits, con las voces en español Conchita y Penélope.\n\n"
                    + "Aquí no tienes que buscar los archivos de las voces ni copiarlos en carpetas: se descargan e instalan desde la propia aplicación, con un toque.\n\n"
                    + "Es un proyecto personal, hecho sin computadora: investigo, escribo, compilo y creo las aplicaciones desde mi teléfono, con inteligencia artificial como ayuda. Si quieres apoyar el proyecto, tu aporte lo hace posible. Si no puedes, la aplicación sigue ahí para que la pruebes. Gracias por estar aquí.";

    @Override
    protected void onDestroy() {
        if (prueba != null) {
            prueba.shutdown();
            prueba = null;
        }
        super.onDestroy();
    }

    private String formato(float v) {
        return String.format("%,.1f%%", v);
    }

    private void construir() {
        Motor mo = Motor.get(this);
        armar("Ivona TTS", "Versión " + BuildInfo.nombreVersion(this));

        seccion("Probar la voz");
        filaEstado = filaInfo("Estado del motor", mo.estado());
        fila("Escuchar una frase de prueba", "Lee una frase de ejemplo con tu configuración actual", this::probar);

        fila("Ir a los ajustes de texto a voz de Android", "Para elegir Ivona TTS como motor predeterminado", this::abrirAjustesTts);

        seccion("Voces de Ivona");
        int cuantas = Voces.instaladas(this).size();
        fila("Descargar e instalar voces", cuantas == 0 ? "Ninguna voz instalada todavía" : cuantas == 1 ? "1 voz instalada" : cuantas + " voces instaladas", () -> startActivity(new Intent(this, MainActivity.class)));
        agregarPreferida();

        seccion("Velocidad, tono y volumen");
        Fila fVol = filaAbierta("Volumen de la voz", String.format("%,.0f", p.getFloat("default_volume", 100f)));
        fVol.vista.setOnClickListener(v -> dialogoDeslizador("Volumen de la voz", 100, p.getFloat("default_volume", 100f), "%,.0f", n -> {
            p.edit().putFloat("default_volume", n).apply();
            fVol.resumen(String.format("%,.0f", n));
        }));
        Fila fVel = filaAbierta("Velocidad del habla", formato(p.getFloat("default_rate", 100f)));
        fVel.vista.setOnClickListener(v -> dialogoDeslizador("Velocidad del habla", 200, p.getFloat("default_rate", 100f), "%,.1f%%", n -> {
            p.edit().putFloat("default_rate", n).apply();
            fVel.resumen(formato(n));
        }));
        Fila fTono = filaAbierta("Tono (solo para agudizar)", formato(p.getFloat("default_pitch", 100f)));
        fTono.vista.setOnClickListener(v -> dialogoDeslizador("Tono (solo para agudizar)", 200, p.getFloat("default_pitch", 100f), "%,.1f%%", n -> {
            p.edit().putFloat("default_pitch", n).apply();
            fTono.resumen(formato(n));
        }));
        fila("Restablecer ajustes de voz", "Vuelve el volumen, la velocidad y el tono a su valor de fábrica", () ->
                confirmar("Restablecer ajustes de voz", "El volumen, la velocidad y el tono volverán a sus valores de fábrica. No se tocan tu diccionario ni tus voces instaladas.", "Restablecer", () -> {
                    Ajustes.restablecer(this, Ajustes.VOZ);
                    breve("Ajustes de voz restablecidos");
                    construir();
                }));

        seccion("Salida de audio");
        String[] valores = {"no", "apps", "todo"};
        CharSequence[] textos = {
                "No usar: cada aplicación decide",
                "Juegos y aplicaciones; el lector de pantalla no cambia",
                "Todo, incluido el lector de pantalla"
        };
        String actual = Ajustes.modoMultimedia(p);
        int idx = 0;
        for (int i = 0; i < valores.length; i++) {
            if (valores[i].equals(actual)) {
                idx = i;
            }
        }
        Fila fMedia = filaAbierta("Usar volumen multimedia", textos[idx].toString());
        fMedia.vista.setOnClickListener(v -> {
            String a = Ajustes.modoMultimedia(p);
            int m = 0;
            for (int i = 0; i < valores.length; i++) {
                if (valores[i].equals(a)) {
                    m = i;
                }
            }
            dialogoLista("Usar volumen multimedia", textos, m, x -> {
                int k = (int) x;
                p.edit().putString("salida_multimedia", valores[k]).apply();
                fMedia.resumen(textos[k].toString());
                breve(k == 0 ? "Volumen multimedia desactivado" : "Volumen multimedia activado");
            });
        });
        interruptor("Evitar cortes en voces pesadas", "Si el teléfono no sintetiza tan rápido como habla, junta un poco de audio antes de sonar. Puede añadir un breve retraso en esas voces", p.getBoolean("reserva_audio", true), v -> {
            p.edit().putBoolean("reserva_audio", v).apply();
            breve(v ? "Evitar cortes activado" : "Evitar cortes desactivado");
        });
        interruptor("Modo compatible con lectores de pantalla", "Acepta el idioma del teléfono para que lectores como Jieshuo no rechacen este motor", p.getBoolean("modo_compatible", true), v -> {
            p.edit().putBoolean("modo_compatible", v).apply();
            breve(v ? "Modo compatible activado" : "Modo compatible desactivado");
        });
        fila("Restablecer salida de audio", "Vuelve a la configuración de fábrica", () ->
                confirmar("Restablecer salida de audio", "La salida de audio volverá a su valor de fábrica. No se tocan tu diccionario ni tus voces instaladas.", "Restablecer", () -> {
                    Ajustes.restablecer(this, Ajustes.AUDIO);
                    breve("Salida de audio restablecida");
                    construir();
                }));
        nota("Con el volumen multimedia, lo que piden los juegos y las aplicaciones suena por el volumen multimedia en lugar del de accesibilidad, y las grabadoras de pantalla pueden capturarlo.");

        seccion("Respuesta del motor (experimental)");
        interruptor("Aumentar respuesta del motor", "Reduce la latencia: usa un segundo motor para repartir las frases largas, sube la prioridad de los hilos, recorta los silencios y no retiene audio. Gasta más memoria y batería", p.getBoolean("respuesta_rapida", false), v -> {
            p.edit().putBoolean("respuesta_rapida", v).apply();
            breve(v ? "Respuesta del motor aumentada" : "Respuesta del motor normal");
            if (!v) {
                Motor.get(this).apagar();
            }
        });
        if (Medicion.activa(this)) {
            filaMedicion = filaInfo("Medición de latencia", Medicion.estadoTexto(this));
            boton("Detener medición y guardar en Descargas", v -> detenerMedicion());
        } else {
            fila("Iniciar medición de latencia", "Mide cuánto tarda cada frase en sonar mientras usas el lector. Al detenerla se guarda un archivo de texto en Descargas", () -> {
                Medicion.iniciar(this);
                breve("Medición iniciada. Usa el lector con normalidad y vuelve aquí para detenerla");
                construir();
            });
        }
        nota("Es una función experimental. Para comparar, mide un rato con la opción desactivada y otro rato con ella activada.");

        seccion("Lectura del texto");
        fila("Ajustes de texto de lectura", "Emoji, números, enlaces y diccionario", () -> startActivity(new Intent(this, LecturaActivity.class)));

        seccion("Copia de seguridad");
        fila("Crear copia de seguridad", "Guarda todos tus ajustes y tu diccionario en un archivo en Descargas", this::crearCopia);
        fila("Restaurar copia de seguridad", "Elige un archivo de copia y recupera tus ajustes y tu diccionario", this::elegirCopia);

        seccion("Fallos y registro");
        nota("¿Tienes algún problema con el motor? Compárteme los fallos y te ayudo a solucionarlo.");
        boton("Compartir fallos del motor", v -> compartirFallos());
        interruptor("Registro detallado del motor", "Se aplica al reiniciar el motor; actívalo si vas a compartir los fallos", p.getBoolean("depurar", true), v -> p.edit().putBoolean("depurar", v).apply());
        fila("Copiar registro", "Copia el estado, los fallos guardados y el registro del motor", this::copiar);
        fila("Borrar fallos guardados", null, () -> {
            Motor.get(this).borrarFallos();
            breve("Fallos borrados");
        });

        seccion("Acerca de");
        filaInfo("Ivona TTS", "Versión " + BuildInfo.nombreVersion(this));
        filaInfo("El proyecto", "Ivona TTS es una versión personal que revive el antiguo motor de voz Ivona para que funcione en teléfonos actuales de 64 bits. Trae las voces en español Conchita, de España, y Penélope, de Estados Unidos, que se descargan desde la propia aplicación. Es una idea desarrollada por Emmanuel, el Fantasma, dentro del proyecto de revivir motores antiguos para que no se pierdan. Las voces y la marca Ivona pertenecen a sus dueños originales; esta aplicación no está afiliada a ellos. Para no perderte novedades ni próximos lanzamientos, visita nuestro canal de YouTube.");
        boton("Visitar el canal de YouTube", v -> abrirEnlace(ENLACE_YOUTUBE));

        seccion("Contáctame");
        filaInfo("Contáctame", "Si tienes alguna duda, inquietud, sugerencia o quieres hablar conmigo, contáctame.");
        filaInfo("Mi número de WhatsApp", "+52 984 431 5424");
        boton("Contactar", v -> abrirEnlace(ENLACE_WHATSAPP));

        seccion("Ayudar al proyecto");
        filaInfo("Ayudar al proyecto", "Si quieres apoyar el proyecto, tu aporte hace posible los próximos lanzamientos.");
        boton("Apoyar el proyecto", v -> abrirEnlace(ENLACE_PAYPAL));
    }

    private void agregarPreferida() {
        final List<Voces.Voz> inst = Voces.instaladas(this);
        if (inst.size() < 2) {
            return;
        }
        final CharSequence[] nombres = new CharSequence[inst.size()];
        int sel = 0;
        String actual = p.getString("voz_preferida", inst.get(0).clave);
        for (int i = 0; i < inst.size(); i++) {
            nombres[i] = inst.get(i).nombre + ", " + inst.get(i).detalle;
            if (inst.get(i).clave.equals(actual)) {
                sel = i;
            }
        }
        final Fila f = filaAbierta("Voz preferida en español", nombres[sel].toString());
        f.vista.setOnClickListener(v -> {
            String a = p.getString("voz_preferida", inst.get(0).clave);
            int ya = 0;
            for (int i = 0; i < inst.size(); i++) {
                if (inst.get(i).clave.equals(a)) {
                    ya = i;
                }
            }
            dialogoLista("Voz preferida en español", nombres, ya, x -> {
                int q = (int) x;
                p.edit().putString("voz_preferida", inst.get(q).clave).apply();
                f.resumen(nombres[q].toString());
            });
        });
        nota("Suena cuando el idioma pedido no indica el país. Si piden español de España o de Estados Unidos, se usa la voz que corresponde.");
    }

    private void arrancar() {
        if (arrancando) {
            return;
        }
        final Motor mo = Motor.get(this);
        if (mo.vivo() || Voces.instaladas(this).isEmpty()) {
            return;
        }
        arrancando = true;
        new Thread(() -> {
            mo.asegurar(Voces.elegir(this, "spa", null, null));
            ui.post(() -> {
                arrancando = false;
                if (!isDestroyed()) {
                    construir();
                }
            });
        }, "arranque-ajustes").start();
    }

    private void probar() {
        if (prueba != null) {
            prueba.shutdown();
            prueba = null;
        }
        if (filaEstado != null) {
            filaEstado.resumen("Probando la voz");
        }
        prueba = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS || prueba == null) {
                ui.post(() -> {
                    if (filaEstado != null) {
                        filaEstado.resumen("No se pudo iniciar el motor de voz de Android con este motor");
                    }
                });
                return;
            }
            try {
                int r = prueba.setLanguage(new Locale("es", "ES"));
                if (r < 0) {
                    prueba.setLanguage(new Locale("es"));
                }
                prueba.speak(getString(R.string.sample_text), TextToSpeech.QUEUE_FLUSH, null, "prueba");
            } catch (Throwable ex) {
                Motor.get(this).guardarFallo("probar", ex);
            }
        }, getPackageName());
    }

    private void abrirAjustesTts() {
        try {
            startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Throwable t2) {
                breve("No pude abrir los ajustes");
            }
        }
    }

    private void compartirFallos() {
        Motor mo = Motor.get(this);
        String fp = mo.falloPrevio();
        String texto = "Ivona TTS " + BuildInfo.nombreVersion(this) + "\n" + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + ", Android " + android.os.Build.VERSION.RELEASE + "\n\n"
                + "Estado: " + mo.estado() + "\n" + (fp.length() > 0 ? "FALLOS GUARDADOS:\n" + fp + "\n---\n" : "") + mo.registroReciente();
        try {
            Intent i = CompartirProvider.intentCompartir(this, "fallos_ivona.txt", texto, "Fallos de Ivona TTS");
            startActivity(Intent.createChooser(i, "Compartir fallos"));
        } catch (Throwable t) {
            breve("No se pudo compartir el registro: " + t.getMessage());
        }
    }

    private void copiar() {
        Motor mo = Motor.get(this);
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        String fp = mo.falloPrevio();
        cm.setPrimaryClip(ClipData.newPlainText("registro", "Estado: " + mo.estado() + "\n" + (fp.length() > 0 ? "FALLOS GUARDADOS:\n" + fp + "\n---\n" : "") + mo.registroReciente()));
        breve("Registro copiado");
    }

    private void detenerMedicion() {
        final Context c = getApplicationContext();
        new Thread(() -> {
            try {
                String[] r = Medicion.detener(c);
                ui.post(() -> {
                    construir();
                    new AlertDialog.Builder(this)
                            .setTitle("Medición guardada")
                            .setMessage("Se guardó en la carpeta Descargas con el nombre " + r[0] + ". " + r[1])
                            .setPositiveButton("Aceptar", null)
                            .show();
                });
            } catch (Throwable t) {
                Motor.get(c).guardarFallo("medición", t);
                ui.post(() -> {
                    construir();
                    new AlertDialog.Builder(this)
                            .setTitle("No se pudo guardar la medición")
                            .setMessage(String.valueOf(t.getMessage()))
                            .setPositiveButton("Aceptar", null)
                            .show();
                });
            }
        }, "detener-medicion").start();
    }

    private void crearCopia() {
        final Context c = getApplicationContext();
        breve("Creando la copia de seguridad");
        new Thread(() -> {
            try {
                String json = Ajustes.exportar(c);
                String nombre = Ajustes.guardarEnDescargas(c, json);
                ui.post(() -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Copia de seguridad creada")
                            .setMessage("Se guardó en la carpeta Descargas con el nombre " + nombre + ". Incluye tus ajustes y tu diccionario.")
                            .setPositiveButton("Aceptar", null)
                            .show();
                });
            } catch (Throwable t) {
                Motor.get(c).guardarFallo("copia de seguridad", t);
                ui.post(() -> new AlertDialog.Builder(this)
                        .setTitle("No se pudo crear la copia")
                        .setMessage(String.valueOf(t.getMessage()))
                        .setPositiveButton("Aceptar", null)
                        .show());
            }
        }, "crear-copia").start();
    }

    private void elegirCopia() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            i.putExtra("android.provider.extra.INITIAL_URI", Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload"));
        } catch (Throwable ignored) {
        }
        startActivityForResult(i, PEDIR_COPIA);
    }

    @Override
    protected void onActivityResult(int codigo, int resultado, Intent datos) {
        super.onActivityResult(codigo, resultado, datos);
        if (codigo != PEDIR_COPIA || resultado != RESULT_OK || datos == null || datos.getData() == null) {
            return;
        }
        final Uri uri = datos.getData();
        final Context c = getApplicationContext();
        new Thread(() -> {
            try {
                Ajustes.Copia copia = Ajustes.leer(Ajustes.leerTexto(c, uri));
                ui.post(() -> confirmar("Restaurar copia de seguridad",
                        "Se reemplazarán tus ajustes y tu diccionario actuales por los de la copia"
                                + (copia.fecha.length() > 0 ? " del " + copia.fecha : "")
                                + ": " + copia.ajustes + " ajustes y " + copia.palabras + " palabras. Tus voces instaladas no se tocan.",
                        "Restaurar", () -> restaurar(copia)));
            } catch (Throwable t) {
                ui.post(() -> new AlertDialog.Builder(this)
                        .setTitle("No se pudo leer la copia")
                        .setMessage(t instanceof IllegalArgumentException ? String.valueOf(t.getMessage()) : "El archivo no es una copia de seguridad válida de Ivona TTS.")
                        .setPositiveButton("Aceptar", null)
                        .show());
            }
        }, "leer-copia").start();
    }

    private void restaurar(Ajustes.Copia copia) {
        try {
            Ajustes.aplicar(this, copia);
            breve("Copia de seguridad restaurada");
            olvidarScroll();
            construir();
        } catch (Throwable t) {
            Motor.get(this).guardarFallo("restaurar copia", t);
            new AlertDialog.Builder(this)
                    .setTitle("No se pudo restaurar")
                    .setMessage(String.valueOf(t.getMessage()))
                    .setPositiveButton("Aceptar", null)
                    .show();
        }
    }
}
