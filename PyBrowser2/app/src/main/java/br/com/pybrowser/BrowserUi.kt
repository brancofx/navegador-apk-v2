@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.pybrowser

import android.app.Activity
import android.net.VpnService
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun BrowserScreen(wide: Boolean) {
    var addr by remember { mutableStateOf("") }
    var panel by remember { mutableStateOf(false) }
    var netDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (Engine.url.value.isEmpty()) Engine.go("file:///android_asset/demo.html") }
    LaunchedEffect(Engine.url.value) { addr = Engine.url.value }
    BackHandler(enabled = Engine.canBack.value) { Engine.back() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ Engine.back() }, enabled = Engine.canBack.value, contentPadding = PaddingValues(6.dp)) { Text("◀") }
            TextButton({ Engine.forward() }, enabled = Engine.canForward.value, contentPadding = PaddingValues(6.dp)) { Text("▶") }
            TextButton({ Engine.reload() }, contentPadding = PaddingValues(6.dp)) { Text("⟳") }
            OutlinedTextField(
                addr, { addr = it }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { Engine.go(addr) })
            )
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = Engine.inspecting.value, onClick = {
                val on = !Engine.inspecting.value
                Engine.setInspect(on); if (on) panel = true
            }, label = { Text("🔍 Inspecionar") })
            FilterChip(selected = panel, onClick = { panel = !panel }, label = { Text("🛠 Painel") })
            AssistChip(onClick = { netDialog = true }, label = { Text("🌐 Rede" + if (Net.vpnOn.value) " (VPN)" else "") })
        }
        if (Engine.loading.value) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))

        val web = @Composable { mod: Modifier ->
            AndroidView(
                factory = { Engine.web().also { v -> (v.parent as? ViewGroup)?.removeView(v) } },
                modifier = mod,
                onRelease = { v -> (v.parent as? ViewGroup)?.removeView(v); Engine.offscreen() }
            )
        }
        if (wide && panel) Row(Modifier.fillMaxSize()) {
            web(Modifier.weight(1f).fillMaxHeight())
            VerticalDivider()
            InspectorPanel(Modifier.width(380.dp).fillMaxHeight())
        } else Column(Modifier.fillMaxSize()) {
            web(Modifier.weight(1f).fillMaxWidth())
            if (panel) { HorizontalDivider(); InspectorPanel(Modifier.fillMaxWidth().heightIn(max = 300.dp)) }
        }
    }
    if (netDialog) NetDialog { netDialog = false }
}

@Composable
private fun InspectorPanel(modifier: Modifier) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    var mode by remember { mutableStateOf("css") }
    var expr by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    fun copy(t: String) { clip.setText(AnnotatedString(t)); Toast.makeText(ctx, "Copiado", Toast.LENGTH_SHORT).show() }

    Column(modifier.background(Panel).verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Elemento selecionado", style = MaterialTheme.typography.titleSmall, color = Mint)
        val p = Engine.picked.value
        if (p == null) Text(
            if (Engine.inspecting.value) "Toque em um elemento da página." else "Ative “Inspecionar” e toque em um elemento.",
            fontSize = 13.sp, color = Color.Gray
        ) else SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("<${p.tag}>", fontFamily = FontFamily.Monospace, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("CSS: ${p.css}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text("XPath: ${p.xpath}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                if (p.text.isNotEmpty()) Text("Texto: ${p.text}", fontSize = 12.sp)
                if (p.attrs.isNotEmpty() && p.attrs != "{}") Text("Atributos: ${p.attrs}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
        if (p != null) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton({ copy(p.css) }) { Text("Copiar CSS") }
            OutlinedButton({ copy(p.xpath) }) { Text("Copiar XPath") }
            OutlinedButton({
                val q = p.css.replace("\\", "\\\\").replace("\"", "\\\"")
                copy("driver.find_element(By.CSS_SELECTOR, \"$q\")")
            }) { Text("Copiar Python") }
            OutlinedButton({ mode = "css"; expr = p.css }) { Text("Testar") }
        }
        HorizontalDivider()
        Text("Testar seletor / JavaScript", style = MaterialTheme.typography.titleSmall, color = Mint)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("css" to "CSS", "xpath" to "XPath", "js" to "JavaScript").forEach { (m, l) ->
                FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(l) })
            }
        }
        OutlinedTextField(
            expr, { expr = it }, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 4,
            label = { Text(when (mode) { "css" -> "ex.: form input[type=text]"; "xpath" -> "ex.: //a[contains(text(),'Entrar')]"; else -> "ex.: document.title" }) },
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button({ Engine.test(mode, expr) { result = it } }, enabled = expr.isNotBlank()) { Text("Testar") }
            OutlinedButton({ Engine.clearHighlight(); result = "" }) { Text("Limpar destaque") }
        }
        if (result.isNotEmpty()) SelectionContainer { Text(result, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
    }
}

@Composable
private fun NetDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var proxy by remember { mutableStateOf(Net.proxy.value.ifEmpty { Store.prefs.getString("proxy", "") ?: "" }) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            val text = ctx.contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readText() } ?: ""
            Net.status.value = Net.importProfile(text) ?: "Perfil WireGuard importado."
        }
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) Net.setVpn(true, ctx)
    }

    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClose) { Text("Fechar") } },
        title = { Text("Rede") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("VPN WireGuard", color = Mint)
                Text(if (Net.hasProfile()) "Perfil importado." else "Nenhum perfil importado.", fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ picker.launch(arrayOf("*/*")) }) { Text("Importar .conf") }
                    Button({
                        if (!Net.hasProfile()) Net.status.value = "Importe um perfil WireGuard (.conf) primeiro."
                        else if (Net.vpnOn.value) Net.setVpn(false, ctx)
                        else VpnService.prepare(ctx).let { i -> if (i != null) consent.launch(i) else Net.setVpn(true, ctx) }
                    }) { Text(if (Net.vpnOn.value) "Desligar" else "Ligar") }
                }
                HorizontalDivider()
                Text("Proxy do navegador", color = Mint)
                OutlinedTextField(proxy, { proxy = it }, singleLine = true, label = { Text("host:porta") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ Net.setProxy(proxy, ctx) }, enabled = proxy.isNotBlank()) { Text("Aplicar") }
                    OutlinedButton({ proxy = ""; Net.setProxy("", ctx) }) { Text("Remover") }
                }
                if (Net.status.value.isNotEmpty()) Text(Net.status.value, fontSize = 12.sp, color = Color.Gray)
            }
        }
    )
}
