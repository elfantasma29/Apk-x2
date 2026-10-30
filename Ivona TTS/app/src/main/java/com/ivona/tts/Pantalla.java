package com.ivona.tts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

abstract class Pantalla extends Activity {

    interface Valor {
        void con(boolean v);
    }

    interface Numero {
        void con(float v);
    }

    final Handler ui = new Handler(Looper.getMainLooper());
    LinearLayout raiz;
    private LinearLayout marco;
    private ScrollView desplazador;
    private LinearLayout tarjeta;
    private int scrollGuardado = 0;

    static final class Fila {
        final LinearLayout vista;
        final TextView titulo;
        final TextView resumen;
        final Switch interruptor;

        Fila(LinearLayout vista, TextView titulo, TextView resumen, Switch interruptor) {
            this.vista = vista;
            this.titulo = titulo;
            this.resumen = resumen;
            this.interruptor = interruptor;
        }

        void resumen(String s) {
            resumen.setText(s);
            resumen.setVisibility(s == null || s.length() == 0 ? View.GONE : View.VISIBLE);
        }

        void marcar(boolean v) {
            if (interruptor != null) {
                interruptor.setChecked(v);
                vista.invalidate();
            }
        }

        void activa(boolean v) {
            vista.setEnabled(v);
            vista.setAlpha(v ? 1f : 0.5f);
        }
    }

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    int color(int id) {
        return getColor(id);
    }

    void armar(String titulo) {
        armar(titulo, null);
    }

    void armar(String titulo, String subtitulo) {
        if (desplazador != null) {
            scrollGuardado = desplazador.getScrollY();
        }
        setTitle(titulo);
        marco = new LinearLayout(this);
        marco.setOrientation(LinearLayout.VERTICAL);
        marco.setBackgroundColor(color(R.color.fondo));
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        marco.setOnApplyWindowInsetsListener((v, ins) -> {
            int t = 0;
            int b = 0;
            int l = 0;
            int r = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                t = i.top;
                b = i.bottom;
                l = i.left;
                r = i.right;
            }
            v.setPadding(l, t, r, b);
            return ins;
        });

        LinearLayout cabecera = new LinearLayout(this);
        cabecera.setOrientation(LinearLayout.VERTICAL);
        cabecera.setPadding(dp(20), dp(20), dp(20), dp(8));
        TextView t = new TextView(this);
        t.setText(titulo);
        t.setTextSize(30);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(color(R.color.texto));
        t.setAccessibilityHeading(true);
        cabecera.addView(t);
        if (subtitulo != null && subtitulo.length() > 0) {
            TextView s = new TextView(this);
            s.setText(subtitulo);
            s.setTextSize(15);
            s.setTextColor(color(R.color.texto2));
            cabecera.addView(s);
        }
        marco.addView(cabecera, new LinearLayout.LayoutParams(-1, -2));

