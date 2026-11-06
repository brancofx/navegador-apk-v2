package br.com.pybrowser

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Run(val id: Long, val script: String, val start: Long, val end: Long, val status: String, val mode: String, val log: String)

/** Scripts (.py em filesDir/scripts), histórico de execuções e agendamentos. */
object Store {
    lateinit var app: Context
    val scripts = mutableStateListOf<File>()
    val history = mutableStateListOf<Run>()
    val schedules = mutableStateOf<Map<String, Int>>(emptyMap())

    val prefs get() = app.getSharedPreferences("pybrowser", Context.MODE_PRIVATE)
    val dir: File get() = File(app.filesDir, "scripts").also { it.mkdirs() }
    val libsDir: File get() = File(app.filesDir, "bibliotecas").also { it.mkdirs() }
    private val histFile get() = File(app.filesDir, "history.json")
    private val main = Handler(Looper.getMainLooper())

    fun init(c: Context) {
        app = c.applicationContext
        if (!prefs.getBoolean("seed", false)) {
            File(dir, "exemplo.py").writeText(app.assets.open("exemplo.py").bufferedReader().use { it.readText() })
            prefs.edit().putBoolean("seed", true).apply()
        }
        refresh()
        loadHistory()
        schedules.value = runCatching {
            val o = JSONObject(prefs.getString("sched", "{}")!!)
            o.keys().asSequence().associateWith { o.getInt(it) }
        }.getOrDefault(emptyMap())
    }

    fun refresh() {
        val list = dir.listFiles { f -> f.isFile && f.extension == "py" }?.sortedBy { it.name.lowercase() } ?: emptyList()
        scripts.clear(); scripts.addAll(list)
    }

    private fun safe(name: String) = name.trim().removeSuffix(".py").replace(Regex("[^\\w .-]"), "_").ifBlank { "script" }

    fun create(name: String, text: String): File {
        val base = safe(name)
        var f = File(dir, "$base.py"); var i = 1
        while (f.exists()) f = File(dir, "${base}_${i++}.py")
        f.writeText(text)
        return f
    }

    /** Retorna o novo arquivo, ou null se o nome já existir. */
    fun rename(file: File, newName: String): File? {
        val nf = File(dir, safe(newName) + ".py")
        if (nf.exists() && nf != file) return null
        return if (file.renameTo(nf)) nf else null
    }

    fun setSchedule(name: String, minutes: Int?) {
        val m = schedules.value.toMutableMap()
        if (minutes == null) m.remove(name) else m[name] = minutes
        schedules.value = m
        prefs.edit().putString("sched", JSONObject(m as Map<*, *>).toString()).apply()
    }

    fun addRun(r: Run) = main.post {
        history.add(0, r)
        while (history.size > 100) history.removeAt(history.lastIndex)
        saveHistory()
    }

    fun clearHistory() { history.clear(); saveHistory() }

    private fun saveHistory() {
        val a = JSONArray()
        history.forEach {
            a.put(JSONObject().put("id", it.id).put("script", it.script).put("start", it.start).put("end", it.end)
                .put("status", it.status).put("mode", it.mode).put("log", it.log))
        }
        runCatching { histFile.writeText(a.toString()) }
    }

    private fun loadHistory() {
        runCatching {
            val a = JSONArray(histFile.takeIf { it.exists() }?.readText() ?: "[]")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                history.add(Run(o.getLong("id"), o.getString("script"), o.getLong("start"), o.getLong("end"),
                    o.getString("status"), o.getString("mode"), o.getString("log")))
            }
        }
    }
}
