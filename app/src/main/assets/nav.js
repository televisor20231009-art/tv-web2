(function () {
  if (window.__navInstalled) return;
  window.__navInstalled = true;

  // Ancho de página en "px CSS". Más bajo = todo se ve más grande; más alto = más contenido.
  var TV_WIDTH = 1280;

  var SEL = 'a[href],button,input:not([type=hidden]),select,textarea,summary,iframe,video,' +
            '[tabindex]:not([tabindex="-1"]),[onclick],[role=button],[role=link],[role=tab]';
  var cur = null;

  // ---------- Estilos para TV + ocultar anuncios típicos ----------
  var st = document.createElement('style');
  st.textContent =
    'html,body{overflow-x:hidden !important}' +
    '::-webkit-scrollbar{display:none !important}' +
    '*{-webkit-tap-highlight-color:transparent}' +
    '.__navf{outline:4px solid #e13c78 !important;outline-offset:3px !important;' +
    'box-shadow:0 0 0 8px rgba(225,60,120,.35) !important;border-radius:4px}' +
    'ins.adsbygoogle,[id^="google_ads_"],iframe[src*="doubleclick"],iframe[src*="exoclick"],' +
    'iframe[src*="juicyads"],iframe[src*="trafficjunky"],iframe[src*="popads"]{display:none !important}';
  (document.head || document.documentElement).appendChild(st);

  // ---------- Viewport fijo (evita que la página quede más ancha que la pantalla) ----------
  function forceViewport() {
    var want = 'width=' + TV_WIDTH;
    var metas = document.querySelectorAll('meta[name=viewport]');
    if (metas.length === 1 && metas[0].getAttribute('content') === want) return;
    for (var i = 0; i < metas.length; i++) metas[i].parentNode.removeChild(metas[i]);
    var m = document.createElement('meta');
    m.name = 'viewport';
    m.content = want;
    (document.head || document.documentElement).appendChild(m);
  }
  forceViewport();
  document.addEventListener('readystatechange', forceViewport);
  window.addEventListener('load', forceViewport);
  var vpTimer = setInterval(forceViewport, 700);
  setTimeout(function () { clearInterval(vpTimer); }, 8000);

  function vvp() {
    return window.visualViewport ||
           { offsetLeft: 0, offsetTop: 0, width: innerWidth, height: innerHeight };
  }

  // ---------- Scroll: usa el contenedor desplazable bajo el cursor, o la ventana ----------
  // frac = fracción de la altura visible (positivo baja, negativo sube)
  window.__scroll = function (frac, fx, fy) {
    var vv = vvp(), dy = frac * vv.height;
    var x = Math.min(Math.max(vv.offsetLeft + fx * vv.width, 1), innerWidth - 1);
    var y = Math.min(Math.max(vv.offsetTop + fy * vv.height, 1), innerHeight - 1);
    var el = document.elementFromPoint(x, y);
    while (el && el !== document.body && el !== document.documentElement) {
      var cs = getComputedStyle(el);
      if ((cs.overflowY === 'auto' || cs.overflowY === 'scroll') && el.scrollHeight > el.clientHeight + 4) {
        el.scrollBy({ top: dy, behavior: 'smooth' });
        return 'el';
      }
      el = el.parentElement;
    }
    window.scrollBy({ top: dy, behavior: 'smooth' });
    return 'win';
  };

  // ---------- Navegación por elementos ----------
  function visible(el) {
    if (!el.getClientRects().length) return false;
    var r = el.getBoundingClientRect();
    if (r.width < 6 || r.height < 6) return false;
    var cs = getComputedStyle(el);
    if (cs.visibility === 'hidden' || cs.display === 'none' ||
        parseFloat(cs.opacity) < 0.05 || cs.pointerEvents === 'none') return false;
    if (r.width > innerWidth * 0.9 && r.height > innerHeight * 0.9 &&
        el.tagName !== 'VIDEO' && el.tagName !== 'IFRAME') return false;
    return true;
  }

  function items() {
    var out = [], all = document.querySelectorAll(SEL);
    for (var i = 0; i < all.length; i++) if (visible(all[i])) out.push(all[i]);
    return out;
  }

  function setCur(el) {
    if (cur) cur.classList.remove('__navf');
    cur = el;
    if (cur) {
      cur.classList.add('__navf');
      var r = cur.getBoundingClientRect();
      if (r.top < 0 || r.bottom > innerHeight || r.left < 0 || r.right > innerWidth)
        cur.scrollIntoView({ block: 'center', inline: 'center' });
    }
  }

  // Centro del elemento como fracción de la pantalla (Java lo convierte en píxeles)
  function pos(el) {
    var r = el.getBoundingClientRect(), vv = vvp();
    var x = Math.min(Math.max(r.left + r.width / 2, 1), innerWidth - 1);
    var y = Math.min(Math.max(r.top + r.height / 2, 1), innerHeight - 1);
    return { x: x, y: y, s: ((x - vv.offsetLeft) / vv.width) + ',' + ((y - vv.offsetTop) / vv.height) };
  }

  window.__nav = function (dir) {
    var list = items();
    if (cur && (!document.contains(cur) || list.indexOf(cur) < 0)) {
      cur.classList.remove('__navf'); cur = null;
    }

    if (dir === 'ok') {
      if (!cur) return 'none';
      var p = pos(cur);
      var top = document.elementFromPoint(p.x, p.y);
      var hit = top && (top === cur || cur.contains(top));
      if (!hit && cur.tagName !== 'IFRAME') { cur.click(); return 'clicked'; }
      return 'o,' + p.s + ',' + (cur.tagName === 'IFRAME' ? 1 : 0);
    }

    if (dir.indexOf('at:') === 0) {              // elemento más cercano a una posición
      var a = dir.split(':'), vv = vvp();
      var px = vv.offsetLeft + parseFloat(a[1]) * vv.width;
      var py = vv.offsetTop + parseFloat(a[2]) * vv.height;
      var near = null, nd = 1e12;
      list.forEach(function (el) {
        var q = el.getBoundingClientRect();
        var d = Math.hypot(q.left + q.width / 2 - px, q.top + q.height / 2 - py);
        if (d < nd) { nd = d; near = el; }
      });
      if (!near) return 'none';
      setCur(near);
      return 'm,' + pos(near).s;
    }

    if (!cur) {
      var first = null, fs = 1e12;
      list.forEach(function (el) {
        var q = el.getBoundingClientRect();
        if (q.bottom < 0 || q.top > innerHeight || q.right < 0 || q.left > innerWidth) return;
        var s = Math.max(q.top, 0) * 3 + Math.max(q.left, 0);
        if (s < fs) { fs = s; first = el; }
      });
      if (!first && list.length) first = list[0];
      if (!first) return 'none';
      setCur(first);
      return 'm,' + pos(first).s;
    }

    var c = cur.getBoundingClientRect();
    var cx = c.left + c.width / 2, cy = c.top + c.height / 2, best = null, bs = 1e12;
    list.forEach(function (el) {
      if (el === cur || cur.contains(el) || el.contains(cur)) return;
      var q = el.getBoundingClientRect();
      var x2 = q.left + q.width / 2, y2 = q.top + q.height / 2, dx = x2 - cx, dy = y2 - cy, pr, o;
      if (dir === 'right')      { if (dx <= 2)  return; pr = dx;  o = Math.abs(dy); }
      else if (dir === 'left')  { if (dx >= -2) return; pr = -dx; o = Math.abs(dy); }
      else if (dir === 'down')  { if (dy <= 2)  return; pr = dy;  o = Math.abs(dx); }
      else                      { if (dy >= -2) return; pr = -dy; o = Math.abs(dx); }
      var s2 = pr + o * 2.5;
      if (s2 < bs) { bs = s2; best = el; }
    });
    if (best) { setCur(best); return 'm,' + pos(best).s; }

    if (dir === 'down' || dir === 'up') {
      window.__scroll(dir === 'down' ? 0.6 : -0.6, 0.5, 0.5);
      return 'scrolled';
    }
    return 'none';
  };

  // ---------- Elimina capas invisibles a pantalla completa (secuestran el primer clic) ----------
  function killOverlays() {
    var els = document.querySelectorAll('body div, body a, body span, body section');
    for (var i = 0; i < els.length; i++) {
      var el = els[i], cs = getComputedStyle(el);
      if (cs.position !== 'fixed' && cs.position !== 'absolute') continue;
      var r = el.getBoundingClientRect();
      if (r.width < innerWidth * 0.8 || r.height < innerHeight * 0.8) continue;
      var transparent = parseFloat(cs.opacity) < 0.05 ||
        ((cs.backgroundColor === 'rgba(0, 0, 0, 0)' || cs.backgroundColor === 'transparent') &&
         cs.backgroundImage === 'none');
      if (!transparent) continue;
      if (el.querySelector('video,iframe,img,canvas,input,button,svg')) continue;
      if ((el.textContent || '').trim().length > 0) continue;
      el.remove();
    }
  }
  setInterval(killOverlays, 2000);
})();
