package com.ivona.tts;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int PEDIR_ZIP = 11;
    private static final int GUARDAR_REGISTRO = 12;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Map<String, TextView> estados = new HashMap<>();
    private final Map<String, Button> botonesDescarga = new HashMap<>();
    private final Map<String, Button> botonesEliminar = new HashMap<>();
    private TextView general;
    private Voces.Voz pendiente;
    private TextToSpeech tts;
    private String ultimoAnuncio = "";
    private final Runnable refresco = new Runnable() {
        @Override
        public void run() {
            actualizar();
            h.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        raiz.setPadding(p, p, p, p);
        sv.addView(raiz, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv);
        setTitle("Ivona TTS");

        TextView titulo = texto(raiz, "Ivona TTS, prototipo", 22);
        titulo.setAccessibilityHeading(true);
        general = texto(raiz, "", 16);
        general.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);

        for (final Voces.Voz v : Voces.TODAS) {
            TextView t = texto(raiz, v.nombre + ", " + v.detalle, 18);
            t.setAccessibilityHeading(true);
            TextView st = texto(raiz, "", 16);
            estados.put(v.clave, st);
            Button d = boton(raiz, "Descargar " + v.nombre, x -> descargar(v));
            botonesDescarga.put(v.clave, d);
            boton(raiz, "Instalar " + v.nombre + " desde un archivo zip", x -> elegirZip(v));
            Button e = boton(raiz, "Eliminar " + v.nombre, x -> eliminar(v));
            botonesEliminar.put(v.clave, e);
        }

        TextView t2 = texto(raiz, "Herramientas", 18);
        t2.setAccessibilityHeading(true);
        boton(raiz, "Probar una voz instalada", x -> probar());
        boton(raiz, "Abrir los ajustes de texto a voz del sistema", x -> abrirAjustes());
        boton(raiz, "Guardar el registro de errores en un archivo", x -> guardarRegistro());

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        actualizar();
    }

    private TextView texto(LinearLayout raiz, String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setPadding(0, 12, 0, 12);
        raiz.addView(t);
        return t;
    }

    private Button boton(LinearLayout raiz, String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setContentDescription(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        raiz.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return b;
    }

    @Override
    protected void onResume() {
        super.onResume();
        h.post(refresco);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(refresco);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
    }

    private void anunciar(String s) {
        if (!s.equals(ultimoAnuncio)) {
            ultimoAnuncio = s;
            general.announceForAccessibility(s);
        }
    }

    private void actualizar() {
        boolean ocupado = DescargaService.activo;
        String g;
        if (ocupado) {
            g = DescargaService.fase + ". " + DescargaService.mensaje;
        } else if (!DescargaService.resultado.isEmpty()) {
            g = DescargaService.mensaje;
        } else {
            g = Voces.instaladas(this).isEmpty() ? "Todavía no hay voces instaladas." : "Hay " + Voces.instaladas(this).size() + " voces instaladas.";
        }
        if (!g.equals(general.getText().toString())) {
            general.setText(g);
        }
        for (Voces.Voz v : Voces.TODAS) {
            boolean inst = Voces.instalada(this, v);
            boolean esta = ocupado && v.clave.equals(DescargaService.clave);
            String s;
            if (esta) {
                s = DescargaService.fase + ", " + Texto.avance(DescargaService.hecho, DescargaService.total);
            } else if (inst) {
                s = "Instalada. " + v.completo();
            } else {
                s = "No instalada. " + v.completo();
            }
            TextView st = estados.get(v.clave);
            if (!s.equals(st.getText().toString())) {
                st.setText(s);
            }
            Button d = botonesDescarga.get(v.clave);
            String td = esta ? "Cancelar la descarga de " + v.nombre : (inst ? "Descargar de nuevo " + v.nombre : "Descargar " + v.nombre);
            if (!td.equals(d.getText().toString())) {
                d.setText(td);
                d.setContentDescription(td);
            }
            botonesEliminar.get(v.clave).setEnabled(inst && !ocupado);
        }
    }

    private void descargar(Voces.Voz v) {
        if (DescargaService.activo) {
            if (v.clave.equals(DescargaService.clave)) {
                DescargaService.cancelar = true;
                anunciar("Cancelando");
            } else {
                anunciar("Ya hay otra descarga en curso");
            }
            return;
        }
        Intent i = new Intent(this, DescargaService.class);
        i.setAction(DescargaService.DESCARGAR);
        i.putExtra("clave", v.clave);
        startForegroundService(i);
        anunciar("Descargando " + v.nombre);
    }

    private void elegirZip(Voces.Voz v) {
        if (DescargaService.activo) {
            anunciar("Espera a que termine la descarga en curso");
            return;
        }
        pendiente = v;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, PEDIR_ZIP);
    }

    private void eliminar(Voces.Voz v) {
        Motor.get(this).olvidar(v);
        DescargaService.borrar(Voces.carpeta(this, v));
        anunciar("Voz " + v.nombre + " eliminada");
        actualizar();
    }

    private void probar() {
        if (Voces.instaladas(this).isEmpty()) {
            anunciar("Primero instala una voz");
            return;
        }
        if (tts != null) {
            tts.shutdown();
        }
        anunciar("Probando la voz");
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                tts.setLanguage(new Locale("es", "ES"));
                tts.speak("Hola, esta es una prueba de la voz de Ivona.", TextToSpeech.QUEUE_FLUSH, null, "prueba");
            } else {
                anunciar("No se pudo iniciar el motor de prueba");
            }
        }, getPackageName());
    }

    private void abrirAjustes() {
        try {
            startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Throwable ignored) {
            }
        }
    }

    private void guardarRegistro() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TITLE, "registro_ivona.txt");
        startActivityForResult(i, GUARDAR_REGISTRO);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri u = data.getData();
        if (req == PEDIR_ZIP && pendiente != null) {
            Intent i = new Intent(this, DescargaService.class);
            i.setAction(DescargaService.INSTALAR);
            i.setData(u);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.putExtra("clave", pendiente.clave);
            startForegroundService(i);
            anunciar("Instalando " + pendiente.nombre);
        } else if (req == GUARDAR_REGISTRO) {
            try (OutputStream o = getContentResolver().openOutputStream(u)) {
                File f = Motor.get(this).archivoRegistro();
                String cab = "Ivona TTS 0.1.0-prototipo, Android " + Build.VERSION.RELEASE + ", " + Build.MODEL + ", " + Build.SUPPORTED_ABIS[0] + "\n\n";
                o.write(cab.getBytes("UTF-8"));
                if (f.exists()) {
                    try (InputStream in = new FileInputStream(f)) {
                        byte[] b = new byte[16384];
                        int n;
                        while ((n = in.read(b)) > 0) {
                            o.write(b, 0, n);
                        }
                    }
                } else {
                    o.write("Todavía no hay registro.\n".getBytes("UTF-8"));
                }
                anunciar("Registro guardado");
            } catch (Throwable t) {
                anunciar("No se pudo guardar el registro");
            }
        }
    }
}
