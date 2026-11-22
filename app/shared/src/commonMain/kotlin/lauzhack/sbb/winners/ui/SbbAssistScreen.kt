package lauzhack.sbb.winners.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import lauzhack.sbb.winners.SampleData
import androidx.compose.ui.unit.dp
import keepcalmandmove.app.shared.generated.resources.*
import lauzhack.sbb.winners.model.Option
import lauzhack.sbb.winners.model.OptionType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private enum class Screen(val title: StringResource) {
    JOURNEY(Res.string.plan),
    SITUATION(Res.string.connection),
    OPTIONS(Res.string.options),
    DETAIL(Res.string.detail),
    OFFERS(Res.string.ticket_offers),
    TRIPS(Res.string.trips),
    EASY_RIDE(Res.string.easy_ride),
    TICKETS(Res.string.tickets),
    SHOP(Res.string.shop),
    PROFILE(Res.string.profile),
}

private val bottomTabs = listOf(
    Screen.JOURNEY, Screen.TRIPS, Screen.EASY_RIDE, Screen.TICKETS, Screen.SHOP, Screen.PROFILE,
)

// Base palette from the SBB Lyne color tokens.
private val HeaderRed = Color(0xFFEB0000)
private val LightRailColors = lightColorScheme(
    primary = HeaderRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFCE7E4),
    onPrimaryContainer = Color(0xFF47130F),
    background = Color(0xFFF6F6F6),
    surface = Color.White,
    onSurface = Color(0xFF212121),
    onSurfaceVariant = Color(0xFF5A5A5A),
    outlineVariant = Color(0xFFDCDCDC),
)
private val DarkRailColors = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF591812),
    primaryContainer = Color(0xFF542722),
    onPrimaryContainer = Color(0xFFFFDAD5),
    background = Color(0xFF101010),
    surface = Color(0xFF212121),
    onSurface = Color(0xFFF6F6F6),
    onSurfaceVariant = Color(0xFFCDCDCD),
    outlineVariant = Color(0xFF444444),
)

private data class DemoConnection(
    val line: String,
    val departureMinute: Long,
    val arrivalMinute: Long,
    val delayed: Boolean = false,
    val departurePlatform: Int = 1,
    val arrivalPlatform: Int = 2,
    val secondClassOccupancy: Occupancy = Occupancy.HIGH,
)

private enum class Occupancy(val label: StringResource) {
    LOW(Res.string.occupancy_low),
    HIGH(Res.string.occupancy_high),
    VERY_HIGH(Res.string.occupancy_very_high),
}

private const val DEMO_START_MINUTE = 9 * 60L
private val demoConnections = listOf(
    DemoConnection("RL1", 8 * 60L + 52, 9 * 60L + 17),
    DemoConnection(
        line = "RE33",
        departureMinute = DEMO_START_MINUTE + elapsedMinutes(SampleData.journey.departureMillis),
        arrivalMinute = DEMO_START_MINUTE + elapsedMinutes(SampleData.journey.plannedArrivalMillis),
        delayed = true,
        departurePlatform = 2,
        arrivalPlatform = 4,
        secondClassOccupancy = Occupancy.VERY_HIGH,
    ),
    DemoConnection("RL3", 9 * 60L + 25, 9 * 60L + 52),
    DemoConnection("RL4", 9 * 60L + 40, 10 * 60L + 5),
)

private sealed interface TravelChoice {
    val arrivalMinute: Long?

    data class Connection(val train: DemoConnection) : TravelChoice {
        override val arrivalMinute = train.arrivalMinute
    }

    data class Assistance(val option: Option) : TravelChoice {
        override val arrivalMinute = option.etaMillis?.let { DEMO_START_MINUTE + elapsedMinutes(it) }
    }
}

private val trainChoices = run {
    val updatedAt = DEMO_START_MINUTE + elapsedMinutes(SampleData.disruption.lastUpdateMillis)
    val connections = demoConnections
        .filter { !it.delayed && it.departureMinute >= updatedAt }
        .map(TravelChoice::Connection)
    val railOptions = SampleData.options
        .filter { it.type == OptionType.WAIT || it.type == OptionType.ALTERNATIVE_ROUTE }
        .map(TravelChoice::Assistance)
    (connections + railOptions).sortedBy { it.arrivalMinute ?: Long.MAX_VALUE }
}

