package com.ivona.tts;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

final class Rapido {

    static final int FRECUENCIA = 22050;
    private static final int UMBRAL = 500;
    static final int PAUSA_MS = 260;

    private Rapido() {
    }

    static List<String> dividir(String t) {
        ArrayList<String> r = new ArrayList<>();
        if (t.length() < 60) {
            r.add(t);
            return r;
        }
        StringBuilder cur = new StringBuilder();
        int n = t.length();
        for (int i = 0; i < n; i++) {
            char c = t.charAt(i);
            cur.append(c);
            boolean fin = c == '\n' || ((c == '.' || c == '!' || c == '?' || c == '…') && (i + 1 >= n || Character.isWhitespace(t.charAt(i + 1))));
            if (fin && cur.toString().trim().length() >= 25) {
                r.add(cur.toString().trim());
                cur.setLength(0);
            }
        }
        String resto = cur.toString().trim();
        if (!resto.isEmpty()) {
            if (resto.length() < 25 && !r.isEmpty()) {
                r.set(r.size() - 1, r.get(r.size() - 1) + " " + resto);
            } else {
                r.add(resto);
            }
        }
        if (r.isEmpty()) {
            r.add(t);
        }
        while (r.size() > 6) {
            int k = r.size() - 2;
            r.set(k, r.get(k) + " " + r.get(k + 1));
            r.remove(k + 1);
        }
        return r;
    }

    static final class Recorte {
        private final int cabeza;
        private final int cola;
        private boolean iniciado = false;
        private int corridaInicial = 0;
        private final ByteArrayOutputStream pendiente = new ByteArrayOutputStream();
        private int impar = -1;

        Recorte(int cabezaMs, int colaMs) {
            this.cabeza = cabezaMs * FRECUENCIA / 1000;
            this.cola = colaMs * FRECUENCIA / 1000;
        }

        byte[] procesar(byte[] d, int largo) {
            ByteArrayOutputStream sal = new ByteArrayOutputStream(largo);
            int i = 0;
            if (impar >= 0 && largo > 0) {
                muestra(sal, (byte) impar, d[0]);
                impar = -1;
                i = 1;
            }
            while (i + 1 < largo) {
                muestra(sal, d[i], d[i + 1]);
                i += 2;
            }
            if (i < largo) {
                impar = d[i] & 0xff;
            }
            return sal.toByteArray();
        }

        private void muestra(ByteArrayOutputStream sal, byte lo, byte hi) {
            int v = (short) ((lo & 0xff) | (hi << 8));
            boolean fuerte = v > UMBRAL || v < -UMBRAL;
            if (!iniciado) {
                if (!fuerte) {
                    corridaInicial++;
                    return;
                }
                iniciado = true;
                int guarda = Math.min(cabeza, corridaInicial);
                for (int k = 0; k < guarda; k++) {
                    sal.write(0);
                    sal.write(0);
                }
                sal.write(lo);
                sal.write(hi);
                return;
            }
            if (fuerte) {
                if (pendiente.size() > 0) {
                    byte[] p = pendiente.toByteArray();
                    sal.write(p, 0, p.length);
                    pendiente.reset();
                }
                sal.write(lo);
                sal.write(hi);
            } else {
                pendiente.write(lo);
                pendiente.write(hi);
                if (pendiente.size() > 4 * FRECUENCIA) {
                    byte[] p = pendiente.toByteArray();
                    sal.write(p, 0, p.length);
                    pendiente.reset();
                }
            }
        }

        byte[] fin() {
            byte[] p = pendiente.toByteArray();
            pendiente.reset();
            int conservar = Math.min(p.length, cola * 2);
            byte[] r = new byte[conservar];
            System.arraycopy(p, 0, r, 0, conservar);
            return r;
        }
    }

    private static final class Trozo {
        final LinkedList<byte[]> cola = new LinkedList<>();
        boolean listo = false;
        int rc = 0;
        int frecuencia = FRECUENCIA;
    }

