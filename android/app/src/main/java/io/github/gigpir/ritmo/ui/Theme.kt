package io.github.gigpir.ritmo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import io.github.gigpir.ritmo.core.Interval
import io.github.gigpir.ritmo.core.Step

/** The neutral colours of the dark interface, the same as on iPhone; the phase colours follow. */
object Theme {
    val background = Color(0xFF0E0E10)
    val surface = Color(0xFF17171A)
    val control = Color(0xFF1F1F23)
    val divider = Color(0xFF1C1C1F)
    val text = Color(0xFFF5F5F7)
    val secondary = Color(0xFFA1A1AA)
}

/** The phase colour: tide, digits and strip. */
val Step.Phase.bright: Color
    get() = when (this) {
        Step.Phase.PREPARE -> Color(0xFFFFC21A)
        Step.Phase.WORK -> Color(0xFFFF5A1F)
        Step.Phase.REST -> Color(0xFF38BDF8)
        Step.Phase.RECOVER -> Color(0xFFA78BFA)
    }

/** The dark tint left behind as the tide goes down. */
val Step.Phase.dark: Color
    get() = when (this) {
        Step.Phase.PREPARE -> Color(0xFF2B2106)
        Step.Phase.WORK -> Color(0xFF2B1208)
        Step.Phase.REST -> Color(0xFF0A2230)
        Step.Phase.RECOVER -> Color(0xFF1D1533)
    }

/** Text drawn on top of the bright colour. */
val Step.Phase.ink: Color
    get() = when (this) {
        Step.Phase.PREPARE -> Color(0xFF171103)
        Step.Phase.WORK -> Color(0xFF170A04)
        Step.Phase.REST -> Color(0xFF04131C)
        Step.Phase.RECOVER -> Color(0xFF0F0A1F)
    }

val Interval.Kind.color: Color
    get() = if (this == Interval.Kind.WORK) Step.Phase.WORK.bright else Step.Phase.REST.bright

/** Android's condensed sans serif, the nearest to the condensed fonts of the iPhone app. */
private val condensedFamily =
    FontFamily(Typeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)))

/** Phase names, titles and counters. Digits all have one width, so numbers don't jump. */
fun condensed(size: TextUnit, color: Color = Color.Unspecified, tracking: TextUnit = TextUnit.Unspecified) =
    TextStyle(
        fontFamily = condensedFamily,
        fontWeight = FontWeight.Bold,
        fontSize = size,
        color = color,
        letterSpacing = tracking,
        fontFeatureSettings = "tnum",
    )

/** The countdown, sized in dp: the phone's text size would only make it overflow. */
@Composable
fun digits(size: Dp, color: Color = Color.Unspecified): TextStyle = condensed(with(LocalDensity.current) { size.toSp() }, color)

@Composable
fun RitmoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Step.Phase.WORK.bright,
            onPrimary = Step.Phase.WORK.ink,
            secondary = Step.Phase.WORK.bright,
            background = Theme.background,
            onBackground = Theme.text,
            surface = Theme.surface,
            onSurface = Theme.text,
            surfaceVariant = Theme.control,
            onSurfaceVariant = Theme.secondary,
            surfaceContainer = Theme.control,
            surfaceContainerHigh = Theme.control,
            surfaceContainerHighest = Theme.control,
            outline = Theme.secondary,
            error = Color(0xFFFF453A),
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides Theme.text, content = content)
    }
}

/** The whole workout as a row of blocks, one per step, as wide as its duration. */
@Composable
fun StripView(steps: List<Step>, modifier: Modifier = Modifier, current: Int? = null, height: Dp = 8.dp, spacing: Dp = 2.dp) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val total = maxOf(1, steps.lastOrNull()?.end ?: 1)
        val gap = spacing.toPx()
        val free = maxOf(0f, size.width - gap * maxOf(0, steps.size - 1))
        val corner = CornerRadius(size.height / 4)
        val outline = 2.dp.toPx()
        var x = 0f
        steps.forEachIndexed { i, step ->
            val width = maxOf(1f, free * step.seconds / total)
            val alpha = if (current != null && i < current) 0.35f else 1f
            drawRoundRect(step.phase.bright, Offset(x, 0f), Size(width, size.height), corner, alpha = alpha)
            if (i == current) {
                drawRoundRect(
                    Theme.text,
                    Offset(x + outline / 2, outline / 2),
                    Size(width - outline, size.height - outline),
                    corner,
                    style = Stroke(outline),
                )
            }
            x += width + gap
        }
    }
}

/** A round icon button. */
@Composable
fun RoundIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    background: Color = Color.Black.copy(alpha = 0.6f),
    foreground: Color = Theme.text,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = foreground, modifier = Modifier.size(size * 0.5f))
    }
}

/** One line of text that shrinks to fit its width, down to [minScale] of its size. */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    minScale: Float = 0.3f,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = alignment) {
        val available = constraints.maxWidth
        val fitted = remember(text, style, available) {
            val width = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
            if (width <= available || width == 0) {
                style
            } else {
                val scale = maxOf(minScale, available.toFloat() / width)
                style.copy(
                    fontSize = style.fontSize * scale,
                    letterSpacing = if (style.letterSpacing.isSpecified) style.letterSpacing * scale else style.letterSpacing,
                )
            }
        }
        Text(text, style = fitted, maxLines = 1, softWrap = false)
    }
}
