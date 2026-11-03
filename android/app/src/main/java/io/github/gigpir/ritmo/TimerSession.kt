package io.github.gigpir.ritmo

import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.gigpir.ritmo.core.Cue
import io.github.gigpir.ritmo.core.TimerSnapshot
import io.github.gigpir.ritmo.core.Workout
import io.github.gigpir.ritmo.core.WorkoutClock
import io.github.gigpir.ritmo.core.steps

/**
 * One workout on screen: it opens ready, and from the first Play it runs the clock and the cues.
 * [TimerService] keeps it going with the screen off, and its notification shows the countdown.
 */
class TimerSession(val workout: Workout, private val app: RitmoApp) {
    val steps = workout.steps
    val clock = WorkoutClock(steps)

    var hasStarted by mutableStateOf(false)
        private set
    var isRunning by mutableStateOf(false)
        private set
    var isMuted by mutableStateOf(false)
        private set
    var isFinished by mutableStateOf(false)
        private set

    /** Counts the jumps of the clock, so the screen redraws even while paused. */
    var jumps by mutableIntStateOf(0)
        private set

    private val player = CuePlayer(app, app.settings)
    private val audio = app.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var last: TimerSnapshot? = null
    private var wasInCall = false
    private var isStopped = false

    /** The step and running state the notification shows. */
    private var notified: Pair<Int, Boolean>? = null

    // Cues are checked ten times a second; the screen redraws on its own schedule.
    private val ticker = object : Runnable {
        override fun run() {
            tick()
            if (!isFinished) handler.postDelayed(this, 100)
        }
    }

    /** Seconds on a clock that goes on while the phone sleeps and never jumps back. */
    fun now(): Double = SystemClock.elapsedRealtime() / 1000.0

    fun togglePause() {
        when {
            !hasStarted -> begin()
            clock.isRunning -> pause()
            else -> resume()
        }
    }

    fun pause() {
        clock.pause(now())
        changed()
    }

    fun next() {
        clock.next(now())
        tick()
        changed()
    }

    fun previous() {
        clock.previous(now())
        tick()
        changed()
    }

    fun toggleMute() {
        isMuted = !isMuted
        player.isMuted = isMuted
    }

    /** No more sounds, and no notification. */
    fun stop() {
        if (isStopped) return
        isStopped = true
        handler.removeCallbacksAndMessages(null)
        player.release()
        TimerService.stop(app)
    }

    private fun begin() {
        hasStarted = true
        TimerService.start(app)
        handler.post(ticker)
        resume()
    }

    private fun resume() {
        if (isFinished) return
        clock.resume(now())
        tick()
        changed()
    }

    private fun tick() {
        if (!hasStarted || steps.isEmpty() || isFinished) return
        pauseForCalls()
        val snapshot = clock.snapshot(now())
        for (cue in Cue.due(last, snapshot, steps)) player.play(cue)
        last = snapshot
        if (snapshot.finished) {
            isFinished = true
            isRunning = false
            // Let "Workout complete" finish, then give the music back and close the notification.
            handler.postDelayed(::stop, 4000)
        } else {
            updateNotification()
        }
    }

    /** A phone call pauses the workout, as on iPhone. */
    private fun pauseForCalls() {
        val inCall = audio.mode != AudioManager.MODE_NORMAL
        if (inCall && !wasInCall && clock.isRunning) pause()
        wasInCall = inCall
    }

    private fun changed() {
        isRunning = clock.isRunning && !isFinished
        jumps++
        updateNotification(force = true)
    }

    /**
     * The notification counts down by itself: it changes only when the step does,
     * or when the clock pauses or jumps.
     */
    private fun updateNotification(force: Boolean = false) {
        if (!hasStarted || isFinished || steps.isEmpty()) return
        val key = clock.stepIndex(now()) to clock.isRunning
        if (!force && key == notified) return
        notified = key
        TimerNotification.show(app, this)
    }
}