    static int ejecutar(Servidor a, Servidor b, List<String> partes, final Servidor.Salida out) {
        final int n = partes.size();
        final Trozo[] trozos = new Trozo[n];
        for (int i = 0; i < n; i++) {
            trozos[i] = new Trozo();
        }
        final Object m = new Object();
        final boolean[] cancelado = {false};
        final int[] siguiente = {0};
        final List<String> texto = partes;
        List<Thread> hilos = new ArrayList<>();
        Servidor[] motores = b == null ? new Servidor[]{a} : new Servidor[]{a, b};
        for (final Servidor srv : motores) {
            Thread t = new Thread(() -> {
                while (true) {
                    final int idx;
                    synchronized (m) {
                        if (cancelado[0] || siguiente[0] >= n) {
                            return;
                        }
                        idx = siguiente[0]++;
                    }
                    final Trozo tr = trozos[idx];
                    final Recorte rec = new Recorte(15, idx == n - 1 ? 30 : 60);
                    int r = srv.hablar(texto.get(idx), new Servidor.Salida() {
                        @Override
                        public void inicio(int frecuencia) {
                            synchronized (m) {
                                tr.frecuencia = frecuencia;
                            }
                        }

                        @Override
                        public void audio(byte[] datos, int largo) {
                            byte[] o = rec.procesar(datos, largo);
                            if (o.length > 0) {
                                synchronized (m) {
                                    tr.cola.add(o);
                                    m.notifyAll();
                                }
                            }
                        }

                        @Override
                        public boolean cancelado() {
                            return cancelado[0] || out.cancelado();
                        }
                    });
                    byte[] resto = rec.fin();
                    synchronized (m) {
                        if (resto.length > 0) {
                            tr.cola.add(resto);
                        }
                        tr.rc = r;
                        tr.listo = true;
                        m.notifyAll();
                    }
                }
            }, "ivona-trozo");
            t.setDaemon(true);
            hilos.add(t);
            t.start();
        }
        boolean iniciado = false;
        int rcFinal = 0;
        boolean emitio = false;
        try {
            for (int i = 0; i < n && !cancelado[0]; i++) {
                final Trozo tr = trozos[i];
                boolean pausado = false;
                while (true) {
                    byte[] d = null;
                    boolean fin = false;
                    synchronized (m) {
                        while (tr.cola.isEmpty() && !tr.listo && !cancelado[0]) {
                            try {
                                m.wait(40);
                            } catch (InterruptedException e) {
                                cancelado[0] = true;
                            }
                            if (out.cancelado()) {
                                cancelado[0] = true;
                            }
                        }
                        if (!tr.cola.isEmpty()) {
                            d = tr.cola.removeFirst();
                        } else if (tr.listo) {
                            fin = true;
                        }
                    }
                    if (cancelado[0]) {
                        break;
                    }
                    if (d != null) {
                        if (i > 0 && !pausado) {
                            pausado = true;
                            byte[] silencio = new byte[PAUSA_MS * FRECUENCIA / 1000 * 2];
                            out.audio(silencio, silencio.length);
                        }
                        if (!iniciado) {
                            iniciado = true;
                            out.inicio(tr.frecuencia);
                        }
                        emitio = true;
                        out.audio(d, d.length);
                        if (out.cancelado()) {
                            cancelado[0] = true;
                            break;
                        }
                    } else if (fin) {
                        if (tr.rc != 0 && rcFinal == 0) {
                            rcFinal = tr.rc;
                        }
                        break;
                    }
                }
            }
        } finally {
            if (cancelado[0]) {
                if (a.ocupado) {
                    a.detener();
                }
                if (b != null && b.ocupado) {
                    b.detener();
                }
            }
            for (Thread t : hilos) {
                try {
                    t.join(cancelado[0] ? 1500 : 20000);
                } catch (InterruptedException ignored) {
                }
            }
        }
        if (cancelado[0]) {
            return emitio ? 0 : rcFinal;
        }
        return rcFinal;
    }
}
