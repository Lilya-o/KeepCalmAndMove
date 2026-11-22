package lauzhack.sbb.winners.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lauzhack.sbb.winners.data.HttpDisruptionSource
import lauzhack.sbb.winners.engine.ia.ConseillerVoyage
import lauzhack.sbb.winners.engine.ia.Recommandation
import lauzhack.sbb.winners.model.Disruption
import lauzhack.sbb.winners.model.Journey

/** Un trajet de démo, relié à un incident RÉEL du jeu de données serveur. */
data class TrajetDemo(
    val incidentId: String,
    val origin: String,
    val destination: String,
    val dureeMin: Long,
) {
    val label: String get() = "$origin → $destination"
}

/** État complet de l'écran d'assistance, calculé par le moteur à partir des données serveur. */
data class AssistUiState(
    val trajet: TrajetDemo = AssistViewModel.TRAJETS.first(),
    val chargement: Boolean = true,
    val erreur: String? = null,
    val timeline: List<Disruption> = emptyList(),
    val curseurMillis: Long? = null,
    val minMillis: Long? = null,
    val maxMillis: Long? = null,
    val disruptionCourante: Disruption? = null,
    val journey: Journey? = null,
    val reco: Recommandation? = null,
)

/**
 * Relie le serveur de replay (A) au moteur de décision (B) pour l'interface (C).
 *
 * 1. [charger] récupère la timeline de l'incident choisi une seule fois.
 * 2. [surCurseur] rejoue : pour un instant t, on prend le dernier snapshot connu (lastUpdateMillis <= t)
 *    et on rappelle `ConseillerVoyage.conseiller`. Pas de profil : le moteur utilise son profil neutre.
 *
 * Le scope vient de Compose (`rememberCoroutineScope`), donc les coroutines suivent le cycle de vie de l'écran.
 */
class AssistViewModel(
    private val scope: CoroutineScope,
    private val source: HttpDisruptionSource = HttpDisruptionSource(),
    private val conseiller: ConseillerVoyage = ConseillerVoyage(),
) {
    private val _state = MutableStateFlow(AssistUiState())
    val state: StateFlow<AssistUiState> = _state.asStateFlow()

    private var jobCalcul: Job? = null

    /** Charge la timeline du trajet choisi (incident réel associé). */
    fun charger(trajet: TrajetDemo = TRAJETS.first()) {
        _state.value = _state.value.copy(
            trajet = trajet, chargement = true, erreur = null,
            timeline = emptyList(), reco = null, curseurMillis = null,
            minMillis = null, maxMillis = null, disruptionCourante = null,
        )
        scope.launch {
            try {
                val timeline = source.timeline(trajet.incidentId)
                if (timeline.isEmpty()) error("Aucun snapshot pour cet incident.")
                val min = timeline.minOf { it.lastUpdateMillis }
                val max = timeline.maxOf { it.lastUpdateMillis }
                _state.value = _state.value.copy(
                    chargement = false, erreur = null,
                    timeline = timeline, minMillis = min, maxMillis = max,
                )
                surCurseur(max) // on démarre sur le dernier état connu
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    chargement = false,
                    erreur = e.message ?: "Serveur injoignable (lance ./gradlew :server:run).",
                )
            }
        }
    }

    /** Déplace le curseur de replay à l'instant [t] et recalcule le conseil. */
    fun surCurseur(t: Long) {
        val timeline = _state.value.timeline
        if (timeline.isEmpty()) return
        val snapshot = timeline.lastOrNull { it.lastUpdateMillis <= t } ?: timeline.first()
        val journey = trajetDemo(_state.value.trajet, t)
        _state.value = _state.value.copy(
            curseurMillis = t, disruptionCourante = snapshot, journey = journey,
        )
        jobCalcul?.cancel()
        jobCalcul = scope.launch {
            try {
                val reco = conseiller.conseiller(snapshot, journey)
                // On n'applique le résultat que si le curseur n'a pas bougé entre-temps.
                if (_state.value.curseurMillis == t) {
                    _state.value = _state.value.copy(reco = reco, erreur = null)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(erreur = e.message ?: "Erreur du moteur.")
            }
        }
    }

    companion object {
        private const val MINUTE_MILLIS = 60_000L

        /**
         * Les trajets de démo, chacun relié à un incident RÉEL du jeu de données.
         * On n'invente pas de trajet : seuls ces incidents existent dans les données.
         */
        val TRAJETS = listOf(
            TrajetDemo("2195005188970", "Lausanne", "Genève", dureeMin = 36),
            TrajetDemo("2195005182293", "Wädenswil", "Pfäffikon SZ", dureeMin = 12),
        )

        /** Trajet : départ = instant du curseur, arrivée prévue = départ + durée du trajet. */
        fun trajetDemo(trajet: TrajetDemo, departMillis: Long): Journey = Journey(
            origin = trajet.origin,
            destination = trajet.destination,
            departureMillis = departMillis,
            plannedArrivalMillis = departMillis + trajet.dureeMin * MINUTE_MILLIS,
        )
    }
}