private val otherChoices = SampleData.options
    .filterNot { it.type == OptionType.WAIT || it.type == OptionType.ALTERNATIVE_ROUTE }
    .map(TravelChoice::Assistance)
private val allChoices = trainChoices + otherChoices

private fun clockTime(minute: Long): String =
    (minute / 60).toString().padStart(2, '0') + ":" + (minute % 60).toString().padStart(2, '0')

@Composable
internal fun SbbAssistScreen() {
    var screen by rememberSaveable { mutableStateOf(Screen.JOURNEY) }
    var reversed by rememberSaveable { mutableStateOf(false) }
    var connectionIndex by rememberSaveable { mutableStateOf(0) }
    var optionIndex by rememberSaveable { mutableStateOf(0) }
    var darkOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var language by rememberSaveable { mutableStateOf<String?>(null) }
    var showInformation by rememberSaveable { mutableStateOf(false) }
    val dark = darkOverride ?: isSystemInDarkTheme()
    val languageCode = (language ?: Locale.current.language).takeIf { it in listOf("fr", "de") } ?: "en"

    CompositionLocalProvider(LocalLanguage provides languageCode) {
        val colors = if (dark) DarkRailColors else LightRailColors
        MaterialTheme(colorScheme = colors) {
            Surface(Modifier.fillMaxSize(), color = colors.outlineVariant) {
                Box(
                    Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        Modifier.widthIn(max = 400.dp).heightIn(max = 860.dp).fillMaxSize(),
                        color = colors.background,
                    ) {
                        Column {
                            PhoneHeader(
                                screen = screen,
                                dark = dark,
                                languageCode = languageCode,
                                onBack = if (screen in bottomTabs) null else {
                                    {
                                        screen = when (screen) {
                                            Screen.DETAIL -> Screen.OPTIONS
                                            Screen.OPTIONS, Screen.OFFERS -> Screen.SITUATION
                                            else -> Screen.JOURNEY
                                        }
                                    }
                                },
                                onToggleTheme = { darkOverride = !dark },
                                onLanguageChange = { language = it },
                            )
                            key(screen) {
                                Column(
                                    Modifier.weight(1f).fillMaxWidth()
                                        .verticalScroll(rememberScrollState()).padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    when (screen) {
                                        Screen.JOURNEY -> JourneyList(reversed, onSwap = { reversed = !reversed }) { index ->
                                            connectionIndex = index
                                            screen = Screen.SITUATION
                                        }
                                        Screen.SITUATION -> JourneyDetail(
                                            connection = demoConnections[connectionIndex],
                                            onDelay = { showInformation = true },
                                            onOffers = { screen = Screen.OFFERS },
                                        )
                                        Screen.OPTIONS -> Options { index ->
                                            optionIndex = index
                                            screen = Screen.DETAIL
                                        }
                                        Screen.DETAIL -> Panel { TravelChoiceDetail(allChoices[optionIndex]) }
                                        Screen.OFFERS -> TicketOffers(demoConnections[connectionIndex])
                                        Screen.TRIPS, Screen.EASY_RIDE, Screen.TICKETS, Screen.SHOP -> Unit
                                        Screen.PROFILE -> DemoProfile()
                                    }
                                }
                            }
                            AssistBottomNavigation(
                                labels = bottomTabs.map { textResource(it.title) },
                                selected = bottomTabs.indexOf(screen).coerceAtLeast(0),
                                onSelected = { screen = bottomTabs[it] },
                            )
                        }
                    }
                }
                if (showInformation) {
                    DelayDialog(
                        onClose = { showInformation = false },
                        onOptions = {
                            showInformation = false
                            screen = Screen.OPTIONS
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneHeader(
    screen: Screen,
    dark: Boolean,
    languageCode: String,
    onBack: (() -> Unit)?,
    onToggleTheme: () -> Unit,
    onLanguageChange: (String) -> Unit,
) {
    Surface(color = HeaderRed, contentColor = Color.White) {
        Box(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .heightIn(min = 54.dp).padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                textResource(screen.title),
                Modifier.fillMaxWidth().padding(horizontal = 80.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Row(
                Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    val backLabel = textResource(Res.string.back)
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = backLabel },
                    ) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                SbbMark(Modifier.padding(start = if (onBack == null) 10.dp else 0.dp))
            }
            Box(Modifier.align(Alignment.CenterEnd)) {
                HeaderControls(dark, languageCode, onToggleTheme, onLanguageChange)
            }
        }
    }
}

@Composable
private fun SbbMark(modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 25.dp, height = 14.dp)) {
        val middle = center.y
        val top = size.height * .14f
        val bottom = size.height * .86f
        val left = size.width * .08f
        val right = size.width * .92f
        val leftShoulder = size.width * .30f
        val rightShoulder = size.width * .70f
        val stroke = 1.7.dp.toPx()

        drawLine(Color.White, Offset(left, middle), Offset(right, middle), stroke)
        drawLine(Color.White, Offset(leftShoulder, top), Offset(left, middle), stroke)
        drawLine(Color.White, Offset(left, middle), Offset(leftShoulder, bottom), stroke)
        drawLine(Color.White, Offset(rightShoulder, top), Offset(right, middle), stroke)
        drawLine(Color.White, Offset(right, middle), Offset(rightShoulder, bottom), stroke)
        drawLine(Color.White, Offset(center.x, top), Offset(center.x, bottom), stroke)
    }
}

@Composable
private fun JourneyList(reversed: Boolean, onSwap: () -> Unit, onOpen: (Int) -> Unit) {
    val journey = SampleData.journey
    val origin = if (reversed) journey.destination else journey.origin
    val destination = if (reversed) journey.origin else journey.destination
    val ink = MaterialTheme.colorScheme.onSurface
    val border = MaterialTheme.colorScheme.outlineVariant
    Panel {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box {
                Canvas(Modifier.matchParentSize()) {
                    val start = Offset(center.x, 15.dp.toPx())
                    val end = Offset(center.x, size.height - 15.dp.toPx())
                    drawLine(ink, start, end, 1.2.dp.toPx())
                    for (point in listOf(start, end)) {
                        drawCircle(ink, 5.dp.toPx(), point, style = Stroke(1.2.dp.toPx()))
                        drawCircle(ink, 2.dp.toPx(), point)
                    }
                }
                Box(Modifier.size(width = 12.dp, height = 64.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(origin, style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                Text(destination, style = MaterialTheme.typography.titleMedium)
            }
            val swapLabel = textResource(Res.string.swap_stations)
            IconButton(
                onClick = onSwap,
                modifier = Modifier.border(1.dp, border, CircleShape)
                    .semantics { contentDescription = swapLabel },
            ) {
                Canvas(Modifier.size(22.dp)) {
                    val top = size.height * .21f
                    val bottom = size.height * .79f
                    val left = size.width * .31f
                    val right = size.width * .69f
                    val wing = size.width * .18f
                    val stroke = 1.4.dp.toPx()
                    drawLine(ink, Offset(left, bottom), Offset(left, top), stroke)
                    drawLine(ink, Offset(left - wing, top + wing), Offset(left, top), stroke)
                    drawLine(ink, Offset(left + wing, top + wing), Offset(left, top), stroke)
                    drawLine(ink, Offset(right, top), Offset(right, bottom), stroke)
                    drawLine(ink, Offset(right - wing, bottom - wing), Offset(right, bottom), stroke)
                    drawLine(ink, Offset(right + wing, bottom - wing), Offset(right, bottom), stroke)
                }
            }
        }
    }
    if (reversed) {
        Panel { Text(textResource(Res.string.no_reverse_connections)) }
        return
    }
    Text(
        textResource(Res.string.demo_timetable),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
    )
    demoConnections.forEachIndexed { index, connection ->
        Panel(onClick = { onOpen(index) }) {
            ConnectionHeading(connection)
            JourneyTimeline(connection)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    if (connection.delayed) DelayLabel()
                }
                Text(
                    textResource(Res.string.duration, connection.arrivalMinute - connection.departureMinute),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConnectionHeading(connection: DemoConnection) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            color = HeaderRed,
            contentColor = Color.White,
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(
                connection.line, Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Text(
            textResource(Res.string.towards, SampleData.journey.destination),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun JourneyDetail(connection: DemoConnection, onDelay: () -> Unit, onOffers: () -> Unit) {
    val journey = SampleData.journey
    val colors = MaterialTheme.colorScheme
    Panel {
        Text(
            textResource(Res.string.route, journey.origin, journey.destination),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        JourneyTimeline(connection)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                textResource(Res.string.duration, connection.arrivalMinute - connection.departureMinute),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            if (connection.delayed) DelayClock()
        }
    }
    Panel {
        StopsTimeline(connection)
        if (connection.delayed) {
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth()
                    .clickable(onClick = onDelay)
                    .heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f)) { DelayLabel() }
                val ink = MaterialTheme.colorScheme.onSurfaceVariant
                Canvas(Modifier.size(16.dp)) {
                    val start = Offset(size.width * .36f, size.height * .24f)
                    val tip = Offset(size.width * .64f, center.y)
                    val end = Offset(start.x, size.height * .76f)
                    val stroke = 1.5.dp.toPx()
                    drawLine(ink, start, tip, stroke)
                    drawLine(ink, tip, end, stroke)
                }
            }
        }
    }
    Panel {
        Text(textResource(Res.string.occupancy_legend), style = MaterialTheme.typography.titleMedium)
        Occupancy.entries.forEach { occupancy ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OccupancyIcon(occupancy)
                Text(textResource(occupancy.label), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Text(
        textResource(Res.string.demo_service_details),
        Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
    )
    Button(
        onClick = onOffers,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = HeaderRed, contentColor = Color.White),
    ) {
        Text(textResource(Res.string.show_ticket_offers))
    }
}

@Composable
private fun TicketOffers(connection: DemoConnection) {
    val journey = SampleData.journey
    Panel {
        Text(textResource(Res.string.route, journey.origin, journey.destination), style = MaterialTheme.typography.titleLarge)
        ConnectionHeading(connection)
        JourneyTimeline(connection)
        Text(textResource(Res.string.demo_offers), style = MaterialTheme.typography.bodySmall)
    }
    listOf(Res.string.second_class to "5.20", Res.string.first_class to "9.40").forEach { (label, fare) ->
        Panel {
            Text(textResource(label), style = MaterialTheme.typography.titleMedium)
            Text(textResource(Res.string.cost, fare))
        }
    }
}

@Composable
private fun OccupancyIcon(occupancy: Occupancy) {
    val colors = MaterialTheme.colorScheme
    val ink = if (occupancy == Occupancy.VERY_HIGH) colors.primary else colors.onSurface
    Canvas(Modifier.size(width = 28.dp, height = 16.dp)) {
        for (person in 0 until 3) {
            val x = size.width * (.18f + person * .32f)
            val color = if (person <= occupancy.ordinal) ink else colors.onSurfaceVariant.copy(alpha = .45f)
            drawCircle(color, 1.6.dp.toPx(), Offset(x, size.height * .19f))
            drawLine(color, Offset(x, size.height * .43f), Offset(x, size.height * .70f), 4.dp.toPx())
            for (offset in listOf(-1.dp.toPx(), 1.dp.toPx())) {
                drawLine(color, Offset(x + offset, size.height * .66f), Offset(x + offset, size.height * .93f), 1.4.dp.toPx())
            }
        }
    }
}

@Composable
private fun ClassOccupancy(label: StringResource, occupancy: Occupancy) {
    val description = textResource(occupancy.label)
    Row(
        modifier = Modifier.semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(textResource(label), style = MaterialTheme.typography.bodySmall)
        OccupancyIcon(occupancy)
    }
}

@Composable
private fun StopsTimeline(connection: DemoConnection) {
    val journey = SampleData.journey
    val ink = MaterialTheme.colorScheme.onSurface
    val railOffset = 56.dp
    Box(Modifier.fillMaxWidth()) {
        Canvas(Modifier.matchParentSize()) {
            val x = railOffset.toPx()
            val start = Offset(x, 12.dp.toPx())
            val end = Offset(x, size.height - 12.dp.toPx())
            drawLine(ink, start, end, 1.5.dp.toPx())
            drawCircle(ink, 3.5.dp.toPx(), start)
            drawCircle(ink, 3.5.dp.toPx(), end)
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StopTime(clockTime(connection.departureMinute), journey.origin, connection.departurePlatform)
            Column(Modifier.padding(start = 70.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ConnectionHeading(connection)
                ClassOccupancy(Res.string.first_class, Occupancy.LOW)
                ClassOccupancy(Res.string.second_class, connection.secondClassOccupancy)
            }
            StopTime(clockTime(connection.arrivalMinute), journey.destination, connection.arrivalPlatform)
        }
    }
}

@Composable
private fun StopTime(time: String, station: String, platform: Int) {
    Row {
        Text(
            time, Modifier.width(48.dp), fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            station,
            Modifier.weight(1f).padding(start = 22.dp, end = 8.dp),
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            textResource(Res.string.platform, platform),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun JourneyTimeline(connection: DemoConnection) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(clockTime(connection.departureMinute), fontWeight = FontWeight.Bold)
        Canvas(Modifier.weight(1f).size(height = 14.dp, width = 1.dp)) {
            if (size.width < 8.dp.toPx()) return@Canvas
            val start = Offset(4.dp.toPx(), center.y)
            val end = Offset(size.width - 4.dp.toPx(), center.y)
            drawLine(ink, start, end, 1.5.dp.toPx())
            drawCircle(ink, 3.dp.toPx(), start)
            if (connection.delayed) {
                drawCircle(HeaderRed, 4.dp.toPx(), Offset(size.width * .78f, center.y))
            }
            drawCircle(ink, 3.dp.toPx(), end)
        }
        Text(clockTime(connection.arrivalMinute), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DelayLabel() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DelayClock()
        Text(textResource(Res.string.delay), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DelayClock() {
    Canvas(Modifier.size(17.dp).background(HeaderRed, RoundedCornerShape(4.dp)).padding(2.dp)) {
        drawCircle(Color.White, size.minDimension * .39f, style = Stroke(1.dp.toPx()))
        val up = center.copy(y = center.y - 3.dp.toPx())
        val right = center.copy(x = center.x + 3.dp.toPx())
        drawLine(Color.White, up, center, 1.dp.toPx())
        drawLine(Color.White, center, right, 1.dp.toPx())
    }
}

@Composable
private fun TimelineCaption() {
    Text(
        textResource(Res.string.demo_timetable),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DelayDialog(onClose: () -> Unit, onOptions: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.widthIn(max = 400.dp).fillMaxWidth(.96f).fillMaxHeight(.92f),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column {
                Surface(color = HeaderRed, contentColor = Color.White) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(textResource(Res.string.information), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        val closeLabel = textResource(Res.string.close)
                        IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = closeLabel }) {
                            Text("×", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DelayInformation(onOptions)
                }
            }
        }
    }
}

@Composable
private fun DemoProfile() {
    Panel {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(Modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) { Text("CM", fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.weight(1f)) {
                Text("Camille Morel", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

private val LocalLanguage = staticCompositionLocalOf { "en" }

@Composable
private fun textResource(resource: StringResource, vararg args: Any): String {
    val language = LocalLanguage.current
    val localized = if (language == "en") resource else Res.allStringResources.getValue("${language}_${resource.key}")
    return if (args.isEmpty()) stringResource(localized) else stringResource(localized, *args)
}

@Composable
private fun HeaderControls(
    dark: Boolean,
    languageCode: String,
    onThemeChange: () -> Unit,
    onLanguageChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            IconButton(onClick = { expanded = true }, modifier = Modifier.width(38.dp)) {
                Text(languageCode.uppercase(), style = MaterialTheme.typography.labelSmall)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf("en" to "English", "fr" to "Français", "de" to "Deutsch").forEach { (code, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        onLanguageChange(code)
                        expanded = false
                    })
                }
            }
        }
        val themeLabel = textResource(if (dark) Res.string.theme_light else Res.string.theme_dark)
        IconButton(
            onClick = onThemeChange,
            modifier = Modifier.width(38.dp).semantics { contentDescription = themeLabel },
        ) {
            ThemeIcon(showSun = dark)
        }
    }
}

@Composable
private fun ThemeIcon(showSun: Boolean) {
    Canvas(Modifier.size(16.dp)) {
        val stroke = Stroke(1.4.dp.toPx())
        val radius = size.minDimension / 2
        if (showSun) {
            drawCircle(Color.White, radius * .40f, style = stroke)
            for (ray in 0 until 8) {
                val angle = ray * kotlin.math.PI / 4
                val direction = Offset(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat())
                drawLine(
                    Color.White,
                    center + direction * (radius * .68f),
                    center + direction * (radius * .96f),
                    stroke.width,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
        } else {
            val moon = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * .52f, size.height * .12f)
                cubicTo(size.width * .02f, size.height * .17f, size.width * .02f, size.height * .89f, size.width * .50f, size.height * .89f)
                cubicTo(size.width * .70f, size.height * .89f, size.width * .84f, size.height * .77f, size.width * .90f, size.height * .60f)
                cubicTo(size.width * .52f, size.height * .73f, size.width * .30f, size.height * .43f, size.width * .52f, size.height * .12f)
                close()
            }
            drawPath(moon, Color.White, style = stroke)
        }
    }
}

@Composable
private fun DelayInformation(onOptions: () -> Unit) {
    val disruption = SampleData.disruption
    val journey = SampleData.journey
    val waitOption = SampleData.options.firstOrNull { it.type == OptionType.WAIT }
    val waitArrival = waitOption?.etaMillis
    val colors = MaterialTheme.colorScheme
    Panel {
        Text(
            textResource(Res.string.route, journey.origin, journey.destination),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        ConnectionHeading(demoConnections.first { it.delayed })
        HorizontalDivider()
        if (waitArrival != null && waitArrival > journey.plannedArrivalMillis) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    textResource(Res.string.delay_if_waiting),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    "+${elapsedMinutes(waitArrival) - elapsedMinutes(journey.plannedArrivalMillis)} min",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            textResource(Res.string.updated_at_clock, clockTime(DEMO_START_MINUTE + elapsedMinutes(disruption.lastUpdateMillis))),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Text(textResource(Res.string.demo_cause), style = MaterialTheme.typography.bodyMedium)
    }
    Panel {
        Text(textResource(Res.string.information), style = MaterialTheme.typography.labelLarge)
        HorizontalDivider()
        Text(textResource(Res.string.status_may_change), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        trainChoices.firstOrNull { it.arrivalMinute != null }?.let { choice ->
            Text(textResource(Res.string.recommended), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(choiceTitle(choice))
            if (choice is TravelChoice.Connection) {
                Text(textResource(Res.string.earliest_train), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            ChoiceArrival(choice)
        }
        Button(
            onClick = onOptions,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = HeaderRed, contentColor = Color.White),
        ) {
            Text(textResource(Res.string.more_options))
        }
    }
    Panel {
        Text(textResource(Res.string.faq), style = MaterialTheme.typography.titleMedium)
        if (waitOption != null) {
            FaqEntry(textResource(Res.string.faq_wait), optionReason(waitOption), textResource(Res.string.sbb_timetable_url))
            HorizontalDivider()
        }
        FaqEntry(
            textResource(Res.string.faq_ticket),
            textResource(Res.string.faq_ticket_answer),
            textResource(Res.string.sbb_passenger_rights_url),
        )
        HorizontalDivider()
        FaqEntry(
            textResource(Res.string.faq_last_train),
            textResource(Res.string.faq_last_train_answer),
            textResource(Res.string.sbb_passenger_rights_url) + "#anchor3",
        )
    }
    Panel {
        Text(textResource(Res.string.latest_message), style = MaterialTheme.typography.titleMedium)
        if (disruption.messages.isNotEmpty()) Text(textResource(Res.string.demo_message))
        HorizontalDivider()
        Text(textResource(Res.string.lines, disruption.affectedLines.joinToString()))
        Text(textResource(Res.string.confidence, (disruption.confidence * 100).toInt()))
        Text(textResource(Res.string.history_missing), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
    TimelineCaption()
}

@Composable
private fun FaqEntry(title: String, answer: String, helpUrl: String) {
    val uriHandler = LocalUriHandler.current
    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    Text(answer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = { uriHandler.openUri(helpUrl) }) {
        Text(textResource(Res.string.visit_sbb))
    }
}

@Composable
private fun Panel(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Surface(Modifier.fillMaxWidth(), shape = shape) {
        Column(
            modifier = (if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
                .fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun Options(onSelect: (Int) -> Unit) {
    allChoices.forEachIndexed { index, choice ->
        if (index == trainChoices.size) {
            Text(textResource(Res.string.other_travel_options), style = MaterialTheme.typography.titleMedium)
        }
        val recommended = index == 0 && trainChoices.firstOrNull()?.arrivalMinute != null
        Card(
            onClick = { onSelect(index) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(
                containerColor = if (recommended) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                contentColor = if (recommended) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (recommended) Text(textResource(Res.string.recommended), style = MaterialTheme.typography.labelLarge)
                Text(choiceTitle(choice), style = MaterialTheme.typography.titleLarge)
                ChoiceArrival(choice)
                Text(textResource(Res.string.open_detail), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    Panel {
        FaqEntry(
            textResource(Res.string.hotel_option),
            textResource(Res.string.hotel_condition),
            textResource(Res.string.sbb_passenger_rights_url) + "#anchor3",
        )
    }
}

@Composable
private fun choiceTitle(choice: TravelChoice): String = when (choice) {
    is TravelChoice.Connection -> textResource(Res.string.train_to, choice.train.line, SampleData.journey.destination)
    is TravelChoice.Assistance -> optionTitle(choice.option)
}

@Composable
private fun ChoiceArrival(choice: TravelChoice) {
    val arrival = choice.arrivalMinute
    Text(
        if (arrival == null) textResource(Res.string.eta_unknown)
        else textResource(Res.string.estimated_arrival_clock, clockTime(arrival)),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun TravelChoiceDetail(choice: TravelChoice) {
    when (choice) {
        is TravelChoice.Connection -> {
            Text(choiceTitle(choice), style = MaterialTheme.typography.titleLarge)
            JourneyTimeline(choice.train)
            Text(textResource(Res.string.train_fare_included))
            Text(textResource(Res.string.demo_timetable), style = MaterialTheme.typography.bodySmall)
        }
        is TravelChoice.Assistance -> OptionDetail(choice.option)
    }
}

@Composable
private fun OptionDetail(option: Option) {
    Text(optionTitle(option), style = MaterialTheme.typography.titleLarge)
    Text(optionReason(option))
    val eta = option.etaMillis
    Text(if (eta == null) textResource(Res.string.eta_unknown)
        else textResource(Res.string.estimated_arrival_clock, clockTime(DEMO_START_MINUTE + elapsedMinutes(eta))))
    if (option.type == OptionType.TAXI) {
        Text(textResource(Res.string.taxi_separate_cost, option.costToTravellerChf.toString()))
    } else {
        Text(textResource(Res.string.cost, option.costToTravellerChf.toString()))
    }
    Text(textResource(if (option.reimbursable) Res.string.refund_check else Res.string.refund_none))
    Text(textResource(Res.string.steps_missing))
}

@Composable
private fun optionTitle(option: Option): String = when (option.type) {
    OptionType.WAIT -> textResource(Res.string.wait_title)
    OptionType.TAXI -> textResource(Res.string.taxi_title, SampleData.journey.destination)
    else -> option.title
}

@Composable
private fun optionReason(option: Option): String = when (option.type) {
    OptionType.WAIT -> SampleData.disruption.estimatedEndMillis?.let {
        textResource(Res.string.wait_reason, clockTime(DEMO_START_MINUTE + elapsedMinutes(it)))
    } ?: option.explanation
    OptionType.TAXI -> textResource(Res.string.taxi_reason)
    else -> option.explanation
}

private fun elapsedMinutes(timestampMillis: Long): Long =
    (timestampMillis - SampleData.disruption.startTimeMillis) / 60_000L
