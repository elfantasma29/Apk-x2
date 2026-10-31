package com.ivona.tts;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;

final class Servidor {

    interface Salida {
        void inicio(int frecuencia);

        void audio(byte[] datos, int largo);

        boolean cancelado();
    }

    private final List<String> comando;
    private final File registro;
    private Process proceso;
    private OutputStream entrada;
    private InputStream salida;
    private final Object escritura = new Object();
    private final Object uso = new Object();
    private final ArrayDeque<String> errores = new ArrayDeque<>();
    private volatile boolean vivo;
    private volatile boolean listo;
    String detalles = "";
    static volatile boolean prioridadTotal = false;
    volatile boolean ocupado = false;
    private int pid = -1;
    private long ultimaPrioridad = 0;
    volatile String resultadoPrioridad = "";

    void priorizar() {
        if (pid <= 0) {
            return;
        }
        long ahora = System.currentTimeMillis();
        if (ahora - ultimaPrioridad < 5000) {
            return;
        }
        ultimaPrioridad = ahora;
        int bien = 0;
        int total = 0;
        try {
            File[] hilos = new File("/proc/" + pid + "/task").listFiles();
            if (hilos != null) {
                for (File h : hilos) {
                    total++;
                    try {
                        android.os.Process.setThreadPriority(Integer.parseInt(h.getName()), android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                        bien++;
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        String r = "Prioridad alta en " + bien + " de " + total + " hilos del motor";
        if (!r.equals(resultadoPrioridad)) {
            resultadoPrioridad = r;
            anotar(r);
        }
    }

    Servidor(List<String> comando, File registro) {
        this.comando = comando;
        this.registro = registro;
    }

    boolean vivo() {
        return vivo && proceso != null && proceso.isAlive();
    }

    private String ultimosErrores() {
        synchronized (errores) {
            StringBuilder sb = new StringBuilder();
            for (String s : errores) {
                sb.append(s).append('\n');
            }
            return sb.toString().trim();
        }
    }

    private void anotar(String linea) {
        synchronized (errores) {
            errores.addLast(linea);
            while (errores.size() > 25) {
                errores.removeFirst();
            }
        }
        if (registro != null) {
            try {
                if (registro.length() > 300000) {
                    registro.delete();
                }
                try (FileOutputStream f = new FileOutputStream(registro, true)) {
                    f.write((linea + "\n").getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
            }
        }
    }

    String iniciar() {
        try {
            anotar("Lanzando: " + comando);
            final Process p = new ProcessBuilder(comando).start();
            proceso = p;
            try {
                java.lang.reflect.Field fp = p.getClass().getDeclaredField("pid");
                fp.setAccessible(true);
                pid = fp.getInt(p);
                android.os.Process.setThreadPriority(pid, android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            } catch (Throwable ignored) {
            }
            entrada = p.getOutputStream();
            salida = new BufferedInputStream(p.getInputStream(), 65536);
            final InputStream err = p.getErrorStream();
            Thread te = new Thread(() -> {
                try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(err, StandardCharsets.UTF_8))) {
                    String l;
                    while ((l = r.readLine()) != null) {
                        anotar("[motor] " + l);
                    }
                } catch (IOException ignored) {
                }
            }, "ivona-err");
            te.setDaemon(true);
            te.start();
            Thread guardia = new Thread(() -> {
                try {
                    Thread.sleep(120000);
                } catch (InterruptedException ignored) {
                }
                if (!listo && p.isAlive()) {
                    p.destroyForcibly();
                }
            }, "ivona-guardia");
            guardia.setDaemon(true);
            guardia.start();
            String l = leerLinea();
            if (l == null) {
                cerrar();
                String e = ultimosErrores();
                return "El motor se cerró al arrancar." + (e.isEmpty() ? "" : "\n" + e);
            }
            String[] f = l.split("\t", -1);
            if (f.length >= 2 && "R".equals(f[0]) && "READY".equals(f[1])) {
                detalles = f.length > 2 ? f[2] : "";
                listo = true;
                vivo = true;
                if (prioridadTotal) {
                    priorizar();
                }
                return null;
            }
            cerrar();
            return "El motor no pudo cargar la voz: " + l.replace('\t', ' ');
        } catch (Throwable t) {
            cerrar();
            return "No se pudo iniciar el motor: " + t;
        }
    }

    private String leerLinea() throws IOException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int c = salida.read();
            if (c < 0) {
                return null;
            }
            if (c == '\n') {
                return sb.toString();
            }
            if (sb.length() < 4096) {
                sb.append((char) (c & 0xff));
            }
        }
    }

    private boolean leerExacto(byte[] b, int n) throws IOException {
        int g = 0;
        while (g < n) {
            int r = salida.read(b, g, n - g);
            if (r < 0) {
                return false;
            }
            g += r;
        }
        return true;
    }

    private void enviar(byte[] datos) throws IOException {
        synchronized (escritura) {
            entrada.write(datos);
            entrada.flush();
        }
    }

    int hablar(String texto, Salida s) {
        synchronized (uso) {
            if (!vivo()) {
                return -2;
            }
            if (prioridadTotal) {
                priorizar();
            }
            ocupado = true;
            try {
                byte[] t = texto.getBytes(StandardCharsets.UTF_8);
                byte[] cab = ("SPEAK\t" + t.length + "\n").getBytes(StandardCharsets.UTF_8);
                byte[] todo = new byte[cab.length + t.length];
                System.arraycopy(cab, 0, todo, 0, cab.length);
                System.arraycopy(t, 0, todo, cab.length, t.length);
                enviar(todo);
                boolean paro = false;
                while (true) {
                    String l = leerLinea();
                    if (l == null) {
                        vivo = false;
                        anotar("El motor se cerró durante la síntesis. " + ultimosErrores());
                        return -3;
                    }
                    String[] f = l.split("\t", -1);
                    if ("S".equals(f[0]) && f.length > 1) {
                        s.inicio(Integer.parseInt(f[1].trim()));
                    } else if ("A".equals(f[0]) && f.length > 1) {
                        int n = Integer.parseInt(f[1].trim());
                        byte[] b = new byte[n];
                        if (!leerExacto(b, n)) {
                            vivo = false;
                            return -3;
                        }
                        if (s.cancelado()) {
                            if (!paro) {
                                paro = true;
                                detener();
                            }
                        } else {
                            s.audio(b, n);
                        }
                    } else if ("R".equals(f[0]) && f.length > 2 && "SPEAK".equals(f[1])) {
                        return Integer.parseInt(f[2].trim());
                    }
                }
            } catch (Throwable e) {
                vivo = false;
                anotar("Fallo al hablar: " + e);
                return -4;
            } finally {
                ocupado = false;
            }
        }
    }

    void detener() {
        try {
            if (entrada != null) {
                enviar("STOP\n".getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
        }
    }

    void cerrar() {
        vivo = false;
        try {
            if (entrada != null) {
                enviar("QUIT\n".getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
        }
        if (proceso != null) {
            proceso.destroyForcibly();
        }
    }
}
