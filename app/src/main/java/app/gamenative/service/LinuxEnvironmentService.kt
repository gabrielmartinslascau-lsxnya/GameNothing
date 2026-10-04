package app.gamenative.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.gamenative.R
import app.gamenative.linux.LinuxContainerManager
import app.gamenative.utils.downloader.LinuxRootfsDownloader
import com.winlator.core.ProcessHelper
import timber.log.Timber

/**
 * Foreground Service that owns and manages the PRoot Linux container process.
 * Handles pause/resume lifecycle by sending SIGSTOP and SIGCONT signals.
 */
class LinuxEnvironmentService : Service() {

    private val binder = LinuxBinder()

    @Volatile
    private var containerPid: Int = -1

    @Volatile
    private var isPaused: Boolean = false

    inner class LinuxBinder : Binder() {
        fun getService(): LinuxEnvironmentService = this@LinuxEnvironmentService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Timber.d("LinuxEnvironmentService received action: $action")

        when (action) {
            ACTION_START -> {
                startForegroundNotification("Linux Container Running")
                startLinuxContainer()
            }
            ACTION_PAUSE -> pauseContainer()
            ACTION_RESUME -> resumeContainer()
            ACTION_STOP -> stopContainer()
        }

        return START_STICKY
    }

    private fun startLinuxContainer() {
        if (containerPid != -1) {
            Timber.d("Linux container already running with PID $containerPid")
            return
        }

        val rootfsDir = LinuxRootfsDownloader.getRootfsDir(this)
        val guestCmd = "/bin/bash -c \"export DISPLAY=:0; export TERM=xterm-256color; if command -v xterm >/dev/null 2>&1; then exec xterm -geometry 120x40; elif command -v bash >/dev/null 2>&1; then exec /bin/bash; else exec /bin/sh; fi\""
        val (command, envp) = LinuxContainerManager.buildProotCommand(this, guestCmd)

        Thread {
            try {
                Timber.d("Launching Linux container via PRoot: $command")
                containerPid = ProcessHelper.exec(command, envp, rootfsDir) { exitCode ->
                    Timber.d("Linux container terminated with exit code $exitCode")
                    containerPid = -1
                    stopSelf()
                }
                Timber.d("Linux container process started with PID $containerPid")
            } catch (e: Exception) {
                Timber.e(e, "Failed to launch Linux container PRoot process")
                stopSelf()
            }
        }.start()
    }

    /**
     * Sends SIGSTOP to pause the PRoot process.
     */
    fun pauseContainer() {
        if (containerPid != -1 && !isPaused) {
            Timber.d("Pausing Linux container (PID $containerPid) with SIGSTOP")
            ProcessHelper.suspendProcess(containerPid)
            isPaused = true
            updateNotification("Linux Container Paused")
        }
    }

    /**
     * Sends SIGCONT to resume the PRoot process.
     */
    fun resumeContainer() {
        if (containerPid != -1 && isPaused) {
            Timber.d("Resuming Linux container (PID $containerPid) with SIGCONT")
            ProcessHelper.resumeProcess(containerPid)
            isPaused = false
            updateNotification("Linux Container Running")
        }
    }

    /**
     * Terminates the container process.
     */
    fun stopContainer() {
        if (containerPid != -1) {
            Timber.d("Stopping Linux container (PID $containerPid)")
            ProcessHelper.killProcess(containerPid)
            containerPid = -1
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopContainer()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Linux Environment Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundNotification(statusText: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Linux Container")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(statusText: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Linux Container")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "linux_environment_service"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "app.gamenative.service.LINUX_START"
        const val ACTION_PAUSE = "app.gamenative.service.LINUX_PAUSE"
        const val ACTION_RESUME = "app.gamenative.service.LINUX_RESUME"
        const val ACTION_STOP = "app.gamenative.service.LINUX_STOP"

        fun start(context: Context) {
            val intent = Intent(context, LinuxEnvironmentService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, LinuxEnvironmentService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, LinuxEnvironmentService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LinuxEnvironmentService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
