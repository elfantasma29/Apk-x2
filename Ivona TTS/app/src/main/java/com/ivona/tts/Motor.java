package com.ivona.tts;

import android.content.Context;
import android.system.Os;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

final class Motor {

    private static Motor unico;

    static synchronized Motor get(Context c) {
        if (unico == null) {
            unico = new Motor(c.getApplicationContext());
        }
        return unico;
    }

    private final Context ctx;
    private Servidor servidor;
    private Voces.Voz actual;
    volatile boolean paro;

    private Motor(Context c) {
        this.ctx = c;
        final Thread.UncaughtExceptionHandler previo = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((th, ex) -> {
            try {
                guardarFallo("hilo " + th.getName(), ex);
            } catch (Throwable ignored) {
            }
            if (previo != null) {
                previo.uncaughtException(th, ex);
            }
        });
    }

    private static volatile int memoriaBaja = -1;

    static boolean bajaMemoria(Context c) {
        if (memoriaBaja < 0) {
            boolean baja = false;
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
                android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                baja = am.isLowRamDevice() || mi.totalMem < 3600L * 1024L * 1024L;
            } catch (Throwable ignored) {
            }
            memoriaBaja = baja ? 1 : 0;
        }
        return memoriaBaja == 1;
    }

    synchronized boolean vivo() {
        return servidor != null && servidor.vivo();
    }

    void apagar() {
        detenerProceso();
    }

    void reiniciarSuave() {
        detenerProceso();
    }

    synchronized String estado() {
        if (servidor != null && servidor.vivo()) {
            return "Motor en marcha. Voz " + (actual == null ? "" : actual.nombre) + " " + servidor.detalles;
        }
        return Voces.instaladas(ctx).isEmpty() ? "No hay voces instaladas" : "Motor en reposo; arranca al leer";
    }

    void log(String s) {
        try {
            java.io.File f = archivoRegistro();
            if (f.length() > 300000) {
                f.delete();
            }
            try (FileOutputStream o = new FileOutputStream(f, true)) {
                o.write((s + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
        }
    }

    void guardarFallo(String donde, Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        String s = "FALLO en " + donde + ": " + sw;
        log(s);
        File archivo = new File(ctx.getFilesDir(), "fallo.txt");
        if (archivo.length() > 65536) {
            archivo.delete();
        }
        try (FileOutputStream f = new FileOutputStream(archivo, true)) {
            f.write((s + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }

    String falloPrevio() {
        File f = new File(ctx.getFilesDir(), "fallo.txt");
        if (!f.exists()) {
            return "";
        }
        try (InputStream in = new java.io.FileInputStream(f)) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) {
                bo.write(b, 0, n);
            }
            String s = new String(bo.toByteArray(), StandardCharsets.UTF_8);
            return s.length() > 6000 ? s.substring(s.length() - 6000) : s;
        } catch (Throwable t) {
            return "";
        }
    }

    void borrarFallos() {
        new File(ctx.getFilesDir(), "fallo.txt").delete();
    }

    String registroReciente() {
        File f = archivoRegistro();
        if (!f.exists()) {
            return "";
        }
        try (InputStream in = new java.io.FileInputStream(f)) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) {
                bo.write(b, 0, n);
            }
            String s = new String(bo.toByteArray(), StandardCharsets.UTF_8);
            return s.length() > 20000 ? s.substring(s.length() - 20000) : s;
        } catch (Throwable t) {
            return "";
        }
    }

    File archivoRegistro() {
        return new File(ctx.getFilesDir(), "registro.txt");
    }

    private String claveGuest() {
        try (ZipFile zf = new ZipFile(ctx.getApplicationInfo().sourceDir)) {
            ZipEntry e = zf.getEntry("assets/guest.zip");
            if (e != null) {
                return "g" + Long.toHexString(e.getCrc()) + "-" + e.getSize();
            }
        } catch (Throwable ignored) {
        }
        return "v1";
    }

    private static void borrar(File f) {
        File[] h = f.listFiles();
        if (h != null) {
            for (File x : h) {
                borrar(x);
            }
        }
        f.delete();
    }

    private File preparar() throws IOException {
        File destino = new File(ctx.getFilesDir(), "guest");
        String version = claveGuest();
        File marca = new File(destino, ".listo");
        boolean ok = false;
        if (marca.exists()) {
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(marca)))) {
                ok = version.equals(r.readLine());
            } catch (IOException ignored) {
            }
        }
        if (!ok) {
            borrar(destino);
            destino.mkdirs();
            try (InputStream in = ctx.getAssets().open("guest.zip"); ZipInputStream z = new ZipInputStream(in)) {
                ZipEntry e;
                byte[] buf = new byte[65536];
                String raiz = destino.getCanonicalPath() + File.separator;
                while ((e = z.getNextEntry()) != null) {
                    File out = new File(destino, e.getName());
                    if (!out.getCanonicalPath().startsWith(raiz)) {
                        continue;
                    }
                    if (e.isDirectory()) {
                        out.mkdirs();
                        continue;
                    }
                    out.getParentFile().mkdirs();
                    try (OutputStream o = new FileOutputStream(out)) {
                        int n;
                        while ((n = z.read(buf)) > 0) {
                            o.write(buf, 0, n);
                        }
                    }
                }
            }
            File lib = new File(destino, "lib");
            File sys = new File(destino, "sysroot/lib");
            String[][] enlaces = {
                    {"libc.so", "libc.so.6"}, {"libdl.so", "libdl.so.2"}, {"libm.so", "libm.so.6"},
                    {"libstdc++.so", "libstdc++.so.6"}
            };
            for (String[] p : enlaces) {
                File l = new File(lib, p[0]);
                l.delete();
                try {
                    Os.symlink(new File(sys, p[1]).getAbsolutePath(), l.getAbsolutePath());
                } catch (Throwable t) {
                    throw new IOException("No se pudo crear el enlace " + p[0] + ": " + t);
                }
            }
            try (FileOutputStream f = new FileOutputStream(marca)) {
                f.write((version + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        new File(destino, "ivona_server").setExecutable(true, false);
        return destino;
    }

    private Servidor segundo;
    private Voces.Voz actualSegundo;
    private boolean arrancandoSegundo = false;

    private Servidor abrir(Voces.Voz v, String[] error) {
        try {
            File guest = preparar();
            File voz = Voces.carpeta(ctx, v);
            if (!new File(voz, ".listo").exists()) {
                error[0] = "La voz " + v.nombre + " no está instalada.";
                return null;
            }
            File qemu = new File(ctx.getApplicationInfo().nativeLibraryDir, "libqemu.so");
            List<String> cmd = new ArrayList<>();
            cmd.add(qemu.getAbsolutePath());
            cmd.add("-L");
            cmd.add(new File(guest, "sysroot").getAbsolutePath());
            cmd.add("-E");
            cmd.add("LD_LIBRARY_PATH=" + new File(guest, "lib").getAbsolutePath());
            cmd.add(new File(guest, "ivona_server").getAbsolutePath());
            cmd.add(new File(guest, "lib/libIvonaProxy.so").getAbsolutePath());
            cmd.add(new File(voz, "lib").getAbsolutePath());
            cmd.add(new File(voz, "data").getAbsolutePath());
            Servidor s = new Servidor(cmd, archivoRegistro());
            String r = s.iniciar();
            if (r != null) {
                error[0] = r;
                return null;
            }
            return s;
        } catch (Throwable t) {
            error[0] = "No se pudo preparar el motor: " + t;
            return null;
        }
    }

    synchronized String asegurar(Voces.Voz v) {
        if (servidor != null && actual == v && servidor.vivo()) {
            return null;
        }
        detenerProceso();
        String[] e = new String[1];
        Servidor s = abrir(v, e);
        if (s == null) {
            return e[0];
        }
        servidor = s;
        actual = v;
        return null;
    }

    void prepararSegundo(final Voces.Voz v) {
        synchronized (this) {
            if (arrancandoSegundo || bajaMemoria(ctx)) {
                return;
            }
            if (segundo != null && segundo.vivo() && actualSegundo == v) {
                return;
            }
            arrancandoSegundo = true;
        }
        Thread t = new Thread(() -> {
            try {
                String[] e = new String[1];
                Servidor s = abrir(v, e);
                synchronized (Motor.this) {
                    if (s != null && actual == v) {
                        if (segundo != null) {
                            segundo.cerrar();
                        }
                        segundo = s;
                        actualSegundo = v;
                    } else if (s != null) {
                        s.cerrar();
                    }
                }
            } finally {
                synchronized (Motor.this) {
                    arrancandoSegundo = false;
                }
            }
        }, "ivona-segundo");
        t.setDaemon(true);
        t.start();
    }

    boolean segundoListo() {
        synchronized (this) {
            return segundo != null && segundo.vivo() && actualSegundo == actual;
        }
    }

    int hablarRapido(String texto, Servidor.Salida salida, int[] trozos) {
        Servidor a;
        Servidor b;
        synchronized (this) {
            a = servidor;
            b = segundo != null && segundo.vivo() && actualSegundo == actual ? segundo : null;
        }
        if (a == null) {
            return -1;
        }
        List<String> partes = b == null ? java.util.Collections.singletonList(texto) : Rapido.dividir(texto);
        if (trozos != null) {
            trozos[0] = partes.size();
        }
        return Rapido.ejecutar(a, b, partes, salida);
    }

    String prioridadResultado() {
        Servidor s;
        synchronized (this) {
            s = servidor;
        }
        return s == null ? "" : s.resultadoPrioridad;
    }

    String detalles() {
        Servidor s = servidor;
        return s == null ? "" : s.detalles;
    }

    int hablar(String texto, Servidor.Salida salida) {
        Servidor s;
        synchronized (this) {
            s = servidor;
        }
        if (s == null) {
            return -1;
        }
        return s.hablar(texto, salida);
    }

    void detener() {
        Servidor s;
        synchronized (this) {
            s = servidor;
        }
        if (s != null) {
            s.detener();
        }
        Servidor b;
        synchronized (this) {
            b = segundo;
        }
        if (b != null && b.ocupado) {
            b.detener();
        }
    }

    synchronized void detenerProceso() {
        if (servidor != null) {
            servidor.cerrar();
        }
        if (segundo != null) {
            segundo.cerrar();
        }
        servidor = null;
        segundo = null;
        actual = null;
        actualSegundo = null;
    }

    synchronized void olvidar(Voces.Voz v) {
        if (actual == v) {
            detenerProceso();
        }
    }
}
