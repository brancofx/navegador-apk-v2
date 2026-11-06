package br.com.pybrowser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.*
import androidx.compose.runtime.mutableStateOf
import com.chaquo.python.Python
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class Picked(val tag: String, val css: String, val xpath: String, val text: String, val attrs: String, val html: String)

/** Ponte entre o Python (runner.py/navegador.py) e o app. Mesmo contrato do PyBrowser 1.0. */
class Bridge {
    fun log(text: String) { Engine.append(text) }
    fun cancelled(): Boolean = Engine.cancel
    fun command(op: String, args: String): String =
        if (op == "get") Engine.load(JSONArray(args).getString(0)) else Engine.evalOp(op, args)
}

object Engine {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private val nonce = UUID.randomUUID().toString().replace("-", "")
    private val busy = AtomicBoolean(false)
    private val sb = StringBuilder()
    @Volatile private var runLog: StringBuilder? = null
    @Volatile var cancel = false

    // Estado observado pela interface
    val log = mutableStateOf("")
    val running = mutableStateOf(false)
    val current = mutableStateOf("")
    val url = mutableStateOf("")
    val title = mutableStateOf("")
    val loading = mutableStateOf(false)
    val canBack = mutableStateOf(false)
    val canForward = mutableStateOf(false)
    val inspecting = mutableStateOf(false)
    val picked = mutableStateOf<Picked?>(null)

    @Volatile private var pageLatch: CountDownLatch? = null
    @Volatile private var loadError: String? = null
    private var wv: WebView? = null
    private val automacao by lazy { Store.app.assets.open("automacao.js").bufferedReader().use { it.readText() } }
    private val inspector by lazy { Store.app.assets.open("inspector.js").bufferedReader().use { it.readText() } }

