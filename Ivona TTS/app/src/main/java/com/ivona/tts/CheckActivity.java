package com.ivona.tts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.TreeSet;

public class CheckActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String accion = getIntent().getAction();
        if ("android.speech.tts.engine.GET_SAMPLE_TEXT".equals(accion)) {
            Intent r = new Intent();
            r.putExtra("sampleText", "Hola, esta es una prueba de la voz de Ivona.");
            setResult(0, r);
            finish();
            return;
        }
        if ("android.speech.tts.engine.INSTALL_TTS_DATA".equals(accion)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        TreeSet<String> set = new TreeSet<>();
        for (Voces.Voz v : Voces.instaladas(this)) {
            set.add("spa");
            set.add("spa-" + v.pais3);
        }
        ArrayList<String> disp = new ArrayList<>(set);
        Intent r = new Intent();
        r.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, disp);
        r.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, new ArrayList<String>());
        setResult(disp.isEmpty() ? TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL : TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, r);
        finish();
    }
}
