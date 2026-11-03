package io.github.gigpir.ritmo.core

import java.util.Locale
import java.util.UUID

/** A new id, written in capitals as on iPhone, so ids look alike in shared files. */
fun newId(): String = UUID.randomUUID().toString().uppercase(Locale.ROOT)

/**
 * A saved workout: an optional Prepare, then the intervals repeated [rounds] times,
 * and that block repeated [cycles] times with an optional rest between cycles,
 * announced as "Recover".
 */
data class Workout(
    val id: String = newId(),
    val name: String,
    val prepare: Int = 10,
    val intervals: List<Interval>,
    val rounds: Int = 1,
    val cycles: Int = 1,
    /** Seconds of rest between one cycle and the next. */
    val cycleRest: Int = 0,
) {
    /** One line for the list, e.g. "8 rounds · 0:20 / 0:10" or "6 intervals · 3 cycles". */
    val summary: String
        get() {
            val parts = mutableListOf<String>()
            if (rounds > 1) parts += "$rounds rounds"
            parts += when {
                intervals.size == 2 && intervals[0].kind == Interval.Kind.WORK && intervals[1].kind == Interval.Kind.REST ->
                    "${clockText(intervals[0].seconds)} / ${clockText(intervals[1].seconds)}"
                intervals.size == 1 -> "1 interval"
                else -> "${intervals.size} intervals"
            }
            if (cycles > 1) parts += "$cycles cycles"
            return parts.joinToString(" · ")
        }

    /** The same workout under new ids, for a copy or an import. */
    fun withNewIdentity(): Workout = copy(id = newId(), intervals = intervals.map { it.copy(id = newId()) })

    /** Equal apart from the ids, so an import can skip what is already in the list. */
    fun hasSameContent(other: Workout): Boolean = content() == other.content()

    private fun content(): List<String> =
        listOf(name, "$prepare", "$rounds", "$cycles", "$cycleRest") +
            intervals.map { "${it.name}|${it.kind.json}|${it.seconds}" }

    companion object {
        /** What a new workout starts from: a classic Tabata without a name. */
        val blank: Workout
            get() = Workout(name = "", intervals = listOf(Interval.work(20), Interval.rest(10)), rounds = 8)

        /** The workouts found on first launch. */
        val samples: List<Workout> = listOf(
            Workout(name = "Tabata", intervals = listOf(Interval.work(20), Interval.rest(10)), rounds = 8),
            Workout(
                name = "HIIT 60/15",
                intervals = listOf(Interval.work(60), Interval.rest(15)),
                rounds = 6,
                cycles = 5,
                cycleRest = 45,
            ),
            Workout(name = "HIIT 40/20", intervals = listOf(Interval.work(40), Interval.rest(20)), rounds = 10),
            Workout(name = "Boxing", intervals = listOf(Interval.work(180), Interval.rest(60)), rounds = 12),
            Workout(
                name = "Full-body circuit",
                intervals = listOf(
                    Interval.work(40, "Squats"), Interval.rest(20),
                    Interval.work(40, "Push-ups"), Interval.rest(20),
                    Interval.work(40, "Plank"), Interval.rest(20),
                ),
                cycles = 3,
                cycleRest = 120,
            ),
        )
    }
}

data class Interval(
    val id: String = newId(),
    val name: String,
    val kind: Kind,
    val seconds: Int,
) {
    enum class Kind(val json: String, val defaultName: String) {
        WORK("work", "Work"),
        REST("rest", "Rest"),
    }

    companion object {
        fun work(seconds: Int, name: String = "Work") = Interval(name = name, kind = Kind.WORK, seconds = seconds)

        fun rest(seconds: Int) = Interval(name = "Rest", kind = Kind.REST, seconds = seconds)
    }
}

/** Times as the app shows them: "0:09", "12:00", "1:02:30". */
fun clockText(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds / 60 % 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%d:%02d", m, s)
    }
}
