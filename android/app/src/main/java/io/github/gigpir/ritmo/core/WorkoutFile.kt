package io.github.gigpir.ritmo.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * The `.ritmo` file for sharing workouts, the same as on iPhone: JSON with a format name
 * and a version, so a later Ritmo can still read the files written today.
 */
object WorkoutFile {
    fun encode(workouts: List<Workout>): String =
        JSONObject()
            .put("format", "ritmo")
            .put("version", 1)
            .put("workouts", workouts.toJson())
            .toString(2)
            // Android writes "60\/15"; both read "60/15", but a person reads it better.
            .replace("\\/", "/")

    /** Reads a `.ritmo` file, or plain JSON with a list of workouts or a single one. */
    fun decode(text: String): List<Workout> =
        when (val json = JSONTokener(text).nextValue()) {
            is JSONObject -> if (json.has("workouts")) json.getJSONArray("workouts").toWorkouts() else listOf(json.toWorkout())
            is JSONArray -> json.toWorkouts()
            else -> throw JSONException("No workouts")
        }

    /**
     * Adds the incoming workouts at the top of the list, under new ids,
     * skipping any that is already there. Also returns the ones added.
     */
    fun merge(incoming: List<Workout>, list: List<Workout>): Merged {
        val added = mutableListOf<Workout>()
        for (workout in incoming) {
            if (list.none { it.hasSameContent(workout) } && added.none { it.hasSameContent(workout) }) {
                added += workout.withNewIdentity()
            }
        }
        return Merged(list = added + list, added = added)
    }

    data class Merged(val list: List<Workout>, val added: List<Workout>)
}

/** The list as the app saves it: a JSON array of workouts, as on iPhone. */
fun List<Workout>.toJson(): JSONArray = JSONArray().also { array -> forEach { array.put(it.toJson()) } }

fun JSONArray.toWorkouts(): List<Workout> = (0 until length()).map { getJSONObject(it).toWorkout() }

/** The keys in alphabetical order, as the iPhone app writes them. */
fun Workout.toJson(): JSONObject =
    JSONObject()
        .put("cycleRest", cycleRest)
        .put("cycles", cycles)
        .put("id", id)
        .put("intervals", JSONArray().also { array -> intervals.forEach { array.put(it.toJson()) } })
        .put("name", name)
        .put("prepare", prepare)
        .put("rounds", rounds)

fun Interval.toJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("kind", kind.json)
        .put("name", name)
        .put("seconds", seconds)

/** Missing ids are made up, so a `.ritmo` file can be written by hand. */
fun JSONObject.toWorkout(): Workout {
    val list = getJSONArray("intervals")
    return Workout(
        id = optString("id").ifEmpty { newId() },
        name = getString("name"),
        prepare = optInt("prepare", 0),
        intervals = (0 until list.length()).map { list.getJSONObject(it).toInterval() },
        rounds = optInt("rounds", 1),
        // Until November 2026 cycles and cycleRest were called sets and recover: saved lists still have them.
        cycles = optInt("cycles", optInt("sets", 1)),
        cycleRest = optInt("cycleRest", optInt("recover", 0)),
    )
}

fun JSONObject.toInterval(): Interval {
    val json = getString("kind")
    val kind = Interval.Kind.entries.firstOrNull { it.json == json } ?: throw JSONException("Unknown kind: $json")
    return Interval(
        id = optString("id").ifEmpty { newId() },
        name = if (has("name")) getString("name") else kind.defaultName,
        kind = kind,
        seconds = getInt("seconds"),
    )
}
