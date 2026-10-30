package com.ivona.tts;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

public class ActualizarActivity extends Pantalla {

    private static final int LIBRE = 0;
    private static final int DESCARGANDO = 1;
    private static final int INSTALANDO = 2;
    private static final int ERROR = 3;

    private static volatile int fase = LIBRE;
    private static volatile long hecho = 0;
    private static volatile long total = 0;
    private static volatile String mensaje = "";

    private Actualizador.Info info;
    private Button actualizar;
    private Fila filaEstado;
    private ProgressBar barra;
    private boolean esperandoPermiso = false;
    private String ultimoTexto = "";
    private int ultimoDecil = -1;

    static void fallo(String texto) {
        mensaje = texto;
        fase = ERROR;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        info = Actualizador.pendiente(this);
        if (info == null) {
            finish();
            return;
        }
        Actualizador.pantallaAbierta = true;
        armar("Nueva actualización disponible", "Ivona TTS " + info.nombre);
        actualizar = boton("Actualizar", v -> pulsar());
        filaEstado = filaInfo("Estado", "");
        filaEstado.vista.setVisibility(View.GONE);
        barra = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        barra.setMax(100);
        barra.setVisibility(View.GONE);
        barra.setContentDescription("Progreso de la actualización");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(12));
        lp.setMargins(dp(24), dp(16), dp(24), dp(4));
        raiz.addView(barra, lp);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
            });
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
    }

    @Override
    protected void onDestroy() {
        Actualizador.pantallaAbierta = false;
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (info == null) {
            return;
        }
        if (Actualizador.pendiente(this) == null) {
            finish();
            return;
        }
        if (esperandoPermiso && getPackageManager().canRequestPackageInstalls()) {
            esperandoPermiso = false;
            pulsar();
        }
        ui.post(sondeo);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(sondeo);
        super.onPause();
    }

    private void pulsar() {
        if (fase == DESCARGANDO || fase == INSTALANDO) {
            return;
        }
        if (!getPackageManager().canRequestPackageInstalls()) {
            esperandoPermiso = true;
            mensaje = "Para instalar, activa el permiso Permitir de esta fuente y vuelve a esta pantalla.";
            fase = ERROR;
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            } catch (Throwable t) {
                mensaje = "No se pudo abrir el permiso de instalación. Actívalo en los ajustes de Android para esta aplicación.";
            }
            return;
        }
        fase = DESCARGANDO;
        hecho = 0;
        total = 0;
        mensaje = "Descargando la actualización";
        ultimoDecil = -1;
        final Actualizador.Info i = info;
        new Thread(() -> trabajar(i), "actualizar").start();
    }

    private void trabajar(Actualizador.Info i) {
        File apk = new File(getFilesDir(), "actualizacion.apk");
        try {
            if (!apk.exists() || !i.sha.equals(sha(apk))) {
                descargar(i, apk);
            }
            if (!i.sha.equals(sha(apk))) {
                apk.delete();
                throw new IOException("El archivo descargado no coincide con la versión publicada");
            }
            mensaje = "Preparando la instalación";
            fase = INSTALANDO;
            Actualizador.recordarNovedades(this, i);
            instalar(apk);
            mensaje = "Confirma la instalación cuando Android te lo pida";
        } catch (Throwable t) {
            fallo("No se pudo actualizar: " + t.getMessage() + ". Pulsa Actualizar para reintentar.");
        }
    }

    private static String sha(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) > 0) {
                md.update(b, 0, n);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte x : md.digest()) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }

    private void descargar(Actualizador.Info i, File apk) throws IOException {
        File parte = new File(apk.getPath() + ".part");
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(i.apk).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) {
                throw new IOException("El servidor respondió " + c.getResponseCode());
            }
            total = c.getContentLengthLong();
            if (total > 0 && getFilesDir().getUsableSpace() < total * 2) {
                throw new IOException("No hay espacio libre suficiente");
            }
            hecho = 0;
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(parte)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    hecho += n;
                }
            }
            if (total > 0 && parte.length() < total) {
                throw new IOException("La descarga se cortó");
            }
            apk.delete();
            if (!parte.renameTo(apk)) {
                throw new IOException("No se pudo guardar el archivo");
            }
        } finally {
            if (c != null) {
                c.disconnect();
            }
            parte.delete();
        }
    }

    private void instalar(File apk) throws IOException {
        PackageInstaller pi = getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        sp.setAppPackageName(getPackageName());
        sp.setSize(apk.length());
        if (Build.VERSION.SDK_INT >= 31) {
            sp.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }
        int id = pi.createSession(sp);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = s.openWrite("ivona_tts", 0, apk.length())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                s.fsync(out);
            }
            Intent r = new Intent(this, InstaladorReceiver.class);
            PendingIntent pend = PendingIntent.getBroadcast(this, id, r, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            s.commit(pend.getIntentSender());
        }
    }

    private final Runnable sondeo = new Runnable() {
        @Override
        public void run() {
            int f = fase;
            boolean ocupado = f == DESCARGANDO || f == INSTALANDO;
            actualizar.setEnabled(!ocupado);
            actualizar.setAlpha(ocupado ? 0.5f : 1f);
            if (f == LIBRE) {
                filaEstado.vista.setVisibility(View.GONE);
                barra.setVisibility(View.GONE);
            } else {
                filaEstado.vista.setVisibility(View.VISIBLE);
                String texto = mensaje;
                if (f == DESCARGANDO && total > 0) {
                    int pct = (int) (hecho * 100 / total);
                    texto = "Descargando. " + Medida.avance(hecho, total);
                    barra.setVisibility(View.VISIBLE);
                    barra.setIndeterminate(false);
                    barra.setProgress(pct);
                    if (pct / 10 != ultimoDecil) {
                        ultimoDecil = pct / 10;
                        anunciar(filaEstado.resumen, "Descargando, " + pct + " por ciento");
                    }
                } else if (f == DESCARGANDO) {
                    barra.setVisibility(View.VISIBLE);
                    barra.setIndeterminate(true);
                } else {
                    barra.setVisibility(View.GONE);
                }
                filaEstado.resumen(texto);
                if (!texto.equals(ultimoTexto) && f != DESCARGANDO) {
                    ultimoTexto = texto;
                    anunciar(filaEstado.resumen, texto);
                }
            }
            ui.postDelayed(this, 500);
        }
    };
}
