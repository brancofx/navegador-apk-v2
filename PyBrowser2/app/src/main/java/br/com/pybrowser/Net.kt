package br.com.pybrowser

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.File
import kotlin.concurrent.thread

/** VPN WireGuard (perfil .conf) e proxy do navegador. */
object Net {
    val vpnOn = mutableStateOf(false)
    val status = mutableStateOf("")
    val proxy = mutableStateOf("")
    private var backend: GoBackend? = null

    private val tunnel = object : Tunnel {
        override fun getName() = "pybrowser"
        override fun onStateChange(newState: Tunnel.State) { vpnOn.value = newState == Tunnel.State.UP }
    }

    private val profile get() = File(Store.app.filesDir, "wg.conf")
    fun hasProfile() = profile.exists()

    /** Retorna null se OK, ou a mensagem de erro. */
    fun importProfile(text: String): String? = try {
        Config.parse(text.byteInputStream().bufferedReader())
        profile.writeText(text); null
    } catch (e: Exception) { "Perfil WireGuard inválido: ${e.message}" }

    fun setVpn(on: Boolean, ctx: Context) {
        thread {
            try {
                val b = backend ?: GoBackend(ctx.applicationContext).also { backend = it }
                val cfg = if (on) Config.parse(profile.bufferedReader()) else null
                b.setState(tunnel, if (on) Tunnel.State.UP else Tunnel.State.DOWN, cfg)
                status.value = if (on) "VPN ligada." else "VPN desligada."
            } catch (e: Throwable) { status.value = "Falha ao alterar a VPN: ${e.message}" }
        }
    }

    fun setProxy(hostPort: String, ctx: Context) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            status.value = "Este WebView não suporta proxy."; return
        }
        val ex = ContextCompat.getMainExecutor(ctx)
        val v = hostPort.trim()
        proxy.value = v
        Store.prefs.edit().putString("proxy", v).apply()
        if (v.isEmpty()) ProxyController.getInstance().clearProxyOverride(ex) { status.value = "Proxy removido." }
        else ProxyController.getInstance().setProxyOverride(
            ProxyConfig.Builder().addProxyRule(v).build(), ex   // sem addDirect(): se o proxy cair, não vaza tráfego
        ) { status.value = "Proxy ativo: $v" }
    }

    fun applySavedProxy(ctx: Context) {
        val v = Store.prefs.getString("proxy", "") ?: ""
        if (v.isNotEmpty()) runCatching { setProxy(v, ctx) }
    }
}
