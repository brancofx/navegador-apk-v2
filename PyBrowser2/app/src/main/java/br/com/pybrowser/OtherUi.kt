@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.pybrowser

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val mono = FontFamily.Monospace
private fun fmt(t: Long) = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(t))

// ---------------------------------------------------------------- Logs em tempo real + terminal
@Composable
fun LogsScreen() {
    val scroll = rememberScrollState()
    val clip = LocalClipboardManager.current
    var cmd by remember { mutableStateOf("") }
    LaunchedEffect(Engine.log.value) { scroll.scrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (Engine.running.value) "● Executando: ${Engine.current.value}" else "Logs",
                color = if (Engine.running.value) Mint else Color.Unspecified,
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1
            )
            if (Engine.running.value) Button({ Engine.cancel = true }) { Text("■ Parar") }
            TextButton({ clip.setText(AnnotatedString(Engine.log.value)) }) { Text("Copiar") }
            TextButton({ Engine.clearLog() }) { Text("Limpar") }
        }
        SelectionContainer(Modifier.weight(1f).fillMaxWidth().background(CodeBg).verticalScroll(scroll).padding(8.dp)) {
            Text(Engine.log.value.ifEmpty { "Os logs aparecem aqui em tempo real." }, fontFamily = mono, fontSize = 13.sp, color = Color(0xFFD6E2F0))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                cmd, { cmd = it }, singleLine = true, modifier = Modifier.weight(1f), enabled = !Engine.running.value,
                label = { Text("Python ou pip install nome") }, textStyle = androidx.compose.ui.text.TextStyle(fontFamily = mono),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = false)
            )
            Spacer(Modifier.width(8.dp))
            Button(enabled = !Engine.running.value, onClick = {
                val c = cmd; cmd = ""
                Engine.append(">>> $c\n"); Engine.start(c, "terminal", true, "terminal")
            }) { Text("Enviar") }
        }
    }
}

// ---------------------------------------------------------------- Bibliotecas
private data class Lib(val name: String, val version: String)

private fun loadLibs(): List<Lib> = runCatching {
    val a = JSONArray(Python.getInstance().getModule("libs").callAttr("list_installed", Store.libsDir.absolutePath).toString())
    (0 until a.length()).map { Lib(a.getJSONObject(it).getString("name"), a.getJSONObject(it).getString("version")) }
}.getOrDefault(emptyList())

@Composable
fun LibsScreen() {
    var name by remember { mutableStateOf("") }
    var libs by remember { mutableStateOf<List<Lib>>(emptyList()) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val busy = Engine.running.value

    // Recarrega a lista ao abrir a tela e sempre que uma instalação termina.
    LaunchedEffect(busy) { if (!busy) libs = withContext(Dispatchers.IO) { loadLibs() } }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Bibliotecas Python", style = MaterialTheme.typography.titleLarge)
        Text("Instala pacotes do PyPI (apenas wheels). Pacotes com código nativo podem não estar disponíveis para Android.", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                name, { name = it.trim() }, singleLine = true, modifier = Modifier.weight(1f), enabled = !busy,
                label = { Text("ex.: requests  ou  beautifulsoup4==4.12.3") },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false)
            )
            Spacer(Modifier.width(8.dp))
            Button(enabled = !busy && name.isNotBlank(), onClick = {
                Engine.append(">>> pip install $name\n"); Engine.start("pip install $name", "pip", true, "pip"); name = ""
                msg = "Instalando… acompanhe abaixo."
            }) { Text("Instalar") }
        }
        if (busy || msg.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(Engine.log.value.takeLast(500), fontFamily = mono, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth().background(CodeBg).padding(6.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text("Instaladas (${libs.size})", color = Mint)
        LazyColumn(Modifier.weight(1f)) {
            items(libs, key = { it.name }) { l ->
                ListItem(
                    headlineContent = { Text(l.name) }, supportingContent = { Text(l.version) },
                    trailingContent = {
                        TextButton(enabled = !busy, onClick = {
                            scope.launch {
                                msg = withContext(Dispatchers.IO) {
                                    runCatching {
                                        Python.getInstance().getModule("libs").callAttr("uninstall", l.name, Store.libsDir.absolutePath); "${l.name} removida."
                                    }.getOrElse { "Falha ao remover: ${it.message}" }
                                }
                                libs = withContext(Dispatchers.IO) { loadLibs() }
                            }
                        }) { Text("Remover", color = MaterialTheme.colorScheme.error) }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                HorizontalDivider(color = Panel)
            }
        }
    }
}

// ---------------------------------------------------------------- Histórico
@Composable
fun HistoryScreen() {
    var sel by remember { mutableStateOf<Run?>(null) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Histórico de execuções", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton({ Store.clearHistory() }, enabled = Store.history.isNotEmpty()) { Text("Limpar") }
        }
        if (Store.history.isEmpty()) Text("Nenhuma execução ainda.", color = Color.Gray)
        LazyColumn {
            items(Store.history.toList(), key = { it.id }) { r ->
                val icon = when (r.status) { "concluído" -> "✅"; "erro" -> "❌"; "interrompido" -> "⏹"; else -> "ℹ️" }
                ListItem(
                    headlineContent = { Text("$icon ${r.script}") },
                    supportingContent = { Text("${fmt(r.start)} · ${"%.1f".format((r.end - r.start) / 1000.0)} s · ${r.mode}") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { sel = r }
                )
                HorizontalDivider(color = Panel)
            }
        }
    }
    sel?.let { r ->
        AlertDialog(
            onDismissRequest = { sel = null },
            title = { Text(r.script, maxLines = 1) },
            text = {
                SelectionContainer(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(r.log.ifEmpty { "(sem saída)" }, fontFamily = mono, fontSize = 12.sp)
                }
            },
            confirmButton = {
                val f = File(Store.dir, r.script)
                TextButton(enabled = f.exists() && !Engine.running.value, onClick = {
                    Engine.start(f.readText(), r.script, false, "primeiro plano"); sel = null
                }) { Text("Executar de novo") }
            },
            dismissButton = { TextButton({ sel = null }) { Text("Fechar") } }
        )
    }
}
