package lauzhack.sbb.winners.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import lauzhack.sbb.winners.audio.BoutonEcouter
import androidx.compose.ui.unit.dp
import keepcalmandmove.app.shared.generated.resources.*
import lauzhack.sbb.winners.model.Option
import lauzhack.sbb.winners.model.OptionType
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch

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

// Horloge de démo calée sur la soirée de l'incident réel (17:52 -> 00:00), pour rester cohérent
// avec le curseur de replay. Le train de la voyageuse (RE33) est prévu vers 18:10.
private const val DEMO_START_MINUTE = 18 * 60L
private val demoConnections = listOf(
    DemoConnection("RL1", 18 * 60L + 20, 18 * 60L + 48),
    DemoConnection(
        line = "RE33",
        departureMinute = DEMO_START_MINUTE + elapsedMinutes(SampleData.journey.departureMillis),
        arrivalMinute = DEMO_START_MINUTE + elapsedMinutes(SampleData.journey.plannedArrivalMillis),
        delayed = true,
        departurePlatform = 2,
        arrivalPlatform = 4,
        secondClassOccupancy = Occupancy.VERY_HIGH,
    ),
    DemoConnection("RL3", 18 * 60L + 35, 19 * 60L + 2),
    DemoConnection("RL4", 18 * 60L + 50, 19 * 60L + 18),
)

/** Construit la liste de correspondances affichées à partir du trajet sélectionné. */
private fun connexionsDe(trajet: TrajetDemo): List<DemoConnection> =
    trajet.connexions.map {
        DemoConnection(
            line = it.line,
            departureMinute = it.depMin.toLong(),
            arrivalMinute = it.arrMin.toLong(),
            delayed = it.delayed,
            departurePlatform = if (it.delayed) 2 else 1,
            arrivalPlatform = if (it.delayed) 4 else 3,
            secondClassOccupancy = if (it.delayed) Occupancy.VERY_HIGH else Occupancy.HIGH,
        )
    }

/** Trajet courant + ses correspondances, fournis en haut de l'arbre et lus par les écrans. */
private val LocalTrajet = compositionLocalOf { AssistViewModel.TRAJETS.first() }
private val LocalConnexions = compositionLocalOf { connexionsDe(AssistViewModel.TRAJETS.first()) }

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

/** Heure murale suisse (UTC+2, heure d'été : correct pour les données de septembre 2026). */
private const val SUISSE_ETE_MILLIS = 2 * 60 * 60 * 1000L
private fun clockFromMillis(millis: Long): String {
    val minuteDuJour = (((millis + SUISSE_ETE_MILLIS) / 60_000L) % 1440L + 1440L) % 1440L
    return clockTime(minuteDuJour)
}

