package io.github.ddmoyu.picomic.download

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import io.github.ddmoyu.picomic.MainActivity
import io.github.ddmoyu.picomic.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.TimeUnit

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: DownloadRepository
    private var observer: Job? = null
    private val owner = java.util.UUID.randomUUID().toString()
    override fun onCreate() {
        super.onCreate(); repository = DownloadRepository.get(this)
        channels(this)
        getSystemService(NotificationManager::class.java).cancel(4104)
        val notification = notice(this, "正在准备下载", active = true)
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(ID, notification)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == PAUSE) {
            scope.launch { try { repository.stop() } catch (e: CancellationException) { throw e }
                catch (_: Exception) { io.github.ddmoyu.picomic.data.EventLog.get(this@DownloadService).record(io.github.ddmoyu.picomic.data.EventCode.DOWNLOAD_FAILED) }
                finally { stopSelf() } }
            return START_NOT_STICKY
        }
        scope.launch {
            try { repository.start(owner) } catch (e: CancellationException) { throw e } catch (_: Exception) { stopSelf(); return@launch }
            if (observer == null) observer = launch {
                launch { repository.tasks.collectLatest { tasks ->
                    val active = tasks.filter { it.state in DownloadRepository.ACTIVE }
                    getSystemService(NotificationManager::class.java).notify(ID, notice(this@DownloadService,
                        "${active.size} 个任务 · ${active.sumOf { it.completed }} / ${active.sumOf { it.total }} 页", active = true))
                } }
                repository.running.collect { if (!it) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() } }
            }
        }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android's dataSync time budget is finite; stop immediately and keep Room checkpoints.
        recovery.launch { runCatching { repository.stop("后台下载达到系统时限，请回到应用继续", owner) } }
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() {
        scope.cancel()
        recovery.launch { runCatching { repository.stop("后台服务已结束，请回到下载管理继续", owner) } }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        private const val CHANNEL = "comic-downloads"
        private const val ID = 4101
        private const val PAUSE = "io.github.ddmoyu.picomic.PAUSE_DOWNLOADS"
        private val recovery = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun start(context: Context) { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
        fun channels(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "漫画下载", NotificationManager.IMPORTANCE_LOW))
        }
        fun notice(context: Context, message: String, active: Boolean): Notification {
            val open = PendingIntent.getActivity(context, 4102, Intent(context, MainActivity::class.java).putExtra("openDownloads", true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            return NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(if (active) "PiComic 正在下载" else "PiComic 下载待继续").setContentText(message)
                .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(active).setAutoCancel(!active)
                .apply { if (active) addAction(0, "暂停全部", PendingIntent.getService(context, 4103, Intent(context, DownloadService::class.java).setAction(PAUSE), PendingIntent.FLAG_IMMUTABLE)) }.build()
        }
        fun scheduleMaintenance(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("download-maintenance", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DownloadMaintenance>(1, TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}

/** Recover persisted intent and notify; never start a dataSync FGS from boot/background. */
class DownloadMaintenance(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try { recover() } catch (e: CancellationException) { throw e } catch (_: Exception) { Result.retry() }
    }
    private suspend fun recover(): Result {
        val repo = DownloadRepository.get(applicationContext)
        repo.awaitReady()
        if (repo.running.value) return Result.success()
        val pending = DownloadDatabase.get(applicationContext).downloads().tasks().count { it.state in DownloadRepository.ACTIVE || it.state == DownloadState.WAITING_NETWORK.name }
        if (pending > 0) {
            DownloadService.channels(applicationContext)
            val notifications = applicationContext.getSystemService(NotificationManager::class.java)
            if (notifications.areNotificationsEnabled()) notifications.notify(4104, DownloadService.notice(applicationContext, "$pending 个任务待继续，点击进入下载管理", active = false))
        }
        return Result.success()
    }
}
