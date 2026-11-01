package com.ivona.tts;

import android.os.Bundle;

import java.util.List;

public class AvisosActivity extends Pantalla {

    private long maximoMostrado = 0;
    private boolean sinConexion = false;
    private boolean consultando = true;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("Avisos");
    }

    @Override
    protected void onResume() {
        super.onResume();
        consultando = true;
        construir();
        Avisos.consultarAsync(this, true, ok -> ui.post(() -> {
            consultando = false;
            sinConexion = !ok;
            if (!isFinishing() && !isDestroyed()) {
                construir();
            }
        }));
    }

    @Override
    protected void onPause() {
        if (maximoMostrado > 0) {
            Avisos.vistos(this, maximoMostrado);
        }
        super.onPause();
    }

    private void construir() {
        long visto = getSharedPreferences("estado", MODE_PRIVATE).getLong("avisos_visto", 0);
        List<Avisos.Aviso> lista = Avisos.lista(this);
        armar("Avisos", lista.isEmpty() ? "Sin mensajes" : (lista.size() == 1 ? "1 mensaje" : lista.size() + " mensajes"));
        if (lista.isEmpty()) {
            nota("No hay avisos por ahora.");
        } else {
            maximoMostrado = Math.max(maximoMostrado, lista.get(0).id);
            seccion("Mensajes del desarrollador");
            for (Avisos.Aviso a : lista) {
                StringBuilder t = new StringBuilder();
                if (a.id > visto) {
                    t.append("Nuevo. ");
                }
                if (a.importante) {
                    t.append("Importante. ");
                }
                t.append(a.titulo.isEmpty() ? "Aviso " + a.id : a.titulo);
                filaInfo(t.toString(), a.texto + (a.fecha.isEmpty() ? "" : "\n" + a.fecha));
            }
        }
        if (sinConexion && !consultando) {
            nota("No se pudo comprobar si hay avisos nuevos. Se muestran los últimos guardados.");
        }
        boton(consultando ? "Buscando avisos nuevos" : "Buscar avisos nuevos", v -> {
            if (consultando) {
                return;
            }
            consultando = true;
            construir();
            Avisos.consultarAsync(this, true, ok -> ui.post(() -> {
                consultando = false;
                sinConexion = !ok;
                if (!isFinishing() && !isDestroyed()) {
                    construir();
                    breve(ok ? "Avisos al día" : "No se pudo comprobar. Revisa tu conexión");
                }
            }));
        });
    }
}
