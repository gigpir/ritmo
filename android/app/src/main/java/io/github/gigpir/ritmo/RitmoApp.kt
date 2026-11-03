package io.github.gigpir.ritmo

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** What lives as long as the app: the workouts, the settings and the workout on screen. */
class RitmoApp : Application() {
    lateinit var store: WorkoutStore
        private set
    lateinit var settings: Settings
        private set

    /** The workout open full screen, ready or running; null while the list is on screen. */
    var session: TimerSession? by mutableStateOf(null)

    override fun onCreate() {
        super.onCreate()
        store = WorkoutStore(File(filesDir, "workouts.json"))
        settings = Settings(this)
        TimerNotification.createChannel(this)
    }
}

val Context.ritmo: RitmoApp get() = applicationContext as RitmoApp
