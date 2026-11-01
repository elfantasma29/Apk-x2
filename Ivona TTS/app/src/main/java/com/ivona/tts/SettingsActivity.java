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
    private Fila filaAvisos;
    private boolean arrancando = false;
    private static final String ENLACE_PAYPAL = "https://paypal.me/TutorialesParaCiegos";
    private static final String ENLACE_WHATSAPP = "https://wa.me/529844315424";
    private static final String ENLACE_GRUPO = "https://chat.whatsapp.com/BmwyFkj8CkX6IWJpqkfTLs";
    private static final String ENLACE_YOUTUBE = "https://youtube.com/@tutorialesparaciegos?si=UuXUBW6xXygK8XX5";
    private static final String ENLACE_GIOVA = "https://youtube.com/@tiogiovaaccesible81";
    private AlertDialog bienvenida;
    private AlertDialog invitacion;
    private AlertDialog avisoNuevo;
    private AlertDialog errorMotor;

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
        mostrarErrorMotor();
        mostrarAvisoNuevo();
        mostrarInvitacion();
        Actualizador.comprobarAlAbrir(this);
        Avisos.consultarAsync(this, false, ok -> ui.post(() -> {
            if (!isFinishing() && !isDestroyed()) {
                actualizarFilaAvisos();
                mostrarAvisoNuevo();
            }
        }));
    }

    private boolean bienvenidaVista() {
        return getSharedPreferences("estado", MODE_PRIVATE).getBoolean("bienvenida_2", false);
    }

    private boolean hayDialogo() {
        return (bienvenida != null && bienvenida.isShowing()) || (novedades != null && novedades.isShowing())
                || (invitacion != null && invitacion.isShowing()) || (avisoNuevo != null && avisoNuevo.isShowing())
                || (errorMotor != null && errorMotor.isShowing());
    }

    private String textoAvisos() {
        int n = Avisos.nuevos(this);
        if (n == 0) {
            return "Sin mensajes nuevos";
        }
        return n == 1 ? "1 mensaje nuevo" : n + " mensajes nuevos";
    }

    private void actualizarFilaAvisos() {
        if (filaAvisos != null) {
            filaAvisos.titulo.setText(Avisos.nuevos(this) > 0 ? "Avisos, nuevo" : "Avisos");
            filaAvisos.resumen(textoAvisos());
        }
    }

    private void mostrarAvisoNuevo() {
        if (!bienvenidaVista() || hayDialogo() || isFinishing() || isDestroyed() || Actualizador.pendiente(this) != null) {
            return;
        }
        if (!Avisos.hayParaNotificar(this)) {
            return;
        }
        avisoNuevo = new AlertDialog.Builder(this)
                .setTitle("Nuevo mensaje")
                .setMessage("Hay un nuevo mensaje disponible. Revisa la sección de avisos.")
                .setCancelable(false)
                .setPositiveButton("Confirmar", (d, w) -> Avisos.notificado(this))
                .create();
        avisoNuevo.show();
    }

    private void mostrarErrorMotor() {
        SharedPreferences e = getSharedPreferences("estado", MODE_PRIVATE);
        String err = e.getString("motor_error", null);
        if (err == null || hayDialogo() || isFinishing() || isDestroyed() || !bienvenidaVista()) {
            return;
        }
        e.edit().remove("motor_error").apply();
        errorMotor = new AlertDialog.Builder(this)
                .setTitle("El motor no pudo iniciarse")
                .setMessage("Ivona TTS no pudo iniciarse en este teléfono, ni con el ejecutor rápido ni con el de respaldo. Por favor, informa al desarrollador cuanto antes para que pueda solucionarlo: toca Compartir fallos y envíame el informe, o contáctame por WhatsApp.")
                .setCancelable(false)
                .setPositiveButton("Compartir fallos", (d, w) -> compartirFallos())
                .setNeutralButton("Contactar por WhatsApp", (d, w) -> abrirEnlace(ENLACE_WHATSAPP))
                .setNegativeButton("Cerrar", null)
                .create();
        errorMotor.show();
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
        if (!e.getBoolean("bienvenida_2", false) || e.getBoolean("grupo_unido", false)) {
            return;
        }
        if (isFinishing() || isDestroyed() || hayDialogo()) {
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
        if (!bienvenidaVista()) {
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

    private TextView parrafo(LinearLayout col, String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(16);
        v.setTextColor(color(R.color.texto));
        v.setLineSpacing(0, 1.15f);
        v.setPadding(0, dp(6), 0, dp(6));
        col.addView(v);
        return v;
    }

    private void mostrarBienvenida() {
        final SharedPreferences e = getSharedPreferences("estado", MODE_PRIVATE);
        if (e.getBoolean("bienvenida_2", false) || (bienvenida != null && bienvenida.isShowing())) {
            return;
        }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(8), dp(24), dp(8));
        for (String t : PARRAFOS_BIENVENIDA) {
            parrafo(col, t);
        }
        TextView creditos = parrafo(col, "Diálogo creado, desarrollado e implementado por Emmanuel el Fantasma.");
        creditos.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        TextView titulo = new TextView(this);
        titulo.setText("Agradecimiento especial");
        titulo.setTextSize(18);
        titulo.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titulo.setTextColor(color(R.color.acento));
        titulo.setAccessibilityHeading(true);
        titulo.setPadding(0, dp(20), 0, dp(4));
        col.addView(titulo);
        TextView gracias = parrafo(col, "Un agradecimiento muy especial al compañero Tio Giova accesible, por aportar los datos de las voces Conchita y Penélope del motor Ivona para este proyecto. Gracias por tu contribución. Visita su canal de YouTube con el botón Visitar canal.");
        gracias.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        bienvenida = new AlertDialog.Builder(this)
                .setTitle("Bienvenido a Ivona TTS")
                .setView(sv)
                .setCancelable(false)
                .setPositiveButton("Entendido", (d, w) -> {
                    e.edit().putBoolean("bienvenida_2", true).putBoolean("bienvenida_vista", true).apply();
                    Avisos.notificado(this);
                    ui.postDelayed(this::mostrarInvitacion, 400);
                })
                .setNeutralButton("Visitar canal", null)
                .setNegativeButton("Apoyar el proyecto", null)
                .create();
        bienvenida.show();
        bienvenida.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> abrirEnlace(ENLACE_GIOVA));
        bienvenida.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> abrirEnlace(ENLACE_PAYPAL));
    }

    private static final String[] PARRAFOS_BIENVENIDA = {
            "Hola, y gracias por probar este lanzamiento.",
            "En esta ocasión revivimos Ivona TTS, un motor de voz que dejó de funcionar en los teléfonos modernos. Este es el segundo lanzamiento de un proyecto con una misión: devolver a la vida motores de voz antiguos y, en el futuro, lanzar nuestro propio motor con nuestras propias voces. Habrá más lanzamientos, incluso voces de computadora traídas a Android.",
            "Esta vez el trabajo fue más grande que con el motor anterior. Ivona se hizo para teléfonos de otra época, y al principio era demasiado lento para leer con fluidez. Tuvimos que modernizarlo por dentro, en sus capas más profundas, para que respondiera rápido. Descubrir de dónde venía esa lentitud nos tomó más tiempo y esfuerzo, y también elevó el costo de la inteligencia artificial con la que trabajamos.",
            "Y hay una parte difícil de decir. Traer motores antiguos a la vida y portar voces de computadora a Android requiere mucho trabajo, y la inteligencia artificial que nos ayuda hay que pagarla. Además, el siguiente motor o voz que queremos traer también tiene costo. Por eso, los lanzamientos se detendrán hasta que juntemos lo necesario. Por nuestra cuenta, de nuestros propios bolsillos, eso podría tardar varios meses o incluso detenerse para siempre.",
            "Si decides apoyarnos, aunque sea con una donación pequeña, continuamos. Esta es nuestra promesa: cuando lleguen las dos primeras donaciones, sin importar la cantidad, retomamos el proyecto. Puedes ayudar desde Ajustes, en Ayudar al proyecto.",
            "Pero no importa si decides donar o no. Te invitamos a probar el motor, a darnos tus sugerencias y a avisarnos de cualquier problema que encuentres, para que juntos revisemos que todo salga bien.",
            "Por último, gracias por estar aquí."
    };

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

        seccion("Estado y prueba");
        filaEstado = filaInfo("Estado del motor", mo.estado());
        fila("Escuchar una frase de prueba", "Lee una frase de ejemplo con tu configuración actual", this::probar);
        fila("Elegir Ivona TTS como motor de voz", "Abre los ajustes de texto a voz de Android", this::abrirAjustesTts);

        seccion("Avisos");
        filaAvisos = fila(Avisos.nuevos(this) > 0 ? "Avisos, nuevo" : "Avisos", textoAvisos(), () -> startActivity(new Intent(this, AvisosActivity.class)));

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
        filaInfo("El proyecto", "Ivona TTS es una versión personal que revive el antiguo motor de voz Ivona para que funcione en teléfonos actuales de 64 bits. Trae las voces en español Conchita, de España, y Penélope, de Estados Unidos, que se descargan desde la propia aplicación. Es una idea desarrollada por Emmanuel, el Fantasma, dentro del proyecto de revivir motores antiguos para que no se pierdan. Las voces y la marca Ivona pertenecen a sus dueños originales; esta aplicación no está afiliada a ellos.");
        filaInfo("Agradecimiento especial", "Al compañero Tio Giova accesible, por aportar los datos de las voces Conchita y Penélope del motor Ivona para este proyecto.");
        botonSecundario("Visitar el canal de Tio Giova accesible", v -> abrirEnlace(ENLACE_GIOVA));
        boton("Visitar nuestro canal de YouTube", v -> abrirEnlace(ENLACE_YOUTUBE));

        seccion("Contacto y apoyo");
        filaInfo("Contáctame", "Si tienes alguna duda, inquietud o sugerencia, o quieres hablar conmigo, escríbeme al WhatsApp +52 984 431 5424.");
        botonSecundario("Contactar por WhatsApp", v -> abrirEnlace(ENLACE_WHATSAPP));
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
                    mostrarErrorMotor();
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
