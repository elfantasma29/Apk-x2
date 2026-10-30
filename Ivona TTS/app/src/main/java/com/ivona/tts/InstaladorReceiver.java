package com.ivona.tts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

public class InstaladorReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intento) {
        int estado = intento.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (estado == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmar = intento.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            if (confirmar != null) {
                confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    c.startActivity(confirmar);
                } catch (Throwable t) {
                    ActualizarActivity.fallo("No se pudo abrir la confirmación de instalación");
                }
            }
            return;
        }
        if (estado == PackageInstaller.STATUS_SUCCESS) {
            return;
        }
        String msg = intento.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if (estado == PackageInstaller.STATUS_FAILURE_ABORTED) {
            ActualizarActivity.fallo("La instalación no se confirmó. Pulsa Actualizar para intentarlo de nuevo.");
        } else {
            ActualizarActivity.fallo("No se pudo instalar la actualización" + (msg == null ? "" : ": " + msg));
        }
    }
}
