@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.pybrowser

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** Destaque de sintaxe Python: comentários, strings (inclusive de aspas triplas), decoradores, números, palavras-chave, builtins, nomes de def/class. */
object PyHighlight : VisualTransformation {
    private val keywords = setOf("False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue",
        "def", "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda",
        "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield", "match", "case")
    private val builtins = setOf("print", "len", "range", "int", "str", "float", "bool", "list", "dict", "set", "tuple", "open",
        "input", "type", "isinstance", "enumerate", "zip", "map", "filter", "sum", "min", "max", "abs", "sorted", "reversed",
        "any", "all", "super", "self", "repr", "round", "driver", "By", "WebDriverWait", "EC")
    // \x22 = aspas duplas, \x27 = aspas simples (evita escapes em Kotlin)
    private val token = Regex(
        """(#[^\n]*)|(\x22{3}[\s\S]*?(?:\x22{3}|\z)|\x27{3}[\s\S]*?(?:\x27{3}|\z)|\x22(?:\\.|[^\x22\\\n])*\x22?|\x27(?:\\.|[^\x27\\\n])*\x27?)|(@[A-Za-z_][\w.]*)|(\b\d+(?:\.\d+)?\b)|(\b[A-Za-z_]\w*\b)"""
    )

    override fun filter(text: AnnotatedString): TransformedText {
        val src = text.text
        val styled = buildAnnotatedString {
            append(src)
            var prev = ""
            for (m in token.findAll(src)) {
                val g = m.groups
                val style: SpanStyle? = when {
                    g[1] != null -> SpanStyle(color = Color(0xFF7C8A9E), fontStyle = FontStyle.Italic)
                    g[2] != null -> SpanStyle(color = Color(0xFFF6C177))
                    g[3] != null -> SpanStyle(color = Color(0xFFFFCB6B))
                    g[4] != null -> SpanStyle(color = Color(0xFFF78C6C))
                    else -> {
                        val w = m.value
                        val s: SpanStyle? = when {
                            prev == "def" || prev == "class" -> SpanStyle(color = Mint)
                            w in keywords -> SpanStyle(color = Color(0xFFC792EA))
                            w in builtins -> SpanStyle(color = Color(0xFF82AAFF))
                            else -> null
                        }
                        prev = w
                        s
                    }
                }
                if (style != null) addStyle(style, m.range.first, m.range.last + 1)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

/** Ao apertar Enter, mantém a indentação da linha anterior (+4 espaços depois de ':'). */
private fun autoIndent(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    if (new.text.length == old.text.length + 1 && new.selection.collapsed) {
        val pos = new.selection.start
        if (pos > 0 && new.text[pos - 1] == '\n') {
            val prevStart = new.text.lastIndexOf('\n', pos - 2) + 1
            val prev = new.text.substring(prevStart, pos - 1)
            var ind = prev.takeWhile { it == ' ' || it == '\t' }
            if (prev.trimEnd().endsWith(":")) ind += "    "
            if (ind.isNotEmpty()) {
                return new.copy(text = new.text.substring(0, pos) + ind + new.text.substring(pos), selection = TextRange(pos + ind.length))
            }
        }
    }
    return new
}

@Composable
fun ScriptsScreen(wide: Boolean, goLogs: () -> Unit) {
    val ctx = LocalContext.current
    var editing by UiState.editing
    var newDialog by remember { mutableStateOf(false) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        var n = 0
        uris.forEach { u ->
            val name = ctx.contentResolver.query(u, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
            } ?: "importado.py"
            if (name.endsWith(".py", ignoreCase = true)) {
                val text = ctx.contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readText() }
                if (text != null) { Store.create(name, text); n++ }
            }
        }
        Store.refresh()
        Toast.makeText(ctx, "$n script(s) .py importado(s)", Toast.LENGTH_SHORT).show()
    }

    BackHandler(enabled = !wide && editing != null) { editing = null }

    val list = @Composable { mod: Modifier ->
        ScriptList(mod, editing, onOpen = { editing = it }, onNew = { newDialog = true },
            onImport = { importer.launch(arrayOf("*/*")) })
    }
    val ed = editing
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            list(Modifier.width(320.dp).fillMaxHeight())
            VerticalDivider()
            if (ed != null) Editor(ed, wide, { editing = null }, goLogs, Modifier.weight(1f))
            else Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { Text("Selecione ou crie um script", color = Color.Gray) }
        }
    } else {
        if (ed == null) list(Modifier.fillMaxSize()) else Editor(ed, wide, { editing = null }, goLogs, Modifier.fillMaxSize())
    }

    if (newDialog) NameDialog("Novo script", "") { name ->
        newDialog = false
        if (name != null) {
            editing = Store.create(name, "from navegador import By, driver\n\ndriver.get(\"https://example.com\")\nprint(driver.title)\n")
            Store.refresh()
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDone: (String?) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(title) },
        text = { OutlinedTextField(v, { v = it }, singleLine = true, label = { Text("Nome (sem .py)") }) },
        confirmButton = { TextButton({ onDone(v) }, enabled = v.isNotBlank()) { Text("OK") } },
        dismissButton = { TextButton({ onDone(null) }) { Text("Cancelar") } }
    )
}

@Composable
private fun ScriptList(modifier: Modifier, selected: File?, onOpen: (File) -> Unit, onNew: () -> Unit, onImport: () -> Unit) {
    Column(modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Scripts", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = onNew) { Text("＋ Novo") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onImport) { Text("Importar") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(Store.scripts.toList(), key = { it.name }) { f ->
                val sched = Store.schedules.value[f.name]
                ListItem(
                    headlineContent = { Text(f.name) },
                    supportingContent = { Text("%.1f KB".format(f.length() / 1024.0) + (sched?.let { " · ⏱ a cada $it min" } ?: "")) },
                    colors = ListItemDefaults.colors(containerColor = if (f == selected) Panel else Color.Transparent),
                    modifier = Modifier.clickable { onOpen(f) }
                )
                HorizontalDivider(color = Panel)
            }
        }
    }
}

