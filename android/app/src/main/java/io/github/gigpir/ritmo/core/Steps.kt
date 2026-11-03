package io.github.gigpir.ritmo.core

/** One step of a running workout. */
data class Step(
    val name: String,
    val phase: Phase,
    val seconds: Int,
    val round: Int,
    val cycle: Int,
    /** Seconds from the start of the workout. */
    val start: Int,
) {
    /** [RECOVER] is the rest between cycles. */
    enum class Phase { PREPARE, WORK, REST, RECOVER }

    val end: Int get() = start + seconds
}

/** The workout as a flat list of steps, with the last-Rest rule applied. */
val Workout.steps: List<Step>
    get() {
        val raw = mutableListOf<Step>()
        fun add(name: String, phase: Step.Phase, seconds: Int, round: Int, cycle: Int) {
            if (seconds > 0) raw += Step(name, phase, seconds, round, cycle, start = 0)
        }

        val roundCount = maxOf(1, rounds)
        val cycleCount = maxOf(1, cycles)
        add("Prepare", Step.Phase.PREPARE, prepare, round = 1, cycle = 1)
        for (cycle in 1..cycleCount) {
            for (round in 1..roundCount) {
                for (interval in intervals) {
                    val phase = if (interval.kind == Interval.Kind.WORK) Step.Phase.WORK else Step.Phase.REST
                    add(interval.name, phase, interval.seconds, round, cycle)
                }
            }
            if (cycle < cycleCount) {
                add("Recover", Step.Phase.RECOVER, cycleRest, round = roundCount, cycle = cycle)
            }
        }

        // A Rest right before a Recover or the end of the workout is dropped.
        val kept = mutableListOf<Step>()
        var boundaryAhead = true
        for (step in raw.asReversed()) {
            if (step.phase == Step.Phase.REST && boundaryAhead) continue
            kept += step
            boundaryAhead = step.phase == Step.Phase.RECOVER
        }

        var start = 0
        return kept.asReversed().map { step ->
            step.copy(start = start).also { start += step.seconds }
        }
    }

val Workout.duration: Int get() = steps.lastOrNull()?.end ?: 0
