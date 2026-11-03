package io.github.gigpir.ritmo.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gigpir.ritmo.Sharing
import io.github.gigpir.ritmo.core.Step
import io.github.gigpir.ritmo.core.Workout
import io.github.gigpir.ritmo.core.clockText
import io.github.gigpir.ritmo.core.duration
import io.github.gigpir.ritmo.core.steps
import io.github.gigpir.ritmo.ritmo

/** The home screen: every saved workout, the last one used first. */
@Composable
fun WorkoutListScreen(
    onOpen: (Workout) -> Unit,
    onEdit: (Workout) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val store = context.ritmo.store
    Column(Modifier.fillMaxSize().background(Theme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
            Text("Workouts", fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onSettings) {
                Icon(RitmoIcons.Tune, contentDescription = "Settings", tint = Step.Phase.WORK.bright)
            }
            IconButton(onClick = onNew) {
                Icon(Icons.Default.Add, contentDescription = "New workout", tint = Step.Phase.WORK.bright)
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(store.workouts, key = { it.id }) { workout ->
                WorkoutCard(
                    workout,
                    onOpen = { onOpen(workout) },
                    onEdit = { onEdit(workout) },
                    onDuplicate = { store.duplicate(workout) },
                    onShare = { Sharing.share(context, listOf(workout), workout.name) },
                    onDelete = { store.delete(workout) },
                )
            }
            item {
                Text(
                    "Tap to open · hold for Edit, Duplicate, Share, Delete",
                    fontSize = 15.sp,
                    color = Theme.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkoutCard(
    workout: Workout,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var showsMenu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Theme.surface)
                .combinedClickable(onClickLabel = "Open the workout", onLongClick = { showsMenu = true }, onClick = onOpen)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row {
                Column(Modifier.weight(1f).alignByBaseline()) {
                    Text(workout.name, style = condensed(30.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(workout.summary, fontSize = 17.sp, color = Theme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                Text(clockText(workout.duration), style = condensed(34.sp), modifier = Modifier.alignByBaseline())
            }
            StripView(workout.steps, height = 10.dp)
        }
        DropdownMenu(expanded = showsMenu, onDismissRequest = { showsMenu = false }) {
            MenuItem("Edit", Icons.Default.Edit) { showsMenu = false; onEdit() }
            MenuItem("Duplicate", RitmoIcons.ContentCopy) { showsMenu = false; onDuplicate() }
            MenuItem("Share", Icons.Default.Share) { showsMenu = false; onShare() }
            MenuItem("Delete", Icons.Default.Delete, destructive = true) { showsMenu = false; onDelete() }
        }
    }
}

/** An item of a long-press menu; a destructive one is red. */
@Composable
fun MenuItem(title: String, icon: ImageVector, destructive: Boolean = false, onClick: () -> Unit) {
    val color = if (destructive) MaterialTheme.colorScheme.error else Theme.text
    DropdownMenuItem(
        text = { Text(title, fontSize = 17.sp, color = color) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color) },
        onClick = onClick,
    )
}
