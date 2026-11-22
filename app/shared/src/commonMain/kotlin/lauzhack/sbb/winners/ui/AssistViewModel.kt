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

/** État complet de l'écran d'assistance, calculé par le moteur à partir des données serveur. */
data class AssistUiState(
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
 * 1. [charger] récupère la timeline de l'incident une seule fois.
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

    /** Charge la timeline de l'incident principal (repli sur le premier incident de la liste). */
    fun charger(incidentId: String = INCIDENT_PRINCIPAL) {
        scope.launch {
            try {
                val timeline = source.timeline(incidentId).ifEmpty {
                    val premier = source.list().firstOrNull()
                        ?: error("Aucun incident disponible sur le serveur.")
                    source.timeline(premier.id)
                }
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
        val journey = trajetDemo(t)
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
        /** Incident principal de la démo : Renens VD – Morges (panne de courant du 17.09.2026). */
        const val INCIDENT_PRINCIPAL = "2195005188970"

        private const val MINUTE_MILLIS = 60_000L
        private const val DUREE_TRAJET_MIN = 36L

        /** Trajet de démo : Lausanne -> Genève, départ = instant du curseur, arrivée prévue = +36 min. */
        fun trajetDemo(departMillis: Long): Journey = Journey(
            origin = "Lausanne",
            destination = "Genève",
            departureMillis = departMillis,
            plannedArrivalMillis = departMillis + DUREE_TRAJET_MIN * MINUTE_MILLIS,
        )
    }
}
