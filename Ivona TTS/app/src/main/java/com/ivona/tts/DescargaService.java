package com.ivona.tts;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.IBinder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DescargaService extends Service {

    static final String DESCARGAR = "com.ivona.tts.DESCARGAR";
    static final String INSTALAR = "com.ivona.tts.INSTALAR";
    private static final String CANAL = "descargas";
    private static final int ID = 7;

    static volatile boolean activo;
    static volatile boolean cancelar;
    static volatile String clave = "";
    static volatile String fase = "";
    static volatile String mensaje = "";
    static volatile String resultado = "";
    static volatile long hecho;
    static volatile long total;

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        if (i == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (activo) {
            return START_NOT_STICKY;
        }
        final Voces.Voz v = Voces.porClave(i.getStringExtra("clave"));
        if (v == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        final boolean instalar = INSTALAR.equals(i.getAction());
        final Uri origen = i.getData();
        activo = true;
        cancelar = false;
        clave = v.clave;
        fase = instalar ? "Instalando" : "Descargando";
        mensaje = "";
        resultado = "";
        hecho = 0;
        total = 0;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CANAL, "Descargas de voces", NotificationManager.IMPORTANCE_LOW));
        startForeground(ID, notificacion(fase + " " + v.nombre, "Preparando"), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        new Thread(() -> trabajar(v, instalar, origen), "ivona-descarga").start();
        return START_NOT_STICKY;
    }

    private Notification notificacion(String titulo, String texto) {
        Intent abrir = new Intent(this, MainActivity.class);
        abrir.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 1, abrir, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CANAL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void aviso(String titulo, String texto) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        Intent abrir = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 2, abrir, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        nm.notify(ID + 1, new Notification.Builder(this, CANAL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build());
    }

    private void progreso(String titulo) {
        getSystemService(NotificationManager.class).notify(ID, notificacion(titulo, Texto.avance(hecho, total)));
    }

    private void trabajar(Voces.Voz v, boolean instalar, Uri origen) {
        File zip = new File(Voces.temporal(this), v.clave + ".zip");
        File[] partes = new File[v.partes.length];
        for (int k = 0; k < partes.length; k++) {
            partes[k] = new File(Voces.temporal(this), v.clave + ".p" + (k + 1));
        }
        try {
            File[] entrada;
            if (instalar) {
                fase = "Copiando";
                copiar(origen, zip);
                entrada = new File[]{zip};
            } else {
                descargar(v, partes);
                entrada = partes;
            }
            if (cancelar) {
                throw new IOException("Cancelado");
            }
            fase = "Instalando";
            mensaje = "Descomprimiendo la voz " + v.nombre;
            hecho = 0;
            total = 0;
            extraer(v, entrada);
            zip.delete();
            for (File f : partes) {
                f.delete();
            }
            Motor.get(this).olvidar(v);
            fase = "Listo";
            mensaje = "Voz instalada: " + v.completo();
            resultado = "ok";
            aviso("Voz instalada", v.completo());
        } catch (Throwable t) {
            boolean c = cancelar;
            fase = c ? "Cancelado" : "Error";
            mensaje = c ? "Cancelado" : "No se pudo completar: " + t.getMessage();
            resultado = c ? "cancelado" : "error";
            for (File f : partes) {
                new File(f.getPath() + ".part").delete();
            }
            new File(zip.getPath() + ".part").delete();
            if (c || t instanceof java.util.zip.ZipException) {
                zip.delete();
                for (File f : partes) {
                    f.delete();
                }
            }
            if (!c) {
                aviso("No se pudo instalar la voz " + v.nombre, mensaje);
            }
        } finally {
            activo = false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void copiar(Uri origen, File zip) throws IOException {
        if (origen == null) {
            throw new IOException("No se recibió el archivo");
        }
        try (InputStream in = getContentResolver().openInputStream(origen); OutputStream out = new FileOutputStream(zip)) {
            if (in == null) {
                throw new IOException("No se pudo abrir el archivo");
            }
            byte[] b = new byte[65536];
            int n;
            long ultimo = 0;
            while ((n = in.read(b)) > 0) {
                if (cancelar) {
                    throw new IOException("Cancelado");
                }
                out.write(b, 0, n);
                hecho += n;
                long ahora = System.currentTimeMillis();
                if (ahora - ultimo > 1000) {
                    ultimo = ahora;
                    mensaje = "Copiando " + Texto.avance(hecho, 0);
                }
            }
        }
    }

    private void descargar(Voces.Voz v, File[] destinos) throws IOException {
        long granTotal = v.total();
        total = granTotal;
        long acumulado = 0;
        for (int k = 0; k < destinos.length; k++) {
            File dest = destinos[k];
            long esperado = v.partes[k];
            if (dest.exists() && dest.length() == esperado) {
                acumulado += esperado;
                hecho = acumulado;
                continue;
            }
            if (granTotal > 0 && getFilesDir().getUsableSpace() < (granTotal - acumulado) + granTotal + 50L * 1048576) {
                throw new IOException("No hay espacio libre suficiente. Se necesitan unos " + (granTotal * 2 / 1048576 + 50) + " MB.");
            }
            bajarParte(v, k, dest, esperado, acumulado);
            acumulado += esperado;
            hecho = acumulado;
        }
    }

    private void bajarParte(Voces.Voz v, int k, File dest, long esperado, long acumulado) throws IOException {
        File parte = new File(dest.getPath() + ".part");
        String nombre = Voces.BASE + v.archivo + String.format(Locale.ROOT, ".%03d", k + 1);
        int intentos = 0;
        while (true) {
            long previo = parte.exists() ? parte.length() : 0;
            if (previo > esperado) {
                parte.delete();
                previo = 0;
            }
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(nombre).openConnection();
                c.setConnectTimeout(20000);
                c.setReadTimeout(30000);
                c.setInstanceFollowRedirects(true);
                if (previo > 0) {
                    c.setRequestProperty("Range", "bytes=" + previo + "-");
                }
                int code = c.getResponseCode();
                if (code != 200 && code != 206) {
                    throw new IOException("El servidor respondió " + code);
                }
                boolean sigue = code == 206 && previo > 0;
                if (!sigue) {
                    previo = 0;
                }
                hecho = acumulado + previo;
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(parte, sigue)) {
                    byte[] b = new byte[65536];
                    int n;
                    long ultimo = 0;
                    while ((n = in.read(b)) > 0) {
                        if (cancelar) {
                            throw new IOException("Cancelado");
                        }
                        out.write(b, 0, n);
                        hecho += n;
                        long ahora = System.currentTimeMillis();
                        if (ahora - ultimo > 1000) {
                            ultimo = ahora;
                            mensaje = "Descargando " + v.nombre + ", parte " + (k + 1) + " de " + v.partes.length + ", " + Texto.avance(hecho, total);
                            progreso("Descargando " + v.nombre);
                        }
                    }
                }
                if (parte.length() != esperado) {
                    throw new IOException("La descarga se cortó");
                }
                if (!parte.renameTo(dest)) {
                    throw new IOException("No se pudo guardar el archivo descargado");
                }
                return;
            } catch (IOException ex) {
                String m = String.valueOf(ex.getMessage());
                if (cancelar || intentos >= 5 || m.startsWith("No hay espacio")) {
                    throw ex;
                }
                intentos++;
                mensaje = "Reintentando (" + intentos + " de 5)";
                try {
                    Thread.sleep(3000L * intentos);
                } catch (InterruptedException ignored) {
                }
            } finally {
                if (c != null) {
                    c.disconnect();
                }
            }
        }
    }

    private void extraer(Voces.Voz v, File[] archivos) throws IOException {
        File destino = Voces.carpeta(this, v);
        File tmp = new File(Voces.raiz(this), v.clave + ".instalando");
        borrar(tmp);
        borrar(destino);
        tmp.mkdirs();
        String raiz = tmp.getCanonicalPath() + File.separator;
        total = 0;
        for (File f : archivos) {
            total += f.length();
        }
        boolean hayLib = false;
        boolean hayVox = false;
        java.util.Vector<InputStream> flujos = new java.util.Vector<>();
        for (File f : archivos) {
            flujos.add(new FileInputStream(f));
        }
        try (ZipInputStream z = new ZipInputStream(new java.io.BufferedInputStream(new java.io.SequenceInputStream(flujos.elements()), 65536))) {
            ZipEntry e;
            byte[] b = new byte[65536];
            long ultimo = 0;
            while ((e = z.getNextEntry()) != null) {
                String nombre = e.getName();
                if (!(nombre.startsWith("lib/") || nombre.startsWith("data/"))) {
                    continue;
                }
                File out = new File(tmp, nombre);
                if (!out.getCanonicalPath().startsWith(raiz)) {
                    continue;
                }
                if (e.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                out.getParentFile().mkdirs();
                if (nombre.startsWith("lib/") && nombre.contains("libvoice_")) {
                    hayLib = true;
                }
                if (nombre.startsWith("data/vox_")) {
                    hayVox = true;
                }
                try (OutputStream o = new FileOutputStream(out)) {
                    int n;
                    while ((n = z.read(b)) > 0) {
                        if (cancelar) {
                            throw new IOException("Cancelado");
                        }
                        o.write(b, 0, n);
                        hecho += n;
                        long ahora = System.currentTimeMillis();
                        if (ahora - ultimo > 1000) {
                            ultimo = ahora;
                            mensaje = "Descomprimiendo " + v.nombre;
                        }
                    }
                }
            }
        }
        if (!hayLib || !hayVox) {
            borrar(tmp);
            throw new IOException("El archivo no es un paquete válido de la voz " + v.nombre);
        }
        try (FileOutputStream f = new FileOutputStream(new File(tmp, ".listo"))) {
            f.write("ok\n".getBytes("UTF-8"));
        }
        if (!tmp.renameTo(destino)) {
            borrar(tmp);
            throw new IOException("No se pudo terminar la instalación");
        }
    }

    static void borrar(File f) {
        File[] h = f.listFiles();
        if (h != null) {
            for (File x : h) {
                borrar(x);
            }
        }
        f.delete();
    }

    static String texto() {
        return String.format(Locale.ROOT, "%s %s", fase, mensaje);
    }
}
