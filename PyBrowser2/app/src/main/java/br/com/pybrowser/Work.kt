package br.com.pybrowser

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Executa um script em segundo plano, com notificação de serviço em primeiro plano quando o Android permitir. */
class ScriptWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    private fun info(name: String): ForegroundInfo {
        val n = NotificationCompat.Builder(applicationContext, "run")
            .setSmallIcon(R.drawable.ic_stat).setContentTitle("PyBrowser")
            .setContentText("Executando $name").setOngoing(true).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(1, n)
    }

    override suspend fun getForegroundInfo() = info(inputData.getString("name") ?: "script")

    override suspend fun doWork(): Result {
        val name = inputData.getString("name") ?: return Result.failure()
        val f = File(Store.dir, name)
        if (!f.exists()) return Result.failure()
        // Android 12+ pode negar serviço em primeiro plano a partir do segundo plano: segue sem notificação.
        runCatching { setForeground(info(name)) }
        withContext(Dispatchers.Main) { Engine.web() }
        val status = Engine.run(f.readText(), name, false, "segundo plano")
        return if (status == "concluído") Result.success() else Result.failure()
    }
}

object Sched {
    private val wm get() = WorkManager.getInstance(Store.app)

    fun runNow(name: String) {
        val req = OneTimeWorkRequestBuilder<ScriptWorker>()
            .setInputData(workDataOf("name" to name))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
        wm.enqueueUniqueWork("run:$name", ExistingWorkPolicy.REPLACE, req)
    }

    /** O Android impõe mínimo de 15 min e pode atrasar a execução (economia de bateria / Doze). */
    fun every(name: String, minutes: Int) {
        val m = minutes.coerceAtLeast(15).toLong()
        val req = PeriodicWorkRequestBuilder<ScriptWorker>(m, TimeUnit.MINUTES)
            .setInputData(workDataOf("name" to name)).build()
        wm.enqueueUniquePeriodicWork("periodic:$name", ExistingPeriodicWorkPolicy.UPDATE, req)
        Store.setSchedule(name, m.toInt())
    }

    fun stop(name: String) {
        wm.cancelUniqueWork("periodic:$name"); wm.cancelUniqueWork("run:$name")
        Store.setSchedule(name, null)
    }
}
