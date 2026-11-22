package lauzhack.sbb.winners.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
internal fun BoutonEcouter(titre: String, conseil: String, libelle: String, langue: String) {
    val texte = listOf(titre.trim().trimEnd('.'), conseil.trim())
        .filter { it.isNotEmpty() }
        .joinToString(". ")

    OutlinedButton(onClick = { lire(texte, langue) }, enabled = texte.isNotBlank()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ink = LocalContentColor.current
            Canvas(Modifier.size(18.dp)) {
                val speaker = Path().apply {
                    moveTo(size.width * .10f, size.height * .36f)
                    lineTo(size.width * .28f, size.height * .36f)
                    lineTo(size.width * .49f, size.height * .16f)
                    lineTo(size.width * .49f, size.height * .84f)
                    lineTo(size.width * .28f, size.height * .64f)
                    lineTo(size.width * .10f, size.height * .64f)
                    close()
                }
                drawPath(speaker, ink)
                drawArc(
                    color = ink, startAngle = -52f, sweepAngle = 104f, useCenter = false,
                    topLeft = Offset(size.width * .40f, size.height * .20f),
                    size = Size(size.width * .52f, size.height * .60f),
                    style = Stroke(1.4.dp.toPx()),
                )
            }
            Text(libelle)
        }
    }
}
