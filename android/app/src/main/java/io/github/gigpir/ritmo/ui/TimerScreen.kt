package io.github.gigpir.ritmo.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.gigpir.ritmo.Donation
import io.github.gigpir.ritmo.Sharing
import io.github.gigpir.ritmo.TimerSession
import io.github.gigpir.ritmo.core.Step
import io.github.gigpir.ritmo.core.Workout
import io.github.gigpir.ritmo.core.clockText
import io.github.gigpir.ritmo.core.duration
import io.github.gigpir.ritmo.ritmo
import kotlinx.coroutines.delay
import kotlin.math.ceil

/**
 * The workout screen: Marea in portrait, Tabellone in landscape.
 * It opens ready on the first step; Play starts it.
 */
@Composable
fun TimerScreen(session: TimerSession, onEnd: () -> Unit) {
    val context = LocalContext.current
    val face = TimerFace(session, rememberNow(session))
    val isUnderway = session.hasStarted && !session.isFinished
    var showsHint by remember { mutableStateOf(false) }
    LaunchedEffect(showsHint) {
        if (showsHint) {
            delay(1500)
            showsHint = false
        }
    }
    // The first Play asks to show the countdown in a notification, then starts either way.
    val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        session.togglePause()
    }
    val controls = TimerControls(
        session = session,
        showsHint = showsHint,
        togglePause = {
            val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (!session.hasStarted && needsPermission) {
                askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                session.togglePause()
            }
        },
        // Once the workout has started the X has to be held, so a sweaty hand can't end it by mistake.
        tapEnd = { if (isUnderway) showsHint = true else onEnd() },
        holdEnd = onEnd,
    )
    BackHandler(onBack = controls.tapEnd)
    KeepScreenOn(isUnderway)
    HideSystemBars()

    BoxWithConstraints(Modifier.fillMaxSize().background(Theme.background)) {
        if (maxWidth > maxHeight) ScoreboardLayout(face, controls) else TideLayout(face, controls)
        if (session.isFinished) FinishedView(session.workout, onDone = onEnd)
    }
}

/** What the buttons of both layouts do. */
private class TimerControls(
    val session: TimerSession,
    val showsHint: Boolean,
    val togglePause: () -> Unit,
    val tapEnd: () -> Unit,
    val holdEnd: () -> Unit,
)

/** The time to draw: every frame while the clock runs, and after every jump. */
@Composable
private fun rememberNow(session: TimerSession): Double {
    var now by remember { mutableDoubleStateOf(session.now()) }
    val isRunning = session.isRunning
    LaunchedEffect(isRunning, session.jumps) {
        now = session.now()
        while (isRunning) withFrameMillis { now = session.now() }
    }
    return now
}

/** What the timer shows at one moment. */
private class TimerFace(session: TimerSession, now: Double) {
    val workout = session.workout
    val steps = session.steps
    val index = session.clock.stepIndex(now)
    val step = steps[index]
    val next = steps.getOrNull(index + 1)
    private val remaining = session.clock.remaining(now)

    /** Seconds left in the step, rounded up. */
    val shown = ceil(remaining).toInt()

    /** The part of the step still to go, from 1 down to 0: the height of the tide. */
    val level = (remaining / step.seconds).toFloat().coerceIn(0f, 1f)

    /** Seconds left in the whole workout. */
    val left = ceil(session.clock.duration - session.clock.elapsed(now)).toInt()
    val hasStarted = session.hasStarted
    val isRunning = session.isRunning
    val isMuted = session.isMuted

    /** Started, then paused: the countdown dims. */
    val isPaused get() = hasStarted && !isRunning

    /** In the last 3 seconds the countdown becomes a single digit. */
    val isFinalCountdown get() = shown in 1..3 && step.seconds > 3
    val digits get() = if (isFinalCountdown) "$shown" else clockText(shown)
    val title get() = step.name.uppercase()
}

// Portrait: Marea

@Composable
private fun TideLayout(face: TimerFace, controls: TimerControls) {
    val phase = face.step.phase
    val overlap = 28.dp
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(phase.dark))
                Box(Modifier.fillMaxWidth().fillMaxHeight(face.level).align(Alignment.BottomCenter).background(phase.bright))
                Countdown(face, phase.bright)
                // Where the tide crosses the digits, they change colour.
                Countdown(
                    face,
                    phase.ink,
                    Modifier.drawWithContent {
                        clipRect(top = size.height * (1 - face.level)) { this@drawWithContent.drawContent() }
                    },
                )
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EndButton(controls)
                        Box(Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "${face.workout.name} · ${clockText(face.left)} left",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                            )
                        }
                        MuteButton(face, controls)
                    }
                    Spacer(Modifier.weight(1f))
                    NextBanner(face, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 52.dp))
                }
            }
            ControlPanel(face, controls)
        },
    ) { measurables, constraints ->
        // The panel covers the bottom of the tide, under its rounded corners.
        val panel = measurables[1].measure(constraints.copy(minHeight = 0))
        val tideHeight = (constraints.maxHeight - panel.height + overlap.roundToPx()).coerceAtLeast(0)
        val tide = measurables[0].measure(Constraints.fixed(constraints.maxWidth, tideHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            tide.place(0, 0)
            panel.place(0, constraints.maxHeight - panel.height)
        }
    }
}

