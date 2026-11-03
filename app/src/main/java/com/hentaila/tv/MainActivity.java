package com.hentaila.tv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.ByteArrayInputStream;

public class MainActivity extends Activity {

    private static final String HOME = "https://hentaila.com/";
    private static final String SITE_HOST = "hentaila.com";

    // Dominios de publicidad / popups que se bloquean
    private static final String[] BLOCKED = {
            "adtng.com", "doubleclick.net", "googlesyndication.com", "exoclick.com",
            "juicyads.com", "trafficjunky.com", "clickadu.com", "propellerads.com",
            "popads.net", "popcash.net", "adsterra.com", "realsrv.com",
            "tsyndicate.com", "magsrv.com", "onclickads.net", "a-ads.com"
    };

    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    // CSS + JS que limpia el sitio: diseño de escritorio, sin barra inferior,
    // sin enlaces externos, con resaltado de foco para el control remoto
    private static final String CLEAN_JS =
            "(function(){"
          // Fuerza el diseño de escritorio (ancho virtual 1280px)
          + "function vp(){var m=document.querySelector('meta[name=viewport]');"
          + " if(!m){m=document.createElement('meta');m.setAttribute('name','viewport');"
          + "  (document.head||document.documentElement).appendChild(m);}"
          + " if(m.getAttribute('content')!=='width=1280'){m.setAttribute('content','width=1280');}}"
          // Oculta cualquier barra fija inferior que contenga el enlace Catálogo
          + "function bar(){document.querySelectorAll('a').forEach(function(a){"
          + " if(!/Cat/.test(a.textContent))return;"
          + " var p=a;"
          + " while(p&&p!==document.body){var c=getComputedStyle(p);"
          + "  if(c.position==='fixed'&&parseInt(c.bottom)<=2){p.style.display='none';break;}"
          + "  p=p.parentElement;}"
          + "});}"
          + "if(!window.__tvInit){window.__tvInit=true;"
          + " var s=document.createElement('style');"
          + " s.textContent='*:focus{outline:4px solid #ff2d75!important;outline-offset:2px!important;border-radius:6px}"
          + "  footer{display:none!important}"
          + "  ::-webkit-scrollbar{display:none}';"
          + " (document.head||document.documentElement).appendChild(s);"
          + " var t=null;var mo=new MutationObserver(function(){clearTimeout(t);t=setTimeout(clean,300);});"
          + " mo.observe(document.documentElement,{childList:true,subtree:true});"
          + "}"
          + "function clean(){"
          + " vp();bar();"
          + " document.querySelectorAll('a[href]').forEach(function(a){"
          + "  var h=a.getAttribute('href')||'';"
          + "  if(/^https?:\\/\\//i.test(h)){"
          + "   try{var u=new URL(h);"
          + "    if(u.hostname!=='hentaila.com'&&!u.hostname.endsWith('.hentaila.com')){"
          + "     var p=a.parentElement;"
          + "     (p&&p.children.length<=2&&p!==document.body?p:a).style.display='none';"
          + "    }}catch(e){}"
          + "  }"
          + " });"
          + " document.querySelectorAll('[target=_blank]').forEach(function(a){a.removeAttribute('target');});"
          + "}"
          + "clean();"
          + "})();";

    private WebView webView;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#14101F"));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#14101F"));
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings st = webView.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setMediaPlaybackRequiresUserGesture(false);
        st.setUserAgentString(DESKTOP_UA);
        st.setLoadWithOverviewMode(true);
        st.setUseWideViewPort(true);
        st.setSupportMultipleWindows(true);
        st.setJavaScriptCanOpenWindowsAutomatically(false);
        st.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        st.setTextZoom(100);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                // Solo se restringe la navegación de la página principal;
                // los reproductores (iframes) cargan libremente.
                if (!req.isForMainFrame()) return false;
                String host = req.getUrl().getHost();
                return !isSiteHost(host);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                String host = req.getUrl().getHost();
                if (host != null) {
                    for (String b : BLOCKED) {
                        if (host.equals(b) || host.endsWith("." + b)) {
                            return new WebResourceResponse("text/plain", "utf-8",
                                    new ByteArrayInputStream(new byte[0]));
                        }
                    }
                }
                return super.shouldInterceptRequest(v, req);
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap favicon) {
                super.onPageStarted(v, url, favicon);
            }

            @Override
            public void onPageCommitVisible(WebView v, String url) {
                super.onPageCommitVisible(v, url);
                v.evaluateJavascript(CLEAN_JS, null);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                super.onPageFinished(v, url);
                v.evaluateJavascript(CLEAN_JS, null);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req,
                                        android.webkit.WebResourceError err) {
                if (req.isForMainFrame()) {
                    v.loadData("<body style='background:#14101F;color:#fff;font:32px sans-serif;"
                            + "text-align:center;padding-top:20%'>Sin conexi&oacute;n.<br>"
                            + "Presiona MEN&Uacute; para reintentar.</body>", "text/html", "utf-8");
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView v, boolean isDialog, boolean isUserGesture, Message msg) {
                return false; // bloquea popups
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                webView.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                if (customViewCallback != null) customViewCallback.onCustomViewHidden();
                webView.setVisibility(View.VISIBLE);
                webView.requestFocus();
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(HOME);
        }
    }

    private boolean isSiteHost(String host) {
        return host != null && (host.equals(SITE_HOST) || host.endsWith("." + SITE_HOST));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (customView != null) {
                webView.getWebChromeClient().onHideCustomView();
                return true;
            }
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_MENU) {
            webView.loadUrl(HOME);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        webView.saveState(out);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        webView.onResume();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }
}
