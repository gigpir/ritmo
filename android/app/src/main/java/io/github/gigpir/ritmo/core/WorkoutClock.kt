package io.github.gigpir.ritmo.core

import kotlin.math.ceil

/**
 * Where a workout stands, computed from timestamps instead of counted ticks,
 * so it stays right after the screen locks or the app sleeps.
 * Times are seconds on any clock that never jumps back.
 */
class WorkoutClock(val steps: List<Step>) {
    val duration: Double = (steps.lastOrNull()?.end ?: 0).toDouble()

    /** Elapsed time at the last pause or jump. */
    private var elapsedBefore = 0.0

    /** When the clock last started running; null while paused. */
    private var runningSince: Double? = null

    val isRunning: Boolean get() = runningSince != null

    fun elapsed(now: Double): Double {
        val running = runningSince?.let { now - it } ?: 0.0
        return (elapsedBefore + running).coerceIn(0.0, duration)
    }

    fun isFinished(now: Double): Boolean = elapsed(now) >= duration

    /** The step running at [now]; the last one once the workout is over. */
    fun stepIndex(now: Double): Int {
        val elapsed = elapsed(now)
        return steps.indexOfLast { it.start <= elapsed }.coerceAtLeast(0)
    }

    /** Seconds left in the current step. */
    fun remaining(now: Double): Double =
        if (steps.isEmpty()) 0.0 else steps[stepIndex(now)].end - elapsed(now)

    fun snapshot(now: Double) = TimerSnapshot(
        step = stepIndex(now),
        shown = ceil(remaining(now)).toInt(),
        finished = isFinished(now),
    )

    fun pause(now: Double) {
        elapsedBefore = elapsed(now)
        runningSince = null
    }

    fun resume(now: Double) {
        if (runningSince != null || elapsedBefore >= duration) return
        runningSince = now
    }

    /** Jumps to the start of the next step, or to the end from the last one. */
    fun next(now: Double) {
        val i = stepIndex(now)
        jump(if (i + 1 < steps.size) steps[i + 1].start.toDouble() else duration, now)
    }

    /** Restarts the current step, or goes back one step during its first 2 seconds. */
    fun previous(now: Double) {
        if (steps.isEmpty()) return
        val i = stepIndex(now)
        val intoStep = elapsed(now) - steps[i].start
        val target = if (intoStep > 2 || i == 0) steps[i].start else steps[i - 1].start
        jump(target.toDouble(), now)
    }

    private fun jump(to: Double, now: Double) {
        elapsedBefore = to
        if (runningSince != null) runningSince = now
    }
}

/** What the screen shows at one moment, enough to decide which cues are due. */
data class TimerSnapshot(
    val step: Int,
    /** Seconds left in the step, rounded up as displayed. */
    val shown: Int,
    val finished: Boolean,
)

/** Sounds and announcements of the timer. */
sealed interface Cue {
    data object Beep : Cue

    data class Announce(val text: String) : Cue

    data object Finish : Cue

    companion object {
        /**
         * The cues due when the timer moves from [old] to [new]: the name of every step
         * that starts (or restarts), a beep at 3, 2 and 1 seconds left, and the end.
         */
        fun due(old: TimerSnapshot?, new: TimerSnapshot, steps: List<Step>): List<Cue> {
            if (new.finished) return if (old?.finished == true) emptyList() else listOf(Finish)
            if (old == null || old.step != new.step || new.shown > old.shown) {
                return listOf(Announce(steps[new.step].name))
            }
            if (new.shown < old.shown && new.shown in 1..3 && new.shown < steps[new.step].seconds) {
                return listOf(Beep)
            }
            return emptyList()
        }
    }
}
