package lauzhack.sbb.winners.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun AssistBottomNavigation(
    labels: List<String>,
    selected: Int,
    onSelected: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surface, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.widthIn(max = 650.dp).fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .selectableGroup()
                    .padding(horizontal = 4.dp),
            ) {
                labels.forEachIndexed { index, label ->
                    val active = selected == index
                    Column(
                        modifier = Modifier.weight(1f)
                            .heightIn(min = 68.dp)
                            .selectable(active, role = Role.Tab, onClick = { onSelected(index) })
                            .padding(top = 5.dp, bottom = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(38.dp)
                                .background(if (active) colors.onSurface else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            val iconModifier = Modifier.size(22.dp)
                            AssistNavIcon(
                                index,
                                if (active) colors.surface else colors.onSurface,
                                if (active) iconModifier else iconModifier.semantics { contentDescription = label },
                            )
                        }
                        if (active) {
                            Text(
                                label, style = MaterialTheme.typography.labelSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistNavIcon(index: Int, ink: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val width = 1.8.dp.toPx()
        fun segment(startX: Float, startY: Float, endX: Float, endY: Float) =
            drawLine(ink, Offset(startX * w, startY * h), Offset(endX * w, endY * h), width, StrokeCap.Round)
        fun outline(centerX: Float, centerY: Float, radius: Float) =
            drawCircle(ink, radius * w, Offset(centerX * w, centerY * h), style = Stroke(width))
        when (index) {
            0 -> {
                for (y in listOf(.18f, .34f, .50f, .66f)) segment(.1f, y, .45f, y)
                outline(.69f, .65f, .24f)
                segment(.69f, .65f, .69f, .50f)
                segment(.69f, .65f, .80f, .65f)
            }
            1 -> {
                drawRoundRect(
                    ink, Offset(.22f * w, .21f * h), Size(.56f * w, .52f * h),
                    CornerRadius(.1f * w), style = Stroke(width),
                )
                segment(.31f, .40f, .69f, .40f)
                outline(.37f, .65f, .035f)
                outline(.63f, .65f, .035f)
                segment(.36f, .74f, .28f, .86f)
                segment(.64f, .74f, .72f, .86f)
            }
            2 -> {
                drawRoundRect(
                    ink, Offset(.08f * w, .30f * h), Size(.84f * w, .40f * h),
                    CornerRadius(.20f * h), style = Stroke(width),
                )
                outline(.30f, .50f, .11f)
            }
            3 -> {
                for ((x, y) in listOf(.10f to .10f, .60f to .10f, .10f to .60f)) {
                    drawRect(ink, Offset(x * w, y * h), Size(.27f * w, .27f * h), style = Stroke(width))
                    drawCircle(ink, width / 2, Offset((x + .135f) * w, (y + .135f) * h))
                }
                segment(.61f, .62f, .61f, .87f)
                segment(.74f, .62f, .87f, .62f)
                segment(.75f, .78f, .87f, .78f)
            }
            4 -> {
                segment(.22f, .38f, .22f, .85f)
                segment(.78f, .38f, .78f, .85f)
                segment(.22f, .85f, .78f, .85f)
                segment(.12f, .38f, .24f, .18f)
                segment(.24f, .18f, .76f, .18f)
                segment(.76f, .18f, .88f, .38f)
                segment(.12f, .38f, .88f, .38f)
                segment(.42f, .85f, .42f, .57f)
                segment(.42f, .57f, .61f, .57f)
                segment(.61f, .57f, .61f, .85f)
            }
            5 -> {
                outline(.50f, .50f, .41f)
                outline(.50f, .40f, .15f)
                drawArc(
                    ink, 195f, 150f, false, Offset(.27f * w, .50f * h),
                    Size(.46f * w, .40f * h), style = Stroke(width),
                )
            }
        }
    }
}
