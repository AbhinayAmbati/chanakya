package com.chanakya.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's charts, drawn by hand.
 *
 * There is no charting library here on purpose: a donut and a bar chart are a few dozen
 * lines of drawing code, and a framework would be the heaviest dependency in a project
 * that otherwise has none. Everything is drawn in the app's own palette, so the charts
 * cannot drift from the rest of the UI.
 */

/** One slice of a donut chart. */
data class ChartSlice(
    val label: String,
    val value: Int,
    val color: Color,
)

/** One bar of a bar chart. */
data class ChartBar(
    val label: String,
    val value: Int,
)

/**
 * A donut with a headline figure in the middle and a legend beside it.
 *
 * @param slices the parts, drawn clockwise from the top in the order given.
 * @param centerValue the large figure in the hole.
 * @param centerCaption the small caption under it.
 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    centerValue: String,
    centerCaption: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 128.dp,
    ringThickness: Dp = 20.dp,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(diameter),
            contentAlignment = Alignment.Center,
        ) {
            val total = slices.sumOf { it.value }.coerceAtLeast(1)
            val ring = ringThickness

            Canvas(modifier = Modifier.fillMaxSize()) {
                val thickness = ring.toPx()
                val inset = thickness / 2f
                val arcSize = Size(size.width - thickness, size.height - thickness)
                // A small gap between wedges keeps neighbouring colours from blurring
                // together, and is skipped for a single slice so a full ring stays whole.
                val gap = if (slices.size > 1) 2f else 0f

                var start = -90f
                slices.forEach { slice ->
                    val sweep = slice.value.toFloat() / total * 360f
                    drawArc(
                        color = slice.color,
                        startAngle = start + gap / 2f,
                        sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = thickness, cap = StrokeCap.Butt),
                    )
                    start += sweep
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = centerValue,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = centerCaption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.width(20.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val total = slices.sumOf { it.value }.coerceAtLeast(1)
            slices.forEach { slice ->
                LegendRow(
                    slice = slice,
                    share = slice.value.toFloat() / total,
                )
            }
        }
    }
}

/** One legend line: a colour dot, the label, and the share. */
@Composable
private fun LegendRow(slice: ChartSlice, share: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(slice.color),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = slice.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(share * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A simple column chart with the day labels underneath.
 *
 * Bars are laid out with ordinary composables rather than drawn on a canvas, which keeps
 * the labels and the bars in the same layout pass and means they cannot drift apart.
 */
@Composable
fun BarChart(
    bars: List<ChartBar>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    chartHeight: Dp = 116.dp,
) {
    val peak = (bars.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)

    Column(modifier = modifier.fillMaxWidth()) {
        val shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEach { bar ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // A faint track, so a quiet day still reads as a column rather than
                    // a gap.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(TRACK_FRACTION)
                            .clip(shape)
                            .background(trackColor),
                    )
                    // The value itself, in the bar colour. This is the box that used to
                    // be missing: the bars were being drawn in the track colour, which on
                    // a white card is very nearly invisible.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight((bar.value.toFloat() / peak).coerceIn(0f, 1f))
                            .clip(shape)
                            .background(barColor),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            bars.forEach { bar ->
                Text(
                    text = bar.label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** How tall the faint track behind each bar is, as a fraction of the chart height. */
private const val TRACK_FRACTION = 0.04f