        desplazador = new ScrollView(this);
        desplazador.setFillViewport(false);
        desplazador.setClipToPadding(false);
        raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setPadding(dp(16), dp(4), dp(16), dp(32));
        desplazador.addView(raiz, new ViewGroup.LayoutParams(-1, -2));
        marco.addView(desplazador, new LinearLayout.LayoutParams(-1, 0, 1f));
        tarjeta = null;
        setContentView(marco);
        marco.requestApplyInsets();
        final int y = scrollGuardado;
        if (y > 0) {
            desplazador.post(() -> desplazador.scrollTo(0, y));
        }
    }

    void olvidarScroll() {
        scrollGuardado = 0;
    }

    TextView seccion(String texto) {
        TextView v = new TextView(this);
        v.setText(texto);
        v.setTextSize(14);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(color(R.color.acento));
        v.setLetterSpacing(0.04f);
        v.setAccessibilityHeading(true);
        v.setPadding(dp(8), dp(20), dp(8), dp(8));
        raiz.addView(v);
        nuevaTarjeta();
        return v;
    }

    LinearLayout nuevaTarjeta() {
        tarjeta = new LinearLayout(this);
        tarjeta.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color(R.color.tarjeta));
        g.setCornerRadius(dp(20));
        tarjeta.setBackground(g);
        tarjeta.setClipToOutline(true);
        raiz.addView(tarjeta, new LinearLayout.LayoutParams(-1, -2));
        return tarjeta;
    }

    private Drawable efecto() {
        int[] a = {android.R.attr.selectableItemBackground};
        TypedArray ta = obtainStyledAttributes(a);
        Drawable d = ta.getDrawable(0);
        ta.recycle();
        return d;
    }

    private void divisor() {
        if (tarjeta.getChildCount() == 0) {
            return;
        }
        View d = new View(this);
        d.setBackgroundColor(color(R.color.divisor));
        d.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)));
        lp.leftMargin = dp(16);
        tarjeta.addView(d, lp);
    }

    Fila fila(String titulo, String resumen, Runnable alTocar) {
        return filaBase(titulo, resumen, alTocar, false, false);
    }

    Fila filaAbierta(String titulo, String resumen) {
        return filaBase(titulo, resumen, () -> {
        }, false, false);
    }

    Fila filaInfo(String titulo, String resumen) {
        return filaBase(titulo, resumen, null, false, false);
    }

    Fila interruptor(String titulo, String resumen, boolean estado, Valor al) {
        Fila f = filaBase(titulo, resumen, null, true, estado);
        f.vista.setOnClickListener(v -> {
            boolean n = !f.interruptor.isChecked();
            f.interruptor.setChecked(n);
            f.vista.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED);
            al.con(n);
        });
        return f;
    }

    private Fila filaBase(String titulo, String resumen, Runnable alTocar, boolean conInterruptor, boolean estado) {
        if (tarjeta == null) {
            nuevaTarjeta();
        }
        divisor();
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(Gravity.CENTER_VERTICAL);
        fila.setMinimumHeight(dp(64));
        fila.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout textos = new LinearLayout(this);
        textos.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(titulo);
        t.setTextSize(17);
        t.setTextColor(color(R.color.texto));
        textos.addView(t);
        TextView r = new TextView(this);
        r.setTextSize(14);
        r.setTextColor(color(R.color.texto2));
        r.setText(resumen);
        r.setVisibility(resumen == null || resumen.length() == 0 ? View.GONE : View.VISIBLE);
        r.setPadding(0, dp(2), 0, 0);
        textos.addView(r);
        fila.addView(textos, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = null;
        if (conInterruptor) {
            sw = new Switch(this);
            sw.setChecked(estado);
            sw.setClickable(false);
            sw.setFocusable(false);
            sw.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = dp(12);
            fila.addView(sw, lp);
        }
        final Switch swf = sw;
        if (alTocar != null || conInterruptor) {
            fila.setBackground(efecto());
            fila.setClickable(true);
            fila.setFocusable(true);
            if (alTocar != null) {
                fila.setOnClickListener(v -> alTocar.run());
            }
        }
        if (conInterruptor) {
            fila.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName("android.widget.Switch");
                    info.setCheckable(true);
                    info.setChecked(swf.isChecked());
                }
            });
        } else if (alTocar != null) {
            fila.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName("android.widget.Button");
                }
            });
        }
        tarjeta.addView(fila, new LinearLayout.LayoutParams(-1, -2));
        return new Fila(fila, t, r, sw);
    }

    Fila filaLarga(String titulo, String resumen, Runnable alTocar, String etiquetaLarga, Runnable alMantener) {
        Fila f = filaBase(titulo, resumen, alTocar, false, false);
        final int id = View.generateViewId();
        f.vista.setLongClickable(true);
        f.vista.setOnLongClickListener(v -> {
            alMantener.run();
            return true;
        });
        f.vista.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Button");
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(id, etiquetaLarga));
            }

            @Override
            public boolean performAccessibilityAction(View host, int action, android.os.Bundle args) {
                if (action == id) {
                    alMantener.run();
                    return true;
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
        return f;
    }

    TextView nota(String texto) {
        TextView v = new TextView(this);
        v.setText(texto);
        v.setTextSize(14);
        v.setTextColor(color(R.color.texto2));
        v.setPadding(dp(8), dp(10), dp(8), dp(4));
        raiz.addView(v);
        return v;
    }

    Button boton(String texto, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(texto);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(color(R.color.sobre_acento));
        b.setMinHeight(dp(56));
        GradientDrawable g = new GradientDrawable();
        g.setColor(color(R.color.acento));
        g.setCornerRadius(dp(28));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), g, null));
        b.setContentDescription(texto);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16);
        raiz.addView(b, lp);
        return b;
    }

    Button botonSecundario(String texto, View.OnClickListener l) {
        Button b = boton(texto, l);
        b.setTextColor(color(R.color.acento));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x00000000);
        g.setStroke(Math.max(1, dp(2)), color(R.color.acento));
        g.setCornerRadius(dp(28));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33808080), g, null));
        return b;
    }

    TextView etiqueta(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(16);
        v.setTextColor(color(R.color.texto));
        v.setPadding(dp(8), dp(8), dp(8), dp(8));
        raiz.addView(v);
        return v;
    }

    void breve(String t) {
        Toast.makeText(this, t, Toast.LENGTH_SHORT).show();
    }

    void anunciar(View v, String t) {
        v.announceForAccessibility(t);
    }

    static void estadoSlider(View v, String t) {
        if (Build.VERSION.SDK_INT >= 30) {
            v.setStateDescription(t);
        }
    }

    void dialogoDeslizador(String titulo, int max, float actual, String formato, Numero al) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        final TextView valor = new TextView(this);
        valor.setGravity(Gravity.CENTER);
        valor.setTextSize(32f);
        valor.setTextColor(color(R.color.texto));
        valor.setText(String.format(formato, actual));
        l.addView(valor, new LinearLayout.LayoutParams(-1, -2));
        final SeekBar barra = new SeekBar(this);
        barra.setMax(max);
        barra.setProgress(Math.round(actual));
        barra.setContentDescription(titulo);
        estadoSlider(barra, String.format(formato, actual));
        barra.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                String x = String.format(formato, (float) p);
                valor.setText(x);
                estadoSlider(s, x);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        l.addView(barra, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setView(l)
                .setPositiveButton(android.R.string.ok, (d, w) -> al.con(barra.getProgress()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    void dialogoLista(String titulo, CharSequence[] textos, int marcado, Numero al) {
        new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setSingleChoiceItems(textos, marcado, (d, i) -> {
                    d.dismiss();
                    al.con(i);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    void confirmar(String titulo, String mensaje, String si, Runnable alAceptar) {
        new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setMessage(mensaje)
                .setPositiveButton(si, (d, w) -> alAceptar.run())
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
