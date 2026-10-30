package com.ivona.tts;

import android.content.ClipData;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class CompartirProvider extends ContentProvider {

    static final String AUTORIDAD = "com.ivona.tts.providers.CompartirProvider";

    static File carpeta(Context c) {
        File f = new File(c.getCacheDir(), "compartir");
        f.mkdirs();
        return f;
    }

    static Uri escribir(Context c, String nombre, String texto) throws IOException {
        File destino = new File(carpeta(c), nombre);
        try (FileOutputStream o = new FileOutputStream(destino)) {
            o.write(texto.getBytes(StandardCharsets.UTF_8));
        }
        return new Uri.Builder().scheme("content").authority(AUTORIDAD).appendPath(nombre).build();
    }

    static Intent intentCompartir(Context c, String nombre, String texto, String asunto) throws IOException {
        Uri uri = escribir(c, nombre, texto);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.setClipData(ClipData.newRawUri(nombre, uri));
        if (asunto != null) {
            i.putExtra(Intent.EXTRA_SUBJECT, asunto);
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return i;
    }

    private File archivo(Uri uri) {
        String nombre = uri.getLastPathSegment();
        if (nombre == null || nombre.indexOf('/') >= 0 || nombre.indexOf('\\') >= 0 || nombre.contains("..")) {
            return null;
        }
        return new File(carpeta(getContext()), nombre);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "text/plain";
    }

    @Override
    public Cursor query(Uri uri, String[] proyeccion, String seleccion, String[] argumentos, String orden) {
        File f = archivo(uri);
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        if (f != null) {
            c.addRow(new Object[]{f.getName(), f.length()});
        }
        return c;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String modo) throws FileNotFoundException {
        File f = archivo(uri);
        if (f == null || !f.exists()) {
            throw new FileNotFoundException(String.valueOf(uri));
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues valores) {
        return null;
    }

    @Override
    public int delete(Uri uri, String seleccion, String[] argumentos) {
        File f = archivo(uri);
        return f != null && f.delete() ? 1 : 0;
    }

    @Override
    public int update(Uri uri, ContentValues valores, String seleccion, String[] argumentos) {
        return 0;
    }
}
