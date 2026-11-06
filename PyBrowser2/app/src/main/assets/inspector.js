function (nonce) {
  // Instalado em cada página pelo app. O nonce só existe neste fechamento: a página não consegue forjar seleções.
  if (window.__pybTools) { try { window.__pybTools.inspect(false); window.__pybTools.clear(); } catch (e) {} }
  var NS = "data-pyb-hl", overlay = null, marked = [], send = console.info.bind(console);

  function esc(s) { return window.CSS && CSS.escape ? CSS.escape(s) : String(s).replace(/[^\w-]/g, "\\$&"); }
  function uniqueId(el) { return !!el.id && document.querySelectorAll("#" + esc(el.id)).length === 1; }

  function cssPath(el) {
    if (uniqueId(el)) return "#" + esc(el.id);
    var parts = [];
    while (el && el.nodeType === 1 && el !== document.documentElement) {
      if (uniqueId(el)) { parts.unshift("#" + esc(el.id)); break; }
      var p = el.tagName.toLowerCase(), par = el.parentElement;
      if (par) {
        var same = [].filter.call(par.children, function (c) { return c.tagName === el.tagName; });
        if (same.length > 1) p += ":nth-of-type(" + (same.indexOf(el) + 1) + ")";
      }
      parts.unshift(p);
      el = par;
    }
    return parts.join(" > ");
  }

  function xpath(el) {
    if (uniqueId(el) && el.id.indexOf('"') < 0) return '//*[@id="' + el.id + '"]';
    var parts = [];
    for (; el && el.nodeType === 1; el = el.parentElement) {
      var i = 1, s = el.previousElementSibling;
      while (s) { if (s.tagName === el.tagName) i++; s = s.previousElementSibling; }
      parts.unshift(el.tagName.toLowerCase() + "[" + i + "]");
    }
    return "/" + parts.join("/");
  }

  function info(el) {
    var attrs = {};
    for (var i = 0; i < el.attributes.length; i++) {
      var a = el.attributes[i];
      if (a.name === NS || (a.name === "style" && el.hasAttribute(NS))) continue;
      attrs[a.name] = a.value.slice(0, 200);
    }
    return {
      tag: el.tagName.toLowerCase(), css: cssPath(el), xpath: xpath(el),
      text: (el.innerText || el.textContent || "").trim().slice(0, 200), attrs: attrs,
      html: el.outerHTML.replace(/ data-pyb-hl="1"/g, "").slice(0, 400)
    };
  }

  function ensure() {
    if (overlay && overlay.isConnected) return overlay;
    overlay = document.createElement("div");
    overlay.setAttribute("data-pyb-ui", "1");
    overlay.style.cssText = "position:fixed;z-index:2147483647;pointer-events:none;box-sizing:border-box;" +
      "border:2px solid #72e8c9;background:rgba(114,232,201,.22);display:none";
    document.documentElement.appendChild(overlay);
    return overlay;
  }
  function show(el) {
    var r = el.getBoundingClientRect(), o = ensure();
    o.style.display = "block"; o.style.left = r.left + "px"; o.style.top = r.top + "px";
    o.style.width = r.width + "px"; o.style.height = r.height + "px";
  }
  function hide() { if (overlay) overlay.style.display = "none"; }
  function clear() {
    marked.forEach(function (m) { m[0].style.outline = m[1]; m[0].removeAttribute(NS); });
    marked = []; hide();
  }
  function mark(list) {
    clear();
    list.slice(0, 50).forEach(function (el) {
      marked.push([el, el.style.outline]); el.setAttribute(NS, "1"); el.style.outline = "2px solid #ff5c8a";
    });
    if (list[0]) list[0].scrollIntoView({ block: "center" });
  }

  function tgt(e) {
    var t = e.target;
    if (e.touches && e.touches[0]) t = document.elementFromPoint(e.touches[0].clientX, e.touches[0].clientY);
    return t && t.nodeType === 1 && !t.hasAttribute("data-pyb-ui") ? t : null;
  }
  function hover(e) { var t = tgt(e); if (t) show(t); }
  function pick(e) {
    var t = tgt(e); if (!t) return;
    e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation();
    show(t);
    send("@pyb:" + nonce + ":" + JSON.stringify(info(t)));
  }

  window.__pybTools = {
    inspect: function (on) {
      var m = on ? "addEventListener" : "removeEventListener";
      document[m]("click", pick, true);
      document[m]("touchstart", hover, { capture: true, passive: true });
      document[m]("mouseover", hover, true);
      if (!on) hide();
    },
    clear: clear,
    test: function (mode, expr) {
      clear();
      var list = [], value = null, hasValue = false;
      try {
        if (mode === "css") {
          list = [].slice.call(document.querySelectorAll(expr));
        } else if (mode === "xpath") {
          var r = document.evaluate(expr, document, null, XPathResult.ANY_TYPE, null);
          if (r.resultType === XPathResult.NUMBER_TYPE) { value = r.numberValue; hasValue = true; }
          else if (r.resultType === XPathResult.STRING_TYPE) { value = r.stringValue; hasValue = true; }
          else if (r.resultType === XPathResult.BOOLEAN_TYPE) { value = r.booleanValue; hasValue = true; }
          else { var n; while ((n = r.iterateNext())) if (n.nodeType === 1) list.push(n); }
        } else {
          var v = (0, eval)(expr);
          if (v instanceof Element) list = [v];
          else if (v && typeof v !== "string" && typeof v.length === "number" && v.length && v[0] instanceof Element) list = [].slice.call(v);
          else { value = v === undefined ? "undefined" : v; hasValue = true; }
        }
      } catch (e) { return JSON.stringify({ ok: false, e: String((e && e.message) || e) }); }
      mark(list);
      var sample = list.slice(0, 5).map(function (el) {
        return { tag: el.tagName.toLowerCase(), css: cssPath(el), text: (el.innerText || el.textContent || "").trim().slice(0, 60) };
      });
      var vs = null;
      if (hasValue) { try { vs = typeof value === "string" ? value : JSON.stringify(value); } catch (e) { vs = String(value); } }
      return JSON.stringify({ ok: true, count: list.length, sample: sample, hasValue: hasValue, value: vs });
    }
  };
  return true;
}
