function operate(storeKey, op, args) {
  let store = window[storeKey];
  if (!store) {
    store = { prefix: Math.random().toString(36).slice(2) + ":", next: 1, elements: new Map() };
    Object.defineProperty(window, storeKey, { value: store });
  }
  function remember(element) {
    const id = store.prefix + store.next++;
    store.elements.set(id, element);
    return id;
  }
  function resolve(id) {
    const element = store.elements.get(id);
    if (!element || !element.isConnected) throw Error("Elemento não pertence mais à página.");
    return element;
  }
  function find(root, by, value, all) {
    let elements;
    if (by === "xpath") {
      const result = document.evaluate(value, root, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE);
      elements = [];
      for (let i = 0; i < result.snapshotLength; i++) {
        const node = result.snapshotItem(i);
        if (node instanceof Element) elements.push(node);
        if (elements.length && !all) break;
      }
    } else {
      const selector = {
        "css selector": () => value,
        "id": () => "#" + CSS.escape(value),
        "name": () => "[name=" + CSS.escape(value) + "]",
        "class name": () => "." + CSS.escape(value),
        "tag name": () => CSS.escape(value),
        "link text": () => "a",
        "partial link text": () => "a"
      }[by];
      if (!selector) throw Error("Localizador inválido: " + by);
      if (by === "link text" || by === "partial link text") {
        elements = Array.from(root.querySelectorAll("a")).filter(element =>
          by === "link text" ? element.innerText.trim() === value : element.innerText.includes(value));
      } else {
        if (!all) {
          const element = root.querySelector(selector());
          return element ? remember(element) : null;
        }
        elements = Array.from(root.querySelectorAll(selector()));
      }
    }
    return all ? elements.map(remember) : (elements.length ? remember(elements[0]) : null);
  }
  function visible(element) {
    const style = getComputedStyle(element);
    return !!element.getClientRects().length && style.visibility === "visible";
  }
  function edit(element, value, clear) {
    if (!visible(element) || element.matches(":disabled") || element.readOnly) {
      throw Error("O campo precisa estar visível e editável.");
    }
    element.focus();
    const isInput = element instanceof HTMLInputElement && !element.matches(
      '[type=button],[type=checkbox],[type=radio],[type=file],[type=hidden],[type=submit],[type=reset]');
    if (isInput || element instanceof HTMLTextAreaElement) {
      const prototype = isInput ? HTMLInputElement.prototype : HTMLTextAreaElement.prototype;
      const text = clear ? "" : element.value + value;
      Object.getOwnPropertyDescriptor(prototype, "value").set.call(element, text);
    } else if (element.isContentEditable) {
      element.textContent = clear ? "" : element.textContent + value;
    } else {
      throw Error("O elemento não é um campo de texto.");
    }
    element.dispatchEvent(new Event("input", { bubbles: true }));
    element.dispatchEvent(new Event("change", { bubbles: true }));
  }

  switch (op) {
    case "find": return find(args[0] == null ? document : resolve(args[0]), args[1], args[2], args[3]);
    case "title": return document.title;
    case "url": return location.href;
    case "html": return document.documentElement.outerHTML;
    case "scroll": window.scrollBy(args[0], args[1]); return null;
    case "script": return new Function(args[0]).apply(window, args[1].map(arg =>
      arg[0] === "element" ? resolve(arg[1]) : arg[1]));
  }
  const element = resolve(args[0]);
  switch (op) {
    case "click":
      if (!visible(element) || element.matches(":disabled")) throw Error("Elemento indisponível para clique.");
      element.scrollIntoView({ block: "center" });
      element.click();
      return null;
    case "type": edit(element, args[1], false); return null;
    case "clear": edit(element, "", true); return null;
    case "text": return element.innerText || "";
    case "attribute": return element.getAttribute(args[1]);
    case "property": return element[args[1]];
    case "visible": return visible(element);
    case "enabled": return !element.matches(":disabled");
    default: throw Error("Comando desconhecido: " + op);
  }
}
