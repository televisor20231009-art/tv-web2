package com.angel.tvweb;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * WebView para Google TV con dos modos:
 *  - FLECHAS (por defecto): las flechas saltan entre enlaces/botones de la página, OK los activa.
 *  - CURSOR: un puntero libre (útil dentro de reproductores de video).
 * Cambiar de modo: mantener OK pulsado (o la tecla MENÚ si el control la tiene).
 * Atrás: sale de pantalla completa, retrocede en el historial y al final cierra la app.
 */
public class MainActivity extends Activity {

    // Cambia aquí la página que quieres mostrar
    private static final String HOME_URL = "https://hentaila.com/";

    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";

    private static final int MATCH = FrameLayout.LayoutParams.MATCH_PARENT;
    private static final long LONG_PRESS_MS = 600;

    private FrameLayout root;
    private WebView web;
    private CursorView cursor;
    private int cursorSize;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private float cx, cy;
    private boolean pointerMode = false;
    private long okDownTime;
    private boolean okLongHandled;
    private String navJs = "";
    private final Handler handler = new Handler(Looper.getMainLooper());

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        navJs = readAsset("nav.js");

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(MATCH, MATCH));

        cursor = new CursorView(this);
        cursorSize = dp(36);
        root.addView(cursor, new FrameLayout.LayoutParams(cursorSize, cursorSize));
        cursor.setVisibility(View.GONE);
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(USER_AGENT);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String scheme = r.getUrl().getScheme();
                return !("http".equals(scheme) || "https".equals(scheme));
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap favicon) {
                inject(v);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                inject(v);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView v, boolean dialog, boolean userGesture, Message msg) {
                return false; // ignora ventanas emergentes
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = view;
                customCallback = cb;
                root.addView(view, 0, new FrameLayout.LayoutParams(MATCH, MATCH));
                web.setVisibility(View.INVISIBLE);
                updateMode();
            }

            @Override
            public void onHideCustomView() { hideCustom(); }
        });

        root.post(() -> {
            cx = root.getWidth() / 2f;
            cy = root.getHeight() / 2f;
            updateMode();
            Toast.makeText(this, "Flechas: moverte · OK: abrir · Mantén OK: cambiar a cursor",
                    Toast.LENGTH_LONG).show();
        });

        web.loadUrl(HOME_URL);
    }

    private void inject(WebView v) {
        if (!navJs.isEmpty()) v.evaluateJavascript(navJs, null);
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    // ---------- Modos ----------
    private boolean pointerActive() { return pointerMode || customView != null; }

    private void updateMode() {
        cursor.setVisibility(pointerActive() ? View.VISIBLE : View.GONE);
        if (pointerActive()) placeCursor();
    }

    private void toggleMode() {
        pointerMode = !pointerMode;
        updateMode();
        Toast.makeText(this, pointerMode
                ? "Modo cursor · mantén OK para volver a flechas"
                : "Modo flechas · mantén OK para usar el cursor", Toast.LENGTH_SHORT).show();
    }

    // ---------- Modo flechas ----------
    private void navigate(int key) {
        String d;
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP:    d = "up"; break;
            case KeyEvent.KEYCODE_DPAD_DOWN:  d = "down"; break;
            case KeyEvent.KEYCODE_DPAD_LEFT:  d = "left"; break;
            default:                          d = "right"; break;
        }
        web.evaluateJavascript("window.__nav&&window.__nav('" + d + "')", null);
    }

    // OK en modo flechas: la página dice dónde está el elemento y aquí se hace un toque real
    private void confirm() {
        if (pointerActive()) { tapAt(cx, cy); return; }
        web.evaluateJavascript("window.__nav&&window.__nav('ok')", v -> {
            if (v == null) return;
            String s = v.replace("\"", "");
            if (!s.contains(",")) return;        // 'none', 'clicked' o null
            String[] p = s.split(",");
            try {
                float x = Float.parseFloat(p[0]) * web.getWidth();
                float y = Float.parseFloat(p[1]) * web.getHeight();
                tapAt(x, y);
                // Si era un reproductor (iframe), pasa a cursor para manejar sus controles
                if (p.length > 2 && "1".equals(p[2])) {
                    cx = x; cy = y;
                    pointerMode = true;
                    updateMode();
                    Toast.makeText(this, "Modo cursor · mantén OK para volver a flechas",
                            Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException ignored) { }
        });
    }

    // ---------- Modo cursor ----------
    private void placeCursor() {
        cursor.setX(cx - cursorSize / 2f);
        cursor.setY(cy - cursorSize / 2f);
    }

    private void move(int key, int repeat) {
        float step = dp(10) + Math.min(repeat, 25) * dp(1) * 1.2f;
        int w = root.getWidth(), h = root.getHeight();
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (cy <= 1) scrollPage(-1); else cy = Math.max(0, cy - step);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (cy >= h - 1) scrollPage(1); else cy = Math.min(h - 1, cy + step);
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                cx = Math.max(0, cx - step);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                cx = Math.min(w - 1, cx + step);
                break;
        }
        placeCursor();
    }

    private void scrollPage(int dir) {
        if (customView == null) web.scrollBy(0, dir * dp(140));
    }

    // Toque real (down + up) en las coordenadas indicadas, relativas a la pantalla de la app
    private void tapAt(float rx, float ry) {
        final View target = customView != null ? customView : web;
        final long t = SystemClock.uptimeMillis();
        int[] a = new int[2], b = new int[2];
        target.getLocationOnScreen(a);
        root.getLocationOnScreen(b);
        final float x = rx + b[0] - a[0], y = ry + b[1] - a[1];

        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        target.dispatchTouchEvent(down);
        down.recycle();

        handler.postDelayed(() -> {
            MotionEvent up = MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
            up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            target.dispatchTouchEvent(up);
            up.recycle();
        }, 70);
    }

    // ---------- Teclas ----------
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (k) {
            case KeyEvent.KEYCODE_MENU:
                if (down && e.getRepeatCount() == 0) toggleMode();
                return true;

            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) {
                    if (pointerActive()) move(k, e.getRepeatCount());
                    else navigate(k);
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (down) {
                    if (e.getRepeatCount() == 0) {
                        okDownTime = SystemClock.uptimeMillis();
                        okLongHandled = false;
                    } else if (!okLongHandled && SystemClock.uptimeMillis() - okDownTime >= LONG_PRESS_MS) {
                        okLongHandled = true;
                        toggleMode();
                    }
                } else if (!okLongHandled) {
                    confirm();
                }
                return true;

            default:
                return super.dispatchKeyEvent(e);
        }
    }

    // ---------- Pantalla completa de video / Atrás ----------
    private void hideCustom() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        web.setVisibility(View.VISIBLE);
        if (customCallback != null) customCallback.onCustomViewHidden();
        customCallback = null;
        updateMode();
    }

    @Override
    public void onBackPressed() {
        if (customView != null) hideCustom();
        else if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override protected void onPause() { super.onPause(); web.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
    @Override protected void onDestroy() { web.destroy(); super.onDestroy(); }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------- Vista del cursor ----------
    private static class CursorView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);

        CursorView(Context c) {
            super(c);
            fill.setColor(Color.argb(200, 255, 255, 255));
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(4f);
            stroke.setColor(Color.parseColor("#1c2f3e"));
            dot.setColor(Color.parseColor("#2389cc"));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float c = getWidth() / 2f, r = c - 4f;
            canvas.drawCircle(c, c, r, fill);
            canvas.drawCircle(c, c, r, stroke);
            canvas.drawCircle(c, c, r / 3f, dot);
        }
    }
}