@Composable
internal fun SbbAssistScreen(desktop: Boolean = false) {
    var screen by rememberSaveable { mutableStateOf(Screen.JOURNEY) }
    var reversed by rememberSaveable { mutableStateOf(false) }
    var connectionIndex by rememberSaveable { mutableStateOf(0) }
    var optionIndex by rememberSaveable { mutableStateOf(0) }
    var darkOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var language by rememberSaveable { mutableStateOf<String?>(null) }
    var showInformation by rememberSaveable { mutableStateOf(false) }
    val dark = darkOverride ?: isSystemInDarkTheme()
    val languageCode = (language ?: Locale.current.language).takeIf { it in listOf("fr", "de") } ?: "en"

    // Pont serveur (A) -> moteur (B) : charge la timeline une fois, le curseur de replay la rejoue.
    val scope = rememberCoroutineScope()
    val viewModel = remember { AssistViewModel(scope) }
    val ui by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.charger() }

    // Bannière « urgente » : apparaît quand le TYPE de l'option recommandée change (ex. attendre -> bus).
    var banniere by remember { mutableStateOf<String?>(null) }
    val texteBanniere = remember { mutableStateOf("") }
    var typePrecedent by remember { mutableStateOf<OptionType?>(null) }
    val typeReco = ui.reco?.recommandee?.type
    LaunchedEffect(typeReco) {
        val avant = typePrecedent
        typePrecedent = typeReco
        if (typeReco != null && avant != null && typeReco != avant) {
            texteBanniere.value = ui.reco?.recommandee?.title ?: ""
            banniere = texteBanniere.value
            delay(4500)
            banniere = null
        }
    }

    CompositionLocalProvider(
        LocalLanguage provides languageCode,
        LocalTrajet provides ui.trajet,
        LocalConnexions provides connexionsDe(ui.trajet),
    ) {
        val colors = if (dark) DarkRailColors else LightRailColors
        MaterialTheme(colorScheme = colors) {
            Surface(Modifier.fillMaxSize(), color = if (desktop) colors.background else colors.outlineVariant) {
                val onBack: (() -> Unit)? = if (screen in bottomTabs) null else {
                    {
                        screen = when (screen) {
                            Screen.DETAIL -> Screen.OPTIONS
                            Screen.OPTIONS, Screen.OFFERS -> Screen.SITUATION
                            else -> Screen.JOURNEY
                        }
                    }
                }
                val body: @Composable () -> Unit = {
                    key(screen) {
                        when (screen) {
                            Screen.JOURNEY -> JourneyList(
                                reversed,
                                onSwap = { reversed = !reversed },
                                desktop = desktop,
                            ) { index ->
                                connectionIndex = index
                                screen = Screen.SITUATION
                            }
                            Screen.SITUATION -> JourneyDetail(
                                connection = LocalConnexions.current[connectionIndex.coerceIn(0, LocalConnexions.current.lastIndex)],
                                onDelay = { showInformation = true },
                                onOffers = { screen = Screen.OFFERS },
                            )
                            Screen.OPTIONS -> Options(ui) { index ->
                                optionIndex = index
                                screen = Screen.DETAIL
                            }
                            Screen.DETAIL -> Panel {
                                val reco = ui.reco
                                if (reco != null) {
                                    reco.options.getOrNull(optionIndex)?.let { EngineOptionDetail(it) }
                                } else {
                                    TravelChoiceDetail(allChoices[optionIndex])
                                }
                            }
                            Screen.OFFERS -> TicketOffers(LocalConnexions.current[connectionIndex.coerceIn(0, LocalConnexions.current.lastIndex)])
                            Screen.TRIPS, Screen.EASY_RIDE, Screen.TICKETS, Screen.SHOP -> Unit
                            Screen.PROFILE -> DemoProfile()
                        }
                    }
                }
                if (desktop) {
                    DesktopLayout(
                        screen = screen,
                        dark = dark,
                        languageCode = languageCode,
                        onBack = onBack,
                        onNavigate = { screen = it },
                        onToggleTheme = { darkOverride = !dark },
                        onLanguageChange = { language = it },
                        content = body,
                    )
                } else {
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
                                    onBack = onBack,
                                    onToggleTheme = { darkOverride = !dark },
                                    onLanguageChange = { language = it },
                                )
                                key(screen) {
                                    Column(
                                        Modifier.weight(1f).fillMaxWidth()
                                            .verticalScroll(rememberScrollState()).padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) { body() }
                                }
                                AssistBottomNavigation(
                                    labels = bottomTabs.map { textResource(it.title) },
                                    selected = bottomTabs.indexOf(screen).coerceAtLeast(0),
                                    onSelected = { screen = bottomTabs[it] },
                                )
                            }
                        }
                    }
                }
                if (showInformation) {
                    DelayDialog(
                        ui = ui,
                        desktop = desktop,
                        onCursor = viewModel::surCurseur,
                        onSelectTrajet = viewModel::charger,
                        onClose = { showInformation = false },
                        onOptions = {
                            showInformation = false
                            screen = Screen.OPTIONS
                        },
                    )
                } else if (desktop) {
                    Box(
                        Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        DemoReplayPanel(
                            ui = ui,
                            onCursor = viewModel::surCurseur,
                            onSelectTrajet = viewModel::charger,
                            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                        )
                    }
                }

                // Notification « urgente » quand la recommandation change.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    AnimatedVisibility(
                        visible = banniere != null,
                        enter = slideInVertically { -it } + fadeIn(),
                        exit = slideOutVertically { -it } + fadeOut(),
                    ) {
                        RecoChangeBanner(texteBanniere.value) { banniere = null }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopLayout(
    screen: Screen,
    dark: Boolean,
    languageCode: String,
    onBack: (() -> Unit)?,
    onNavigate: (Screen) -> Unit,
    onToggleTheme: () -> Unit,
    onLanguageChange: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val helpUrl = textResource(Res.string.sbb_passenger_rights_url)

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(
                drawerState = drawer,
                modifier = Modifier.widthIn(max = 360.dp),
                drawerContainerColor = Color(0xFF181818),
                drawerContentColor = Color.White,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val close = textResource(Res.string.close)
                    IconButton(
                        onClick = { scope.launch { drawer.close() } },
                        modifier = Modifier.align(Alignment.End).semantics { contentDescription = close },
                    ) { Text("×", style = MaterialTheme.typography.titleLarge) }
                    listOf(Screen.JOURNEY, Screen.OFFERS, Screen.PROFILE).forEach { destination ->
                        DesktopMenuItem(textResource(destination.title), selected = screen == destination) {
                            onNavigate(destination)
                            scope.launch { drawer.close() }
                        }
                    }
                    DesktopMenuItem(textResource(Res.string.help_and_contact)) {
                        scope.launch { drawer.close() }
                        uriHandler.openUri(helpUrl)
                    }
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = colors.surface, contentColor = colors.onSurface) {
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val compactHeader = maxWidth < 520.dp
                    Row(
                        Modifier.widthIn(max = 1200.dp).fillMaxWidth()
                            .heightIn(min = 72.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.weight(1f)) {
                            TextButton(onClick = { scope.launch { drawer.open() } }) {
                                Text("☰", Modifier.padding(end = 8.dp))
                                Text(textResource(Res.string.menu))
                            }
                        }
                        Surface(color = HeaderRed, shape = RoundedCornerShape(5.dp)) {
                            Box(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) { SbbMark() }
                        }
                        Row(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HeaderControls(
                                dark, languageCode, onToggleTheme, onLanguageChange,
                                compact = compactHeader,
                                themeIconColor = colors.onSurface,
                            )
                            if (!compactHeader) {
                                TextButton(onClick = { onNavigate(Screen.PROFILE) }) {
                                    Text(textResource(Res.string.profile))
                                }
                            }
                        }
                    }
                }
            }
            key(screen) {
                Box(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(Modifier.fillMaxWidth().height(if (screen == Screen.JOURNEY) 140.dp else 120.dp).background(HeaderRed))
                    Column(
                        Modifier.widthIn(max = 790.dp).fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 26.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (screen != Screen.JOURNEY) {
                            Panel {
                                if (onBack != null) {
                                    TextButton(onClick = onBack) { Text(textResource(Res.string.back)) }
                                }
                                Text(textResource(screen.title), style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopMenuItem(label: String, selected: Boolean = false, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, style = MaterialTheme.typography.titleMedium) },
        selected = selected,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = Color(0xFF343434),
            selectedTextColor = Color.White,
            unselectedTextColor = Color.White,
        ),
    )
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
private fun JourneyList(reversed: Boolean, onSwap: () -> Unit, desktop: Boolean, onOpen: (Int) -> Unit) {
    val trajet = LocalTrajet.current
    val origin = if (reversed) trajet.destination else trajet.origin
    val destination = if (reversed) trajet.origin else trajet.destination
    val ink = MaterialTheme.colorScheme.onSurface
    val border = MaterialTheme.colorScheme.outlineVariant
    if (!reversed) {
        // Carte d'intro « Votre voyage » : décrit le trajet sélectionné.
        Panel {
            Text(
                textResource(Res.string.your_trip).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                textResource(Res.string.route, origin, destination),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                trajet.tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
                Box(Modifier.size(width = 12.dp, height = if (desktop) 82.dp else 64.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StationField(origin, if (desktop) Res.string.station_from else null)
                HorizontalDivider()
                StationField(destination, if (desktop) Res.string.station_to else null)
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
    if (desktop) {
        Text(
            textResource(Res.string.select_connection),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
    }
    Text(
        textResource(Res.string.demo_timetable),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
    )
    LocalConnexions.current.forEachIndexed { index, connection ->
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
private fun StationField(station: String, label: StringResource?) {
    Column {
        if (label != null) {
            Text(
                textResource(label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(station, style = MaterialTheme.typography.titleMedium)
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
            textResource(Res.string.towards, LocalTrajet.current.destination),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun JourneyDetail(connection: DemoConnection, onDelay: () -> Unit, onOffers: () -> Unit) {
    val journey = LocalTrajet.current
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
            if (connection.delayed) DelayClock(announce = true)
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
    val journey = LocalTrajet.current
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
private fun OccupancyIcon(occupancy: Occupancy, announce: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val ink = if (occupancy == Occupancy.VERY_HIGH) colors.primary else colors.onSurface
    val description = textResource(occupancy.label)
    val iconModifier = Modifier.size(width = 28.dp, height = 16.dp)
    Canvas(if (announce) iconModifier.semantics { contentDescription = description } else iconModifier) {
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(textResource(label), style = MaterialTheme.typography.bodySmall)
        OccupancyIcon(occupancy, announce = true)
    }
}

@Composable
private fun StopsTimeline(connection: DemoConnection) {
    val journey = LocalTrajet.current
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
private fun DelayClock(announce: Boolean = false) {
    val description = textResource(Res.string.delay)
    val iconModifier = Modifier.size(17.dp).background(HeaderRed, RoundedCornerShape(4.dp)).padding(2.dp)
    Canvas(if (announce) iconModifier.semantics { contentDescription = description } else iconModifier) {
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
private fun DelayDialog(
    ui: AssistUiState,
    desktop: Boolean,
    onCursor: (Long) -> Unit,
    onSelectTrajet: (TrajetDemo) -> Unit,
    onClose: () -> Unit,
    onOptions: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            Modifier.widthIn(max = if (desktop) 1080.dp else 400.dp)
                .fillMaxWidth(.96f)
                .fillMaxHeight(.92f),
        ) {
            if (!desktop) {
                DelayDialogCard(ui, desktop, onClose, onOptions, Modifier.fillMaxSize())
            } else if (maxWidth >= 760.dp) {
                val replayHeight = maxHeight
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DelayDialogCard(ui, desktop, onClose, onOptions, Modifier.weight(1f).fillMaxHeight())
                    Box(Modifier.width(350.dp).heightIn(max = replayHeight).verticalScroll(rememberScrollState())) {
                        DemoReplayPanel(ui, onCursor, onSelectTrajet, Modifier.fillMaxWidth())
                    }
                }
            } else {
                val replayHeight = maxHeight * .40f
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DelayDialogCard(ui, desktop, onClose, onOptions, Modifier.weight(1f).fillMaxWidth())
                    Box(Modifier.fillMaxWidth().heightIn(max = replayHeight).verticalScroll(rememberScrollState())) {
                        DemoReplayPanel(ui, onCursor, onSelectTrajet, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun DelayDialogCard(
    ui: AssistUiState,
    desktop: Boolean,
    onClose: () -> Unit,
    onOptions: () -> Unit,
    modifier: Modifier,
) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.background) {
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
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(if (desktop) 20.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (desktop) 12.dp else 8.dp),
            ) {
                DelayInformation(ui, onOptions)
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
    compact: Boolean = true,
    themeIconColor: Color = Color.White,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            if (compact) {
                IconButton(onClick = { expanded = true }, modifier = Modifier.width(38.dp)) {
                    Text(languageCode.uppercase(), style = MaterialTheme.typography.labelSmall)
                }
            } else {
                TextButton(onClick = { expanded = true }) {
                    Text(when (languageCode) {
                        "de" -> "Deutsch"
                        "fr" -> "Français"
                        else -> "English"
                    })
                }
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
            ThemeIcon(showSun = dark, ink = themeIconColor)
        }
    }
}

@Composable
private fun ThemeIcon(showSun: Boolean, ink: Color) {
    Canvas(Modifier.size(16.dp)) {
        val stroke = Stroke(1.4.dp.toPx())
        val radius = size.minDimension / 2
        if (showSun) {
            drawCircle(ink, radius * .40f, style = stroke)
            for (ray in 0 until 8) {
                val angle = ray * kotlin.math.PI / 4
                val direction = Offset(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat())
                drawLine(
                    ink,
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
            drawPath(moon, ink, style = stroke)
        }
    }
}

@Composable
private fun DelayInformation(ui: AssistUiState, onOptions: () -> Unit) {
    val colors = MaterialTheme.colorScheme

    val reco = ui.reco
    val disruption = ui.disruptionCourante
    when {
        reco == null || disruption == null -> Panel {
            Text(
                textResource(
                    when {
                        ui.erreur != null -> Res.string.recommendation_unavailable
                        ui.chargement -> Res.string.recommendation_loading
                        else -> Res.string.status_may_change
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (ui.erreur != null) colors.primary else colors.onSurfaceVariant,
            )
        }
        else -> {
            Panel {
                val language = LocalLanguage.current
                val title = if (language == "fr") reco.titre else textResource(Res.string.travel_advice)
                val advice = if (language == "fr") reco.conseil else LocalizedTravelAdvice(ui)
                val voiceLanguage = when (language) {
                    "de" -> "de-DE"
                    "en" -> "en-US"
                    else -> "fr-FR"
                }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(advice, style = MaterialTheme.typography.bodyMedium)
                BoutonEcouter(title, advice, textResource(Res.string.listen_recommendation), voiceLanguage)
                HorizontalDivider()
                ui.journey?.let { journey ->
                    reco.retardSiAttenteMinutes(journey)?.let { retard ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                textResource(Res.string.delay_if_waiting),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                            Text(
                                "+$retard min",
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Text(
                    textResource(Res.string.train_tonight_probability, reco.chanceTrainCeSoirPourcent),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Recommandation + alternatives + indemnité.
            Panel {
                Text(textResource(Res.string.recommended), style = MaterialTheme.typography.labelLarge)
                reco.recommandee?.let { option ->
                    Text(engineOptionTitle(option), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(option.explanation, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    option.etaMillis?.let { arrival ->
                        Text(
                            textResource(Res.string.estimated_arrival_clock, clockFromMillis(arrival)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (reco.alternatives.isNotEmpty()) {
                    HorizontalDivider()
                    Text(textResource(Res.string.other_travel_options), style = MaterialTheme.typography.labelLarge)
                    reco.alternatives.forEach { option ->
                        Text("•  ${engineOptionTitle(option)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                reco.indemnite?.let { indemnite ->
                    HorizontalDivider()
                    Text(engineOptionTitle(indemnite), fontWeight = FontWeight.SemiBold)
                    Text(indemnite.explanation, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Button(
                    onClick = onOptions,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = HeaderRed, contentColor = Color.White),
                ) {
                    Text(textResource(Res.string.more_options))
                }
            }

            // Dernier message brut + fiabilité de l'information.
            Panel {
                Text(textResource(Res.string.latest_message), style = MaterialTheme.typography.titleMedium)
                disruption.messages.maxByOrNull { it.timestampMillis }?.let { Text(it.text) }
                HorizontalDivider()
                Text(textResource(Res.string.lines, disruption.affectedLines.joinToString()))
                Text(textResource(Res.string.confidence, (disruption.confidence * 100).toInt()))
            }
        }
    }
    TimelineCaption()
}

@Composable
private fun LocalizedTravelAdvice(ui: AssistUiState): String {
    val recommendation = ui.reco ?: return textResource(Res.string.recommendation_unavailable)
    val option = recommendation.recommandee
    val action = textResource(
        when (option?.type) {
            OptionType.ALTERNATIVE_ROUTE -> Res.string.advice_alternative
            OptionType.WAIT -> if (recommendation.faits.resolue) Res.string.advice_train_resumed else Res.string.advice_wait
            OptionType.TAXI -> Res.string.advice_taxi
            OptionType.HOTEL -> Res.string.advice_hotel
            OptionType.REFUND -> Res.string.advice_compensation
            null -> Res.string.advice_check_updates
        },
    )
    val arrival = option?.etaMillis ?: return action
    val now = ui.curseurMillis ?: ui.journey?.departureMillis
    val tomorrow = now != null &&
        (arrival + SUISSE_ETE_MILLIS).floorDiv(86_400_000L) ==
        (now + SUISSE_ETE_MILLIS).floorDiv(86_400_000L) + 1
    val arrivalText = textResource(
        if (tomorrow) Res.string.advice_arrival_tomorrow else Res.string.advice_arrival,
        clockFromMillis(arrival),
    )
    return "$action $arrivalText"
}

/**
 * Panneau de DÉMO, volontairement à l'écart de l'application (fond sombre, badge « MODE DÉMO »).
 * Ce n'est pas une fonctionnalité pour le voyageur : c'est l'outil du pitch pour rejouer l'incident
 * réel minute par minute et montrer comment la situation (et la reco du moteur) évoluent.
 * Affiché sur Desktop ; il pilote le même ViewModel que l'app.
 */
@Composable
private fun DemoReplayPanel(
    ui: AssistUiState,
    onCursor: (Long) -> Unit,
    onSelectTrajet: (TrajetDemo) -> Unit,
    modifier: Modifier,
) {
    val min = ui.minMillis
    val max = ui.maxMillis
    val cursor = ui.curseurMillis

    val panelColor = Color(0xFF1E1E1E)
    val borderColor = Color(0xFF3A3A3A)
    val mutedText = Color(0xFFBDBDBD)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = panelColor,
        contentColor = Color.White,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).background(HeaderRed, CircleShape))
                Text(
                    "MODE DÉMO",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = HeaderRed,
                )
            }
            // Pastilles : basculer entre les trajets (chacun = un incident RÉEL des données).
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistViewModel.TRAJETS.forEach { trajet ->
                    val selected = trajet.incidentId == ui.trajet.incidentId
                    Surface(
                        onClick = { if (!selected) onSelectTrajet(trajet) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) HeaderRed else Color.Transparent,
                        contentColor = Color.White,
                        border = if (selected) null else BorderStroke(1.dp, borderColor),
                    ) {
                        Text(
                            trajet.label,
                            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            Text(
                "Rejeu d'un incident réel (données SBB). Outil de présentation, pas une fonction de l'app.",
                style = MaterialTheme.typography.bodySmall,
                color = mutedText,
            )
            HorizontalDivider(color = borderColor)

            if (min == null || max == null || cursor == null || max <= min) {
                Text(
                    ui.erreur ?: "En attente des données du serveur…",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ui.erreur != null) Color(0xFFFF8A80) else mutedText,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        clockFromMillis(cursor),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    ui.reco?.titre?.let { titre ->
                        Text(
                            "→ $titre",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedText,
                        )
                    }
                }
                Slider(
                    value = cursor.toFloat(),
                    onValueChange = { onCursor(it.toLong()) },
                    valueRange = min.toFloat()..max.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = HeaderRed,
                        activeTrackColor = HeaderRed,
                        inactiveTrackColor = borderColor,
                    ),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(clockFromMillis(min), style = MaterialTheme.typography.labelSmall, color = mutedText)
                    Text(
                        "glissez pour rejouer",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedText,
                        textAlign = TextAlign.Center,
                    )
                    Text(clockFromMillis(max), style = MaterialTheme.typography.labelSmall, color = mutedText)
                }

                // Impact en direct : retard + options, recalculés par le moteur à chaque position.
                ui.reco?.let { reco ->
                    HorizontalDivider(color = borderColor)
                    ui.journey?.let { journey ->
                        reco.retardSiAttenteMinutes(journey)?.let { retard ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Si vous attendez",
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = mutedText,
                                )
                                Text(
                                    "+$retard min",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = HeaderRed,
                                )
                            }
                        }
                    }
                    Text(
                        "Chance qu'un train passe ce soir : ${reco.chanceTrainCeSoirPourcent} %",
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedText,
                    )
                    reco.recommandee?.let { option ->
                        Text(
                            "✓  ${option.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                    reco.alternatives.forEach { option ->
                        Text("•  ${option.title}", style = MaterialTheme.typography.bodySmall, color = mutedText)
                    }
                    reco.indemnite?.let { option ->
                        Text("💶  ${option.title}", style = MaterialTheme.typography.bodySmall, color = mutedText)
                    }
                }
            }
        }
    }
}

/** Bannière rouge type notification, qui glisse depuis le haut quand la reco change. */
@Composable
private fun RecoChangeBanner(titre: String, onDismiss: () -> Unit) {
    Surface(
        onClick = onDismiss,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(12.dp)
            .widthIn(max = 440.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = HeaderRed,
        contentColor = Color.White,
        shadowElevation = 10.dp,
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🔔", style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f)) {
                Text(
                    "La recommandation a changé",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(titre, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
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
private fun Options(ui: AssistUiState, onSelect: (Int) -> Unit) {
    val reco = ui.reco
    if (reco != null) {
        // Options réelles classées par le moteur (cohérentes avec le panneau et la fenêtre Informations).
        reco.options.forEachIndexed { index, option ->
            val recommended = index == 0
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
                    Text(engineOptionTitle(option), style = MaterialTheme.typography.titleLarge)
                    option.etaMillis?.let {
                        Text(
                            textResource(Res.string.estimated_arrival_clock, clockFromMillis(it)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (recommended) {
                        Text(option.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
        return
    }
    // Repli (serveur indisponible) : liste de démo de C.
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
private fun engineOptionTitle(option: Option): String =
    if (LocalLanguage.current == "fr") option.title else textResource(
        when (option.type) {
            OptionType.ALTERNATIVE_ROUTE -> Res.string.alternative_route
            OptionType.WAIT -> Res.string.wait_title
            OptionType.TAXI -> Res.string.taxi_option
            OptionType.HOTEL -> Res.string.hotel_option
            OptionType.REFUND -> Res.string.compensation_option
        },
    )

/** Détail d'une option produite par le moteur (heures réelles via clockFromMillis). */
@Composable
private fun EngineOptionDetail(option: Option) {
    Text(engineOptionTitle(option), style = MaterialTheme.typography.titleLarge)
    Text(option.explanation)
    val eta = option.etaMillis
    Text(
        if (eta == null) textResource(Res.string.eta_unknown)
        else textResource(Res.string.estimated_arrival_clock, clockFromMillis(eta)),
    )
    if (option.type == OptionType.TAXI) {
        Text(textResource(Res.string.taxi_separate_cost, option.costToTravellerChf.toString()))
    } else {
        Text(textResource(Res.string.cost, option.costToTravellerChf.toString()))
    }
    Text(textResource(if (option.reimbursable) Res.string.refund_check else Res.string.refund_none))
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
