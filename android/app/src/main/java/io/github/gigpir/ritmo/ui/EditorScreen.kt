package io.github.gigpir.ritmo.ui

import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.gigpir.ritmo.core.Interval
import io.github.gigpir.ritmo.core.Step
import io.github.gigpir.ritmo.core.Workout
import io.github.gigpir.ritmo.core.clockText
import io.github.gigpir.ritmo.core.duration
import io.github.gigpir.ritmo.core.steps

private const val PREPARE = "prepare"
private const val CYCLE_REST = "cycleRest"

/** One workout as a single form. */
@Composable
fun EditorScreen(start: Workout, isNew: Boolean, onSave: (Workout) -> Unit, onClose: () -> Unit) {
    var workout by remember { mutableStateOf(start) }
    // The duration whose wheels are open: one at a time, so the form stays short.
    var openWheels by remember { mutableStateOf<String?>(null) }
    fun toggle(id: String) {
        openWheels = if (openWheels == id) null else id
    }
    fun setIntervals(intervals: List<Interval>) {
        workout = workout.copy(intervals = intervals)
    }
    val canSave = workout.name.isNotBlank() && workout.steps.isNotEmpty()
    BackHandler(onBack = onClose)

    Column(Modifier.fillMaxSize().background(Theme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        TopBar(
            title = if (isNew) "New workout" else "Edit workout",
            leading = { TextButton(onClick = onClose) { Text("Cancel", fontSize = 17.sp) } },
            trailing = {
                TextButton(onClick = { onSave(workout); onClose() }, enabled = canSave) {
                    Text("Save", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            FormGroup { Preview(workout) }
            FormGroup { NameField(workout.name) { workout = workout.copy(name = it) } }
            FormGroup {
                DurationRow(
                    title = "Prepare",
                    color = Step.Phase.PREPARE.bright,
                    seconds = workout.prepare,
                    allowsNone = true,
                    isOpen = openWheels == PREPARE,
                    onToggle = { toggle(PREPARE) },
                    onChange = { workout = workout.copy(prepare = it) },
                )
            }
            FormGroup(
                header = "Intervals",
                footer = "Tap an interval to set its time. Hold it to rename it, switch between Work and Rest, move it or delete it.",
            ) {
                val intervals = workout.intervals
                intervals.forEachIndexed { index, interval ->
                    key(interval.id) {
                        IntervalRow(
                            interval = interval,
                            isOpen = openWheels == interval.id,
                            canMoveUp = index > 0,
                            canMoveDown = index < intervals.lastIndex,
                            onToggle = { toggle(interval.id) },
                            onChange = { changed -> setIntervals(intervals.map { if (it.id == changed.id) changed else it }) },
                            onMove = { by -> setIntervals(intervals.toMutableList().apply { add(index + by, removeAt(index)) }) },
                            onDelete = { setIntervals(intervals.filter { it.id != interval.id }) },
                        )
                    }
                    RowDivider()
                }
                FormRow(Modifier.clickable { setIntervals(intervals + nextInterval(intervals)) }) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Step.Phase.WORK.bright)
                    Spacer(Modifier.width(12.dp))
                    Text("Add interval", fontSize = 17.sp, color = Step.Phase.WORK.bright)
                }
                RowDivider()
                StepperRow("Rounds", workout.rounds, 1..99) { workout = workout.copy(rounds = it) }
            }
            FormGroup(
                header = "Cycles",
                footer = "A cycle is all the rounds above. The rest between cycles is announced as \"Recover\".",
            ) {
                StepperRow("Cycles", workout.cycles, 1..20) { workout = workout.copy(cycles = it) }
                RowDivider()
                DurationRow(
                    title = "Rest between cycles",
                    color = Step.Phase.RECOVER.bright,
                    seconds = workout.cycleRest,
                    allowsNone = true,
                    isOpen = openWheels == CYCLE_REST,
                    onToggle = { toggle(CYCLE_REST) },
                    enabled = workout.cycles > 1,
                    onChange = { workout = workout.copy(cycleRest = it) },
                )
            }
        }
    }
}

/** A Rest after a Work, a Work after a Rest. */
private fun nextInterval(intervals: List<Interval>): Interval {
    val kind = if (intervals.lastOrNull()?.kind == Interval.Kind.WORK) Interval.Kind.REST else Interval.Kind.WORK
    return Interval(name = kind.defaultName, kind = kind, seconds = if (kind == Interval.Kind.WORK) 20 else 10)
}

/** The total and the strip, updated while you edit. */
@Composable
private fun Preview(workout: Workout) {
    val steps = workout.steps
    val work = steps.filter { it.phase == Step.Phase.WORK }.sumOf { it.seconds }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row {
            Text(
                "TOTAL",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = Theme.secondary,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Text(clockText(workout.duration), style = condensed(34.sp), modifier = Modifier.alignByBaseline())
        }
        StripView(steps, height = 14.dp)
        Text("${steps.size} steps · ${clockText(work)} of work", fontSize = 13.sp, color = Theme.secondary)
    }
}

@Composable
private fun NameField(name: String, onChange: (String) -> Unit) {
    FormRow {
        BasicTextField(
            value = name,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 17.sp, color = Theme.text),
            cursorBrush = SolidColor(Step.Phase.WORK.bright),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { field ->
                Box {
                    if (name.isEmpty()) Text("Name", fontSize = 17.sp, color = Theme.secondary.copy(alpha = 0.6f))
                    field()
                }
            },
        )
    }
}

/** An interval edited in place: a tap opens its wheels, holding it offers the rest. */
@Composable
private fun IntervalRow(
    interval: Interval,
    isOpen: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onChange: (Interval) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    var showsMenu by remember { mutableStateOf(false) }
    var isRenaming by remember { mutableStateOf(false) }
    Box {
        DurationRow(
            title = interval.name.ifEmpty { interval.kind.defaultName },
            color = interval.kind.color,
            seconds = interval.seconds,
            allowsNone = false,
            isOpen = isOpen,
            onToggle = onToggle,
            onLongClick = { showsMenu = true },
            onChange = { onChange(interval.copy(seconds = it)) },
        )
        DropdownMenu(expanded = showsMenu, onDismissRequest = { showsMenu = false }) {
            MenuItem("Rename", Icons.Default.Edit) { showsMenu = false; isRenaming = true }
            MenuItem(if (interval.kind == Interval.Kind.WORK) "Make it Rest" else "Make it Work", RitmoIcons.SwapHoriz) {
                showsMenu = false
                onChange(switchKind(interval))
            }
            if (canMoveUp) MenuItem("Move up", RitmoIcons.ArrowUpward) { showsMenu = false; onMove(-1) }
            if (canMoveDown) MenuItem("Move down", RitmoIcons.ArrowDownward) { showsMenu = false; onMove(1) }
            MenuItem("Delete", Icons.Default.Delete, destructive = true) { showsMenu = false; onDelete() }
        }
    }
    if (isRenaming) {
        RenameDialog(interval, onDismiss = { isRenaming = false }) { onChange(interval.copy(name = it)) }
    }
}

/** A Work becomes a Rest and the other way round; a default name follows the kind. */
private fun switchKind(interval: Interval): Interval {
    val kind = if (interval.kind == Interval.Kind.WORK) Interval.Kind.REST else Interval.Kind.WORK
    val name = if (interval.name == interval.kind.defaultName) kind.defaultName else interval.name
    return interval.copy(kind = kind, name = name)
}

@Composable
private fun RenameDialog(interval: Interval, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(interval.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename interval") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text(interval.kind.defaultName) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(name.trim()); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A duration such as "0:20" that opens minute and second wheels when tapped. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DurationRow(
    title: String,
    color: Color,
    seconds: Int,
    allowsNone: Boolean,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onChange: (Int) -> Unit,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    Column(Modifier.alpha(if (enabled) 1f else 0.4f)) {
        FormRow(Modifier.combinedClickable(enabled = enabled, onLongClick = onLongClick, onClick = onToggle)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (seconds == 0) {
                Text("None", fontSize = 17.sp, color = Theme.secondary)
            } else {
                Text(clockText(seconds), style = condensed(20.sp, if (isOpen) color else Theme.text))
            }
        }
        if (isOpen && enabled) DurationWheels(seconds, allowsNone, onChange)
    }
}

/**
 * Minute and second wheels, like the Clock app's timer. They write only when turned,
 * and when they open the form scrolls the least it can to show them whole.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DurationWheels(seconds: Int, allowsNone: Boolean, onChange: (Int) -> Unit) {
    val latestOnChange by rememberUpdatedState(onChange)
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos {} // once laid out
        requester.bringIntoView()
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(160.dp).bringIntoViewRequester(requester),
        factory = { context ->
            fun wheel(count: Int, unit: String) = NumberPicker(context).apply {
                minValue = 0
                maxValue = count - 1
                displayedValues = Array(count) { "$it $unit" }
                wrapSelectorWheel = false
                // Turned only: a tap mustn't open the keyboard.
                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            }
            val minutes = wheel(100, "min")
            val secs = wheel(60, "s")
            val listener = NumberPicker.OnValueChangeListener { _, _, _ ->
                val total = minutes.value * 60 + secs.value
                latestOnChange(if (allowsNone) total else maxOf(1, total))
            }
            minutes.setOnValueChangedListener(listener)
            secs.setOnValueChangedListener(listener)
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(minutes, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
                addView(secs, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            }
        },
        update = { layout ->
            val minutes = layout.getChildAt(0) as NumberPicker
            val secs = layout.getChildAt(1) as NumberPicker
            if (minutes.value != seconds / 60) minutes.value = seconds / 60
            if (secs.value != seconds % 60) secs.value = seconds % 60
        },
    )
}

/** A count with − and + buttons, like the iPhone's stepper. */
@Composable
private fun StepperRow(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    FormRow {
        Text(title, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text("$value", style = condensed(22.sp))
        Spacer(Modifier.width(12.dp))
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).background(Theme.control),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton("−", "Fewer ${title.lowercase()}", enabled = value > range.first) { onChange(value - 1) }
            Box(Modifier.width(1.dp).height(18.dp).background(Theme.secondary.copy(alpha = 0.3f)))
            StepperButton("+", "More ${title.lowercase()}", enabled = value < range.last) { onChange(value + 1) }
        }
    }
}

@Composable
private fun StepperButton(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 46.dp, height = 32.dp).clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 20.sp, color = if (enabled) Theme.text else Theme.secondary.copy(alpha = 0.4f))
    }
}
