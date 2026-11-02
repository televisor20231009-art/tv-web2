(function () {
  if (window.__navInstalled) return;
  window.__navInstalled = true;

  var SEL = 'a[href],button,input:not([type=hidden]),select,textarea,summary,iframe,video,' +
            '[tabindex]:not([tabindex="-1"]),[onclick],[role=button],[role=link],[role=tab]';
  var cur = null;

  var st = document.createElement('style');
  st.textContent = '.__navf{outline:4px solid #2389cc !important;outline-offset:3px !important;' +
                   'box-shadow:0 0 0 8px rgba(35,137,204,.35) !important;border-radius:4px}';
  (document.head || document.documentElement).appendChild(st);

  function visible(el) {
    if (!el.getClientRects().length) return false;
    var r = el.getBoundingClientRect();
    if (r.width < 6 || r.height < 6) return false;
    var cs = getComputedStyle(el);
    if (cs.visibility === 'hidden' || cs.display === 'none' ||
        parseFloat(cs.opacity) < 0.05 || cs.pointerEvents === 'none') return false;
    // capas que cubren casi toda la pantalla (publicidad): se ignoran
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

  window.__nav = function (dir) {
    var list = items();
    if (cur && (!document.contains(cur) || list.indexOf(cur) < 0)) {
      cur.classList.remove('__navf'); cur = null;
    }

    if (dir === 'ok') {
      if (!cur) return 'none';
      var r = cur.getBoundingClientRect();
      var vv = window.visualViewport ||
               { offsetLeft: 0, offsetTop: 0, width: innerWidth, height: innerHeight };
      var x = Math.min(Math.max(r.left + r.width / 2, 1), innerWidth - 1);
      var y = Math.min(Math.max(r.top + r.height / 2, 1), innerHeight - 1);
      var top = document.elementFromPoint(x, y);
      var hit = top && (top === cur || cur.contains(top));
      if (!hit && cur.tagName !== 'IFRAME') { cur.click(); return 'clicked'; }
      // fracciones de la pantalla: Java las convierte en un toque real
      return ((x - vv.offsetLeft) / vv.width) + ',' + ((y - vv.offsetTop) / vv.height) +
             ',' + (cur.tagName === 'IFRAME' ? 1 : 0);
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
      setCur(first);
      return first ? 'moved' : 'none';
    }

    var c = cur.getBoundingClientRect();
    var cx = c.left + c.width / 2, cy = c.top + c.height / 2, best = null, bs = 1e12;
    list.forEach(function (el) {
      if (el === cur || cur.contains(el) || el.contains(cur)) return;
      var q = el.getBoundingClientRect();
      var x2 = q.left + q.width / 2, y2 = q.top + q.height / 2, dx = x2 - cx, dy = y2 - cy, p, o;
      if (dir === 'right')      { if (dx <= 2)  return; p = dx;  o = Math.abs(dy); }
      else if (dir === 'left')  { if (dx >= -2) return; p = -dx; o = Math.abs(dy); }
      else if (dir === 'down')  { if (dy <= 2)  return; p = dy;  o = Math.abs(dx); }
      else                      { if (dy >= -2) return; p = -dy; o = Math.abs(dx); }
      var s2 = p + o * 2.5;
      if (s2 < bs) { bs = s2; best = el; }
    });
    if (best) { setCur(best); return 'moved'; }

    if (dir === 'down' || dir === 'up') {
      window.scrollBy(0, (dir === 'down' ? 1 : -1) * innerHeight * 0.7);
      return 'scrolled';
    }
    return 'none';
  };
})();
