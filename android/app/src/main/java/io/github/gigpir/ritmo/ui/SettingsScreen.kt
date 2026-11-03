package io.github.gigpir.ritmo.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gigpir.ritmo.BuildConfig
import io.github.gigpir.ritmo.Donation
import io.github.gigpir.ritmo.Sharing
import io.github.gigpir.ritmo.ritmo

/** Three switches, all on by default, and the workouts to share or import. */
@Composable
fun SettingsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.ritmo
    val settings = app.settings
    var voice by remember { mutableStateOf(settings.voice) }
    var vibration by remember { mutableStateOf(settings.vibration) }
    var duckMusic by remember { mutableStateOf(settings.duckMusic) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importMessage = Sharing.import(context, uri)
    }
    BackHandler(onBack = onDone)

    Column(Modifier.fillMaxSize().background(Theme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        TopBar(
            title = "Settings",
            trailing = {
                TextButton(onClick = onDone) { Text("Done", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            FormGroup(footer = "Reads each phase aloud: Prepare, Work, Rest, Recover. When off, a long beep marks the change.") {
                SwitchRow("Voice", voice) {
                    voice = it
                    settings.voice = it
                }
            }
            FormGroup {
                SwitchRow("Vibration", vibration) {
                    vibration = it
                    settings.vibration = it
                }
            }
            FormGroup(footer = "Music from other apps gets quieter during beeps and announcements.") {
                SwitchRow("Lower music during cues", duckMusic) {
                    duckMusic = it
                    settings.duckMusic = it
                }
            }
            FormGroup(
                header = "Workouts",
                footer = "Workouts travel as .ritmo files, the same on Android and iPhone: share them, keep a copy in Files, " +
                    "and open one on a phone with Ritmo to add its workouts.",
            ) {
                ActionRow("Share all workouts", Icons.Default.Share) {
                    Sharing.share(context, app.store.workouts, "Ritmo workouts")
                }
                RowDivider()
                ActionRow("Import from Files", RitmoIcons.FileDownload) {
                    importer.launch(arrayOf("application/json", "application/octet-stream"))
                }
            }
            if (Donation.URL.isNotEmpty()) {
                FormGroup(footer = "Ritmo is free and open source, with no ads and no tracking.") {
                    ActionRow("Support Ritmo", Icons.Default.Favorite) { Sharing.open(context, Donation.URL) }
                }
            }
            Text(
                "Ritmo ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                fontSize = 13.sp,
                color = Theme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    importMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { importMessage = null },
            confirmButton = { TextButton(onClick = { importMessage = null }) { Text("OK") } },
            text = { Text(message) },
        )
    }
}
