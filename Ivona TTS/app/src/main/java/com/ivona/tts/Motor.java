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

    synchronized String asegurar(Voces.Voz v) {
        if (servidor != null && actual == v && servidor.vivo()) {
            return null;
        }
        detenerProceso();
        try {
            File guest = preparar();
            File voz = Voces.carpeta(ctx, v);
            if (!new File(voz, ".listo").exists()) {
                return "La voz " + v.nombre + " no está instalada.";
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
                return r;
            }
            servidor = s;
            actual = v;
            return null;
        } catch (Throwable t) {
            return "No se pudo preparar el motor: " + t;
        }
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
    }

    synchronized void detenerProceso() {
        if (servidor != null) {
            servidor.cerrar();
        }
        servidor = null;
        actual = null;
    }

    synchronized void olvidar(Voces.Voz v) {
        if (actual == v) {
            detenerProceso();
        }
    }
}
