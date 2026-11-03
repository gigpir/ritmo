package io.github.gigpir.ritmo

import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import io.github.gigpir.ritmo.core.Workout
import io.github.gigpir.ritmo.core.steps
import io.github.gigpir.ritmo.ui.EditorScreen
import io.github.gigpir.ritmo.ui.RitmoTheme
import io.github.gigpir.ritmo.ui.SettingsScreen
import io.github.gigpir.ritmo.ui.TimerScreen
import io.github.gigpir.ritmo.ui.WorkoutListScreen

class MainActivity : ComponentActivity() {
    /** What opening a .ritmo file did, shown in a dialog. */
    private var importMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // The volume buttons set the cues' volume, which is the music's.
        volumeControlStream = AudioManager.STREAM_MUSIC
        if (savedInstanceState == null) receive(intent)
        val firstEdit = if (BuildConfig.DEBUG && savedInstanceState == null) debugStart(intent) else null
        setContent {
            RitmoTheme {
                RitmoScreens(firstEdit)
                importMessage?.let { message ->
                    AlertDialog(
                        onDismissRequest = { importMessage = null },
                        confirmButton = { TextButton(onClick = { importMessage = null }) { Text("OK") } },
                        text = { Text(message) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }

    /** A .ritmo file opened with Ritmo, or shared to it. */
    private fun receive(intent: Intent) {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return
        importMessage = Sharing.import(this, uri)
    }

    /**
     * Screenshots from the terminal: `adb shell am start -S -n io.github.gigpir.ritmo/.MainActivity
     * --ei openWorkout 0 [--ez autostart true]`, or `--ei editWorkout 0`.
     */
    private fun debugStart(intent: Intent): Workout? {
        val workouts = ritmo.store.workouts
        val open = intent.getIntExtra("openWorkout", -1)
        if (open in workouts.indices) {
            ritmo.session = TimerSession(workouts[open], ritmo).also {
                if (intent.getBooleanExtra("autostart", false)) it.togglePause()
            }
        }
        return workouts.getOrNull(intent.getIntExtra("editWorkout", -1))
    }
}

/** A workout in the editor, and whether it's a new one. */
private class Editing(val workout: Workout, val isNew: Boolean)

/** One screen at a time: the list, the editor, Settings, or a workout full screen. */
@Composable
private fun RitmoScreens(firstEdit: Workout?) {
    val app = LocalContext.current.ritmo
    var editing by remember { mutableStateOf(firstEdit?.let { Editing(it, isNew = false) }) }
    var showsSettings by rememberSaveable { mutableStateOf(false) }
    val session = app.session
    val current = editing
    when {
        session != null -> key(session) {
            TimerScreen(session, onEnd = {
                session.stop()
                app.session = null
            })
        }
        current != null -> key(current) {
            EditorScreen(current.workout, current.isNew, onSave = app.store::save, onClose = { editing = null })
        }
        showsSettings -> SettingsScreen(onDone = { showsSettings = false })
        else -> WorkoutListScreen(
            onOpen = { workout ->
                if (workout.steps.isNotEmpty()) {
                    app.store.markUsed(workout)
                    app.session = TimerSession(workout, app)
                }
            },
            onEdit = { editing = Editing(it, isNew = false) },
            onNew = { editing = Editing(Workout.blank, isNew = true) },
            onSettings = { showsSettings = true },
        )
    }
}
