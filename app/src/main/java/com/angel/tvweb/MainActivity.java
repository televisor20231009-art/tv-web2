package com.angel.tvweb;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
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
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * HentaiLA para Google TV.
 *  - Cursor siempre visible. En modo ELEMENTOS (por defecto) las flechas saltan entre
 *    enlaces/botones y el cursor se coloca sobre cada uno; OK lo abre.
 *  - En modo LIBRE las flechas mueven el cursor por toda la pantalla.
 *  - Cambiar de modo: mantener OK pulsado (o tecla MENÚ).
 *  - CH+ / CH- (o Re Pág / Av Pág): desplazar la página.
 *  - Bloqueo de publicidad: lista de dominios, redirecciones automáticas y ventanas emergentes.
 */
public class MainActivity extends Activity {

    // Página a mostrar
    private static final String HOME_URL = "https://hentaila.com/";

    // Otros dominios del propio sitio que no deben bloquearse (agrega aquí si hace falta)
    private static final String[] EXTRA_ALLOWED_HOSTS = { };

    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";

    private static final int MATCH = FrameLayout.LayoutParams.MATCH_PARENT;
    private static final long LONG_PRESS_MS = 450;

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
    private String siteHost = "";
    private final Set<String> blocked = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        navJs = readAsset("nav.js");
        siteHost = String.valueOf(Uri.parse(HOME_URL).getHost()).toLowerCase(Locale.ROOT);
        loadBlocklist("blocklist.txt");
        loadBlocklist("blocklist-extra.txt");

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(MATCH, MATCH));

        cursor = new CursorView(this);
        cursorSize = dp(40);
        root.addView(cursor, new FrameLayout.LayoutParams(cursorSize, cursorSize));
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
                Uri u = r.getUrl();
                String scheme = u.getScheme();
                if (!("http".equals(scheme) || "https".equals(scheme))) return true;
                String host = u.getHost();
                if (isBlocked(host)) return true;
                // Redirección automática a otro dominio sin que el usuario haya tocado nada = anuncio
                if (r.isForMainFrame() && !isSiteHost(host) && !r.hasGesture()) {
                    Toast.makeText(MainActivity.this, "Redirección bloqueada: " + host,
                            Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                if (isBlocked(r.getUrl().getHost())) {
                    return new WebResourceResponse("text/plain", "utf-8",
                            new ByteArrayInputStream(new byte[0]));
                }
                return null;
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap favicon) { inject(v); }

            @Override
            public void onPageFinished(WebView v, String url) { inject(v); }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView v, boolean dialog, boolean userGesture, Message msg) {
                return false; // ventanas emergentes bloqueadas
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
            Toast.makeText(this,
                    "Flechas: saltar entre elementos · OK: abrir · Mantén OK: cursor libre · CH+/CH-: desplazar",
                    Toast.LENGTH_LONG).show();
        });

        web.loadUrl(HOME_URL);
    }

    // ---------- Bloqueo de publicidad ----------
    private void loadBlocklist(String asset) {
        String txt = readAsset(asset);
        if (txt.isEmpty()) return;
        for (String line : txt.split("\\r?\\n")) {
            int h = line.indexOf('#');
            if (h >= 0) line = line.substring(0, h);
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] t = line.split("\\s+");
            String host = t[t.length - 1].toLowerCase(Locale.ROOT);
            if (host.indexOf('.') < 0 || host.matches("[0-9.]+")) continue;
            blocked.add(host);
        }
    }

    private boolean isSiteHost(String host) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        if (host.equals(siteHost) || host.endsWith("." + siteHost)) return true;
        for (String a : EXTRA_ALLOWED_HOSTS) {
            if (host.equals(a) || host.endsWith("." + a)) return true;
        }
        return false;
    }

    private boolean isBlocked(String host) {
        if (host == null || isSiteHost(host)) return false;
        String h = host.toLowerCase(Locale.ROOT);
        while (true) {
            if (blocked.contains(h)) return true;
            int i = h.indexOf('.');
            if (i < 0) return false;
            h = h.substring(i + 1);
        }
    }

    private void inject(WebView v) {
        if (!navJs.isEmpty()) v.evaluateJavascript(navJs, null);
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
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
        cursor.setFree(pointerActive());
        placeCursor();
    }

    private void toggleMode() {
        pointerMode = !pointerMode;
        updateMode();
        if (!pointerMode) {
            // volver a elementos: engancha el elemento más cercano al cursor
            float fx = cx / Math.max(1, web.getWidth()), fy = cy / Math.max(1, web.getHeight());
            web.evaluateJavascript("window.__nav&&window.__nav('at:" + fx + ":" + fy + "')", this::applyNavResult);
        }
        Toast.makeText(this, pointerMode
                ? "Cursor libre · mantén OK para volver a elementos"
                : "Modo elementos · mantén OK para cursor libre", Toast.LENGTH_SHORT).show();
    }

    // ---------- Modo elementos ----------
    private void navigate(int key) {
        String d;
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP:    d = "up"; break;
            case KeyEvent.KEYCODE_DPAD_DOWN:  d = "down"; break;
            case KeyEvent.KEYCODE_DPAD_LEFT:  d = "left"; break;
            default:                          d = "right"; break;
        }
        web.evaluateJavascript("window.__nav&&window.__nav('" + d + "')", this::applyNavResult);
    }

    // Respuesta "m,fx,fy": coloca el cursor sobre el elemento seleccionado
    private void applyNavResult(String v) {
        if (v == null) return;
        String[] p = v.replace("\"", "").split(",");
        if (p.length >= 3 && "m".equals(p[0])) {
            try {
                cx = Float.parseFloat(p[1]) * web.getWidth();
                cy = Float.parseFloat(p[2]) * web.getHeight();
                placeCursor();
            } catch (NumberFormatException ignored) { }
        }
    }

    private void confirm() {
        if (pointerActive()) { tapAt(cx, cy); return; }
        web.evaluateJavascript("window.__nav&&window.__nav('ok')", v -> {
            String s = v == null ? "" : v.replace("\"", "");
            String[] p = s.split(",");
            if (p.length >= 3 && "o".equals(p[0])) {
                try {
                    float x = Float.parseFloat(p[1]) * web.getWidth();
                    float y = Float.parseFloat(p[2]) * web.getHeight();
                    tapAt(x, y);
                    // Un reproductor (iframe) se maneja con el cursor libre
                    if (p.length > 3 && "1".equals(p[3])) {
                        cx = x; cy = y;
                        pointerMode = true;
                        updateMode();
                        Toast.makeText(this, "Cursor libre · mantén OK para volver a elementos",
                                Toast.LENGTH_SHORT).show();
                    }
                } catch (NumberFormatException ignored) { }
            } else if (!"clicked".equals(s)) {
                tapAt(cx, cy);      // nada seleccionado: toca donde está el cursor
            }
        });
    }

    // ---------- Cursor libre ----------
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

    private void scrollScreen(int dir) {
        if (customView == null) web.scrollBy(0, Math.round(dir * web.getHeight() * 0.8f));
    }

    // Toque real (down + up) en coordenadas de pantalla de la app
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

            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                if (down) scrollScreen(-1);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (down) scrollScreen(1);
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
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean free = false;

        CursorView(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(5f);
        }

        void setFree(boolean f) { free = f; invalidate(); }

        @Override
        protected void onDraw(Canvas canvas) {
            float c = getWidth() / 2f, r = c - 5f;
            if (free) {
                // Cursor libre: círculo blanco grande
                fill.setColor(Color.argb(210, 255, 255, 255));
                ring.setColor(Color.parseColor("#3c1446"));
                dot.setColor(Color.parseColor("#e13c78"));
                canvas.drawCircle(c, c, r, fill);
                canvas.drawCircle(c, c, r, ring);
                canvas.drawCircle(c, c, r / 3f, dot);
            } else {
                // Modo elementos: marcador rosa, más discreto
                fill.setColor(Color.argb(230, 225, 60, 120));
                ring.setColor(Color.WHITE);
                canvas.drawCircle(c, c, r * 0.55f, fill);
                canvas.drawCircle(c, c, r * 0.55f, ring);
            }
        }
    }
}