    // ---------- WebView compartilhada (aba Navegador + scripts + segundo plano) ----------
    @SuppressLint("SetJavaScriptEnabled")
    fun web(): WebView {
        wv?.let { return it }
        val w = WebView(Store.app)
        w.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            builtInZoomControls = true; displayZoomControls = false
            useWideViewPort = true; loadWithOverviewMode = true
        }
        w.webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView, u: String, f: Bitmap?) { url.value = u; loading.value = true }
            override fun onPageFinished(v: WebView, u: String) {
                url.value = u; title.value = v.title ?: ""; loading.value = false
                canBack.value = v.canGoBack(); canForward.value = v.canGoForward()
                v.evaluateJavascript("($inspector)('$nonce');", null)
                if (inspecting.value) v.evaluateJavascript("window.__pybTools && window.__pybTools.inspect(true)", null)
                pageLatch?.countDown()
            }
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (r.isForMainFrame) { loadError = e.description.toString(); pageLatch?.countDown() }
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                val t = m.message()
                val tag = "@pyb:$nonce:"
                if (t.startsWith(tag)) { runCatching { parsePick(JSONObject(t.substring(tag.length))) }; return true }
                if (running.value) append("[js] $t\n")
                return true
            }
        }
        wv = w
        offscreen()
        w.onResume(); w.resumeTimers()
        return w
    }

    /** Sem tela (segundo plano / aba trocada): dá um tamanho fixo para que checagens de visibilidade funcionem. */
    fun offscreen() {
        val w = wv ?: return
        if (w.parent != null) return
        w.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
        w.layout(0, 0, 1080, 1920)
    }

    fun normalize(raw: String): String {
        val s = raw.trim()
        return when {
            Regex("^(https?|file|about|data):", RegexOption.IGNORE_CASE).containsMatchIn(s) -> s
            s.contains('.') && !s.contains(' ') -> "https://$s"
            else -> "https://duckduckgo.com/?q=" + URLEncoder.encode(s, "UTF-8")
        }
    }
    fun go(u: String) { web().loadUrl(normalize(u)) }
    fun back() { web().goBack() }
    fun forward() { web().goForward() }
    fun reload() { web().reload() }

    private fun parsePick(o: JSONObject) {
        picked.value = Picked(o.optString("tag"), o.optString("css"), o.optString("xpath"), o.optString("text"),
            o.optJSONObject("attrs")?.toString(2) ?: "", o.optString("html"))
    }

    private fun parseEval(raw: String?): JSONObject? = runCatching {
        JSONObject(JSONTokener(raw ?: "null").nextValue() as String)
    }.getOrNull()

    // ---------- Inspeção e testes de seletor (thread principal) ----------
    fun setInspect(on: Boolean) {
        inspecting.value = on
        web().evaluateJavascript("window.__pybTools && window.__pybTools.inspect($on)", null)
    }

    fun clearHighlight() { web().evaluateJavascript("window.__pybTools && window.__pybTools.clear()", null) }

    /** mode: "css", "xpath" ou "js". Resultado em texto pronto para exibir. */
    fun test(mode: String, expr: String, cb: (String) -> Unit) {
        val js = "(function(){try{return window.__pybTools.test(" + JSONObject.quote(mode) + "," + JSONObject.quote(expr) +
            ")}catch(e){return JSON.stringify({ok:false,e:String(e)})}})()"
        web().evaluateJavascript(js) { raw ->
            val o = parseEval(raw)
            cb(when {
                o == null -> "A página não respondeu (ainda carregando?)."
                !o.optBoolean("ok") -> "Erro: " + o.optString("e")
                else -> buildString {
                    append("${o.optInt("count")} elemento(s)")
                    val s = o.optJSONArray("sample")
                    if (s != null) for (i in 0 until s.length()) {
                        val e = s.getJSONObject(i)
                        append("\n• <${e.optString("tag")}> ${e.optString("css")}")
                        if (e.optString("text").isNotEmpty()) append("  “${e.optString("text")}”")
                    }
                    if (o.optBoolean("hasValue")) append("\nValor: " + o.optString("value", "null"))
                }
            })
        }
    }

    // ---------- Comandos vindos do Python (thread de execução) ----------
    private fun waitFor(l: CountDownLatch, secs: Long) {
        val end = System.currentTimeMillis() + secs * 1000
        while (!l.await(200, TimeUnit.MILLISECONDS)) {
            if (cancel) throw RuntimeException("Execução interrompida.")
            if (System.currentTimeMillis() > end) throw RuntimeException("Tempo esgotado aguardando o navegador.")
        }
    }

    fun load(raw: String): String {
        val latch = CountDownLatch(1)
        pageLatch = latch; loadError = null
        main.post { web().loadUrl(normalize(raw)) }
        waitFor(latch, 45)
        loadError?.let { throw RuntimeException("Falha ao carregar a página: $it") }
        return "null"
    }

    fun evalOp(op: String, argsJson: String): String {
        val js = "(function(){try{" + automacao + "\n;var r=operate('__pyb'," + JSONObject.quote(op) + "," + argsJson +
            ");return JSON.stringify({ok:true,v:r===undefined?null:r})}catch(e){return JSON.stringify({ok:false,e:String((e&&e.message)||e)})}})()"
        val latch = CountDownLatch(1)
        var raw: String? = null
        main.post { web().evaluateJavascript(js) { raw = it; latch.countDown() } }
        waitFor(latch, 60)
        val o = parseEval(raw) ?: throw RuntimeException("Resposta inválida do navegador (a página mudou durante o comando?).")
        if (!o.optBoolean("ok")) throw RuntimeException(o.optString("e"))
        val arr = JSONArray().put(o.opt("v") ?: JSONObject.NULL).toString()   // "[valor]" -> "valor"
        return arr.substring(1, arr.length - 1)
    }

    // ---------- Execução, logs em tempo real, histórico ----------
    fun append(t: String) {
        runLog?.let { if (it.length < 50_000) it.append(t) }
        synchronized(sb) {
            sb.append(t)
            if (sb.length > 60_000) sb.delete(0, sb.length - 50_000)
            log.value = sb.toString()
        }
    }

    fun clearLog() = synchronized(sb) { sb.setLength(0); log.value = "" }

    fun start(source: String, name: String, interactive: Boolean, mode: String) {
        scope.launch { run(source, name, interactive, mode) }
    }

    suspend fun run(source: String, name: String, interactive: Boolean, mode: String): String = withContext(Dispatchers.IO) {
        if (!busy.compareAndSet(false, true)) { append("Já existe uma execução em andamento.\n"); return@withContext "ocupado" }
        cancel = false; running.value = true; current.value = name
        val started = System.currentTimeMillis()
        val rl = StringBuilder(); runLog = rl
        if (!interactive) append("▶ $name ($mode)\n")
        val status = try {
            Python.getInstance().getModule("runner")
                .callAttr("run", source, name, Bridge(), interactive, Store.app.filesDir.absolutePath).toString()
        } catch (e: Throwable) { append("Erro interno: ${e.message}\n"); "erro" }
        if (!interactive) append("■ $status\n")
        runLog = null; running.value = false; busy.set(false)
        if (!interactive) Store.addRun(Run(started, name, started, System.currentTimeMillis(), status, mode, rl.toString()))
        status
    }
}