@Composable
private fun Editor(file: File, wide: Boolean, onClose: () -> Unit, goLogs: () -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    var tf by remember(file) { mutableStateOf(TextFieldValue(runCatching { file.readText() }.getOrDefault(""))) }
    var dirty by remember(file) { mutableStateOf(false) }
    val latest by rememberUpdatedState(tf)
    val latestDirty by rememberUpdatedState(dirty)
    var menu by remember { mutableStateOf(false) }
    var dlg by remember { mutableStateOf("") }

    fun save() { runCatching { file.writeText(tf.text) }; dirty = false }
    DisposableEffect(file) { onDispose { if (latestDirty && file.exists()) runCatching { file.writeText(latest.text) } } }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-python")) { uri ->
        uri?.let { u ->
            runCatching { ctx.contentResolver.openOutputStream(u)?.use { it.write(latest.text.toByteArray()) } }
                .onSuccess { Toast.makeText(ctx, "Exportado", Toast.LENGTH_SHORT).show() }
        }
    }
    fun insert(t: String) {
        val s = tf.selection
        tf = TextFieldValue(tf.text.replaceRange(s.min, s.max, t), TextRange(s.min + t.length)); dirty = true
    }

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!wide) TextButton(onClose) { Text("◀") }
            Text(file.name + if (dirty) " •" else "", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { save(); Engine.start(tf.text, file.name, false, "primeiro plano"); goLogs() }) { Text("▶ Executar") }
            OutlinedButton(onClick = {
                save(); Sched.runNow(file.name)
                Toast.makeText(ctx, "Enfileirado em segundo plano. Veja Logs e Histórico.", Toast.LENGTH_LONG).show()
            }) { Text("⏳ Segundo plano") }
            OutlinedButton(onClick = { save() }, enabled = dirty) { Text("💾 Salvar") }
            Box {
                OutlinedButton(onClick = { menu = true }) { Text("⋮ Mais") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Exportar .py") }, onClick = { menu = false; save(); exporter.launch(file.name) })
                    DropdownMenuItem(text = { Text("Renomear") }, onClick = { menu = false; dlg = "rename" })
                    DropdownMenuItem(text = { Text("Duplicar") }, onClick = {
                        menu = false; save()
                        val c = Store.create(file.nameWithoutExtension + "_copia", tf.text); Store.refresh(); UiState.editing.value = c
                    })
                    DropdownMenuItem(text = { Text("Agendar repetição…") }, onClick = { menu = false; dlg = "sched" })
                    DropdownMenuItem(text = { Text("Excluir") }, onClick = { menu = false; dlg = "delete" })
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.weight(1f).fillMaxWidth().background(CodeBg).verticalScroll(rememberScrollState()).padding(8.dp)) {
            BasicTextField(
                value = tf,
                onValueChange = { tf = autoIndent(tf, it); dirty = true },
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 300.dp),
                textStyle = TextStyle(color = Color(0xFFE6EDF7), fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp),
                cursorBrush = SolidColor(Mint),
                visualTransformation = PyHighlight,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None)
            )
        }
        // Teclas rápidas que o teclado do celular esconde
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(Panel).padding(horizontal = 4.dp)) {
            listOf("⇥" to "    ", ":" to ":", "(" to "(", ")" to ")", "[" to "[", "]" to "]", "{" to "{", "}" to "}",
                "\"" to "\"", "'" to "'", "=" to "=", "#" to "#", "_" to "_", "." to ".", "," to ",").forEach { (label, text) ->
                TextButton(onClick = { insert(text) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(label, fontFamily = FontFamily.Monospace) }
            }
        }
    }

    when (dlg) {
        "rename" -> NameDialog("Renomear", file.nameWithoutExtension) { n ->
            dlg = ""
            if (n != null) {
                save()
                val nf = Store.rename(file, n)
                if (nf == null) Toast.makeText(ctx, "Já existe um script com esse nome.", Toast.LENGTH_SHORT).show()
                else { Sched.stop(file.name); Store.refresh(); UiState.editing.value = nf }
            }
        }
        "delete" -> AlertDialog(
            onDismissRequest = { dlg = "" }, title = { Text("Excluir ${file.name}?") }, text = { Text("Esta ação não pode ser desfeita.") },
            confirmButton = { TextButton({ dlg = ""; Sched.stop(file.name); file.delete(); Store.refresh(); UiState.editing.value = null }) { Text("Excluir") } },
            dismissButton = { TextButton({ dlg = "" }) { Text("Cancelar") } })
        "sched" -> {
            var min by remember { mutableStateOf((Store.schedules.value[file.name] ?: 15).toString()) }
            AlertDialog(
                onDismissRequest = { dlg = "" }, title = { Text("Repetir em segundo plano") },
                text = {
                    Column {
                        OutlinedTextField(min, { min = it.filter(Char::isDigit) }, singleLine = true, label = { Text("A cada quantos minutos (mín. 15)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Spacer(Modifier.height(8.dp))
                        Text("O Android decide o horário exato e pode atrasar ou pular execuções (economia de bateria, Doze).", fontSize = 12.sp, color = Color.Gray)
                    }
                },
                confirmButton = { TextButton({ save(); Sched.every(file.name, min.toIntOrNull() ?: 15); dlg = "" }) { Text("Agendar") } },
                dismissButton = {
                    Row {
                        if (Store.schedules.value.containsKey(file.name)) TextButton({ Sched.stop(file.name); dlg = "" }) { Text("Cancelar agendamento") }
                        TextButton({ dlg = "" }) { Text("Fechar") }
                    }
                })
        }
    }
}