@Composable
private fun Countdown(face: TimerFace, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .alpha(if (face.isPaused) 0.55f else 1f)
            .padding(start = 16.dp, end = 16.dp, top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FitText(face.title, condensed(60.sp, color, tracking = 3.sp))
        FitText(face.digits, digits(if (face.isFinalCountdown) 320.dp else 240.dp, color))
    }
}

@Composable
private fun NextBanner(face: TimerFace, modifier: Modifier = Modifier) {
    val next = face.next ?: return
    if (face.isFinalCountdown) {
        val ink = next.phase.ink
        Row(
            modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(next.phase.bright)
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("NEXT", fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = ink)
            Spacer(Modifier.width(12.dp))
            Text(
                next.name.uppercase(),
                style = condensed(36.sp, ink, tracking = 1.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(clockText(next.seconds), style = condensed(36.sp, ink))
        }
    } else {
        Row(
            modifier
                .clip(CircleShape)
                .background(Theme.background.copy(alpha = 0.88f))
                .padding(horizontal = 22.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(next.phase.bright))
            Spacer(Modifier.width(12.dp))
            Text(
                "Next: ${next.name} ${clockText(next.seconds)}",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ControlPanel(face: TimerFace, controls: TimerControls) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Theme.background)
            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        StripView(face.steps, current = face.index, height = 14.dp, spacing = 3.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Counter("${face.step.round}/${face.workout.rounds}", "round")
            Spacer(Modifier.weight(1f))
            StepButtons(face, controls, small = 60.dp, large = 88.dp)
            Spacer(Modifier.weight(1f))
            Counter("${face.step.cycle}/${face.workout.cycles}", "cycle")
        }
    }
}

@Composable
private fun Counter(value: String, caption: String) {
    Column(Modifier.widthIn(min = 52.dp, max = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FitText(value, condensed(38.sp), minScale = 0.5f)
        Text(caption, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Theme.secondary)
    }
}

// Landscape: Tabellone

@Composable
private fun ScoreboardLayout(face: TimerFace, controls: TimerControls) {
    val phase = face.step.phase
    Row(Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start))
                .padding(horizontal = 32.dp),
        ) {
            // As tall as the screen allows, leaving room for the name and the bar.
            val digitsSize = min(300.dp, maxHeight * 0.56f)
            Column(Modifier.fillMaxSize().alpha(if (face.isPaused) 0.55f else 1f), verticalArrangement = Arrangement.Center) {
                FitText(face.title, condensed(44.sp, phase.bright, tracking = 4.sp), alignment = Alignment.CenterStart)
                FitText(face.digits, digits(digitsSize, phase.bright), alignment = Alignment.CenterStart)
                Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(10.dp).clip(CircleShape).background(phase.dark)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(face.level).clip(CircleShape).background(phase.bright))
                }
            }
        }
        Box(Modifier.width(2.dp).fillMaxHeight().background(Theme.divider))
        Column(
            Modifier
                .width(270.dp)
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                .padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            Row {
                EndButton(controls, background = Theme.divider)
                Spacer(Modifier.weight(1f))
                MuteButton(face, controls, background = Theme.divider)
            }
            Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
            Row {
                Stat("ROUND") { Fraction(face.step.round, face.workout.rounds) }
                Spacer(Modifier.weight(1f))
                Stat("CYCLE") { Fraction(face.step.cycle, face.workout.cycles) }
            }
            Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
            Stat("NEXT") {
                val next = face.next
                Text(
                    next?.let { "${it.name.uppercase()} ${clockText(it.seconds)}" } ?: "END",
                    style = condensed(32.sp, next?.phase?.bright ?: Theme.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
            Stat("TIME LEFT") { Text(clockText(face.left), style = condensed(32.sp)) }
            Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
            StepButtons(face, controls, small = 54.dp, large = 70.dp)
        }
    }
}

@Composable
private fun Stat(label: String, value: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, color = Theme.secondary)
        value()
    }
}

@Composable
private fun Fraction(value: Int, total: Int) {
    val text = buildAnnotatedString {
        append("$value")
        withStyle(SpanStyle(color = Theme.secondary)) { append("/$total") }
    }
    Text(text, style = condensed(52.sp))
}

