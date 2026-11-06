package br.com.pybrowser

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File

val Mint = Color(0xFF72E8C9)
val Ink = Color(0xFF0E1725)
val Panel = Color(0xFF18263A)
val CodeBg = Color(0xFF0B1220)

private val scheme = darkColorScheme(
    primary = Mint, onPrimary = Color(0xFF112924), background = Ink, surface = Ink, surfaceVariant = Panel,
    onBackground = Color(0xFFEEF5FF), onSurface = Color(0xFFEEF5FF), secondary = Color(0xFF82AAFF), error = Color(0xFFFF6B8B)
)

@Composable
fun PyTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)

/** Estado que precisa sobreviver à troca de abas. */
object UiState { val editing = mutableStateOf<File?>(null) }

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        Store.init(this)
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel("run", "Execução de scripts", NotificationManager.IMPORTANCE_LOW))
        }
        Net.applySavedProxy(this)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { PyTheme { AppRoot() } }
    }
}

@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val wide = LocalConfiguration.current.screenWidthDp >= 600          // tablet / celular deitado
    val tabs = listOf("📝" to "Scripts", "🌐" to "Navegador", "📟" to "Logs", "📦" to "Libs", "🕘" to "Histórico")

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxSize()) {
            if (wide) NavigationRail(containerColor = Panel) {
                Spacer(Modifier.height(8.dp))
                tabs.forEachIndexed { i, (ic, l) ->
                    NavigationRailItem(selected = tab == i, onClick = { tab = i }, icon = { Text(ic) }, label = { Text(l) })
                }
            }
            Column(Modifier.weight(1f)) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        0 -> ScriptsScreen(wide) { tab = 2 }
                        1 -> BrowserScreen(wide)
                        2 -> LogsScreen()
                        3 -> LibsScreen()
                        else -> HistoryScreen()
                    }
                }
                if (!wide) NavigationBar(containerColor = Panel) {
                    tabs.forEachIndexed { i, (ic, l) ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(ic) }, label = { Text(l, maxLines = 1) })
                    }
                }
            }
        }
    }
}
