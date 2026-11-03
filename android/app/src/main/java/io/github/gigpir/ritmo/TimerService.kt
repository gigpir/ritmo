package io.github.gigpir.ritmo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.compose.ui.graphics.toArgb
import io.github.gigpir.ritmo.core.clockText
import io.github.gigpir.ritmo.ui.bright
import kotlin.math.ceil

/**
 * Keeps Ritmo running while a workout runs with the screen off: a foreground service that keeps
 * the processor awake, with the countdown in its notification. The workout is in [TimerSession].
 */
class TimerService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires startForeground even if the workout ended in the meantime.
        val session = ritmo.session
        val notification = session?.let { TimerNotification.build(this, it) } ?: TimerNotification.idle(this)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(TimerNotification.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(TimerNotification.ID, notification)
        }
        if (session == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Ritmo:workout")
                .apply { acquire(4 * 60 * 60 * 1000L) }
        }
        return START_NOT_STICKY
    }

    /** Swiping Ritmo away from the recent apps ends the workout, as closing the app does on iPhone. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        ritmo.session?.stop()
        ritmo.session = null
        stopSelf()
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, TimerService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerService::class.java))
        }
    }
}

/**
 * The running workout on the lock screen and in the notifications: phase, countdown,
 * what comes next and the round, in the colour of the phase.
 */
object TimerNotification {
    const val ID = 1
    private const val CHANNEL = "workout"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Workout", NotificationManager.IMPORTANCE_LOW).apply {
            description = "The running workout, with its countdown"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(context: Context, session: TimerSession) {
        context.getSystemService(NotificationManager::class.java).notify(ID, build(context, session))
    }

    fun build(context: Context, session: TimerSession): Notification {
        val now = session.now()
        val clock = session.clock
        val index = clock.stepIndex(now)
        val step = session.steps[index]
        val next = session.steps.getOrNull(index + 1)
        val remaining = clock.remaining(now)
        var progress = "Round ${step.round}/${session.workout.rounds}"
        if (session.workout.cycles > 1) progress += " · Cycle ${step.cycle}/${session.workout.cycles}"

        val builder = base(context)
            .setContentTitle(step.name)
            .setContentText(next?.let { "Next: ${it.name} ${clockText(it.seconds)} · $progress" } ?: progress)
            .setSubText(session.workout.name)
            .setColor(step.phase.bright.toArgb())
            .setColorized(true)
        if (clock.isRunning) {
            // The chronometer shows whole seconds rounded down; 999 ms more and it rounds up, like the app.
            builder
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis() + (remaining * 1000).toLong() + 999)
        } else {
            builder
                .setShowWhen(false)
                .setContentTitle("${step.name} · paused at ${clockText(ceil(remaining).toInt())}")
        }
        return builder.build()
    }

    /** For the moment between a workout's end and the service's: Android wants a notification even then. */
    fun idle(context: Context): Notification = base(context).setContentTitle("Ritmo").build()

    private fun base(context: Context): Notification.Builder {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_WORKOUT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(open)
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder
    }
}
