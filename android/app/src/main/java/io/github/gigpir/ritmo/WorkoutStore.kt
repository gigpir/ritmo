package io.github.gigpir.ritmo

import android.util.AtomicFile
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.gigpir.ritmo.core.Workout
import io.github.gigpir.ritmo.core.WorkoutFile
import io.github.gigpir.ritmo.core.toJson
import io.github.gigpir.ritmo.core.toWorkouts
import org.json.JSONArray
import java.io.File
import java.io.IOException

/** Every workout in list order, saved to one JSON file as on iPhone. */
class WorkoutStore(file: File) {
    private val file = AtomicFile(file)

    var workouts: List<Workout> by mutableStateOf(load())
        private set

    private fun load(): List<Workout> =
        try {
            JSONArray(file.readFully().decodeToString()).toWorkouts()
        } catch (e: Exception) {
            Workout.samples
        }

    /** Updates a workout, or adds it at the top if it is new. */
    fun save(workout: Workout) {
        workouts = if (workouts.any { it.id == workout.id }) {
            workouts.map { if (it.id == workout.id) workout else it }
        } else {
            listOf(workout) + workouts
        }
        persist()
    }

    fun duplicate(workout: Workout) {
        val copy = workout.withNewIdentity().copy(name = workout.name + " copy")
        val i = workouts.indexOfFirst { it.id == workout.id } + 1
        workouts = workouts.toMutableList().apply { add(i, copy) }
        persist()
    }

    /** Adds shared workouts at the top, skipping those already in the list; returns the ones added. */
    fun add(incoming: List<Workout>): List<Workout> {
        val merged = WorkoutFile.merge(incoming, workouts)
        if (merged.added.isNotEmpty()) {
            workouts = merged.list
            persist()
        }
        return merged.added
    }

    fun delete(workout: Workout) {
        workouts = workouts.filter { it.id != workout.id }
        persist()
    }

    /** The workout just started moves to the top of the list. */
    fun markUsed(workout: Workout) {
        val i = workouts.indexOfFirst { it.id == workout.id }
        if (i <= 0) return
        workouts = listOf(workouts[i]) + workouts.filterIndexed { index, _ -> index != i }
        persist()
    }

    private fun persist() {
        try {
            val out = file.startWrite()
            try {
                out.write(workouts.toJson().toString().toByteArray())
                file.finishWrite(out)
            } catch (e: IOException) {
                file.failWrite(out)
                throw e
            }
        } catch (e: IOException) {
            Log.e("Ritmo", "Could not save workouts", e)
        }
    }
}