// Controls shared by both layouts

@Composable
private fun StepButtons(face: TimerFace, controls: TimerControls, small: Dp, large: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIcon(
            RitmoIcons.SkipPrevious,
            "Previous step",
            size = small,
            background = Theme.control,
            onClick = controls.session::previous,
        )
        RoundIcon(
            if (face.isRunning) RitmoIcons.Pause else Icons.Default.PlayArrow,
            if (!face.hasStarted) "Start" else if (face.isRunning) "Pause" else "Resume",
            size = large,
            background = Theme.text,
            foreground = Theme.background,
            onClick = controls.togglePause,
        )
        RoundIcon(
            RitmoIcons.SkipNext,
            "Next step",
            size = small,
            background = Theme.control,
            onClick = controls.session::next,
        )
    }
}

/** The X closes the screen. Once the workout has started it has to be held. */
@Composable
private fun EndButton(controls: TimerControls, background: Color = Color.Black.copy(alpha = 0.6f)) {
    val tapEnd by rememberUpdatedState(controls.tapEnd)
    val holdEnd by rememberUpdatedState(controls.holdEnd)
    Box {
        RoundIcon(
            Icons.Default.Close,
            contentDescription = null,
            size = 52.dp,
            background = background,
            modifier = Modifier
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = if (controls.session.hasStarted) "End workout" else "Close"
                    // A TalkBack double tap is deliberate enough.
                    onClick {
                        holdEnd()
                        true
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        var released = false
                        var up: PointerInputChange? = null
                        withTimeoutOrNull(600) {
                            up = waitForUpOrCancellation()
                            released = true
                        }
                        if (!released) holdEnd() else if (up != null) tapEnd()
                    }
                },
        )
        if (controls.showsHint) {
            Text(
                "Hold to end",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Theme.background,
                modifier = Modifier
                    .below(60.dp)
                    .clip(CircleShape)
                    .background(Theme.text)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

/** Drawn [offset] below the top of its parent, without taking room in it. */
private fun Modifier.below(offset: Dp) = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(0, 0) { placeable.place(0, offset.roundToPx()) }
}

@Composable
private fun MuteButton(face: TimerFace, controls: TimerControls, background: Color = Color.Black.copy(alpha = 0.6f)) {
    RoundIcon(
        if (face.isMuted) RitmoIcons.VolumeOff else RitmoIcons.VolumeUp,
        if (face.isMuted) "Sound off" else "Sound on",
        size = 52.dp,
        background = background,
        onClick = controls.session::toggleMute,
    )
}

/** "Workout complete", and once a week at most, a request for a donation. */
@Composable
private fun FinishedView(workout: Workout, onDone: () -> Unit) {
    val context = LocalContext.current
    val settings = context.ritmo.settings
    // Decided once, when the workout ends.
    var asksForDonation by remember {
        mutableStateOf(settings.shouldAskForDonation().also { if (it) settings.didAskForDonation() })
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Theme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Step.Phase.WORK.bright, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text("Workout complete", style = condensed(44.sp))
        Spacer(Modifier.height(16.dp))
        Text("${workout.name} · ${clockText(workout.duration)}", fontSize = 22.sp, color = Theme.secondary)
        Spacer(Modifier.weight(1f))
        if (asksForDonation) {
            DonationCard(
                onDonate = { Sharing.open(context, Donation.URL) },
                onDonated = {
                    settings.hasDonated = true
                    asksForDonation = false
                },
                onLater = { asksForDonation = false },
            )
            Spacer(Modifier.height(16.dp))
        }
        Box(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Theme.text)
                .clickable(role = Role.Button, onClick = onDone),
            contentAlignment = Alignment.Center,
        ) {
            Text("Done", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Theme.background)
        }
    }
}

@Composable
private fun DonationCard(onDonate: () -> Unit, onDonated: () -> Unit, onLater: () -> Unit) {
    Column(
        Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Theme.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Enjoying Ritmo?", style = condensed(26.sp))
        Text(
            "Ritmo is free and open source, with no ads and no tracking. " +
                "If it helps your training, you can support it with a small donation.",
            fontSize = 16.sp,
            color = Theme.secondary,
        )
        Button(onClick = onDonate, modifier = Modifier.fillMaxWidth()) {
            Text("Donate", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Row {
            TextButton(onClick = onDonated) { Text("I already donated") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onLater) { Text("Not now") }
        }
    }
}

/** The screen stays on from the first Play to the end. */
@Composable
private fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

/** Like the iPhone app, the timer hides the status and navigation bars. */
@Composable
private fun HideSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
