package lauzhack.sbb.winners

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KeepCalmAndMove",
        state = rememberWindowState(width = 1320.dp, height = 860.dp),
    ) {
        DesktopApp()
    }
}
