
package lauzhack.sbb.winners.engine

import lauzhack.sbb.winners.OptionEngine
import lauzhack.sbb.winners.engine.Rules.Responsabilite
import lauzhack.sbb.winners.engine.ia.ExtracteurParRegles
import lauzhack.sbb.winners.engine.ia.FaitsExtraits
import lauzhack.sbb.winners.engine.ia.Tendance
import lauzhack.sbb.winners.model.Disruption
import lauzhack.sbb.winners.model.Journey
import lauzhack.sbb.winners.model.Option
import lauzhack.sbb.winners.model.OptionType
import lauzhack.sbb.winners.model.Profile
import lauzhack.sbb.winners.model.Severity
import kotlin.math.exp
import kotlin.math.round

/**
 * Moteur de décision basé sur des règles (rôle B).
 *
 * 0. analyser        : comprend la situation (type de cause, qui est responsable,
 *                      chance qu'un train repasse encore ce soir) et rédige un conseil principal.
 * 1. generateOptions : fabrique les options RÉALISTES (pas d'itinéraire inventé).
 * 2. rank            : note chaque option selon le profil et les trie.
 *
 * @param distanceKm distance route entre deux gares, ou null si inconnue (→ pas de taxi proposé).
 * @param alternatives vrais itinéraires alternatifs (à brancher avec A : OJP ou GTFS).
 *        Par défaut : aucun. Le moteur n'invente jamais d'itinéraire.
 * @param decalageHoraireMillis décalage UTC → heure suisse (+2 h en septembre, heure d'été).
 * @param heureFinServiceMillis heure approximative du dernier train de la soirée (01:00 par défaut).
 * @param heurePremierTrainMillis heure approximative du premier train du matin (05:30 par défaut).
 *        Ces deux heures sont des ESTIMATIONS : à remplacer par l'horaire réel (données de A).
 */
class RuleBasedOptionEngine(
    private val distanceKm: (origin: String, destination: String) -> Double? = Distances::kmEntre,
    private val alternatives: (Disruption, Journey) -> List<Option> = { _, _ -> emptyList() },
    private val decalageHoraireMillis: Long = HEURE_ETE_SUISSE_MILLIS,
    private val heureFinServiceMillis: Long = 1 * HEURE_MILLIS,
    private val heurePremierTrainMillis: Long = 5 * HEURE_MILLIS + 30 * MINUTE_MILLIS,
) : OptionEngine {

    // =========================================================================
    // 0. ANALYSER LA SITUATION
    // =========================================================================

    /** Ce que le moteur a compris de la situation. C peut afficher [conseil] en bandeau. */
    data class Analyse(
        val categorie: CategorieCause,
        val responsabilite: Responsabilite,
        /** Estimation du moteur (pas une info SBB) : chance qu'un train circule encore ce soir. */
        val chanceRepriseCeSoir: Double,
        val niveauChance: NiveauChance,
        val finDuServiceMillis: Long,
        val premierTrainMillis: Long,
        val busDeRemplacementAnnonce: Boolean,
        /** Le conseil principal, en une phrase ou deux. */
        val conseil: String,
    ) {
        val repriseImprobable: Boolean get() = chanceRepriseCeSoir < SEUIL_REPRISE_IMPROBABLE
    }

    enum class CategorieCause { INFRASTRUCTURE_ENDOMMAGEE, ACCIDENT_DE_PERSONNE, PANNE_TECHNIQUE, INCONNUE }
    enum class NiveauChance { TRES_FAIBLE, FAIBLE, MOYENNE, ELEVEE }

    /**
     * @param faits ce que l'on a compris des messages. Par défaut : les règles seules.
     *        [lauzhack.sbb.winners.engine.ia.ConseillerVoyage] fournit des faits complétés par l'IA.
     */
    fun analyser(
        disruption: Disruption,
        journey: Journey,
        faits: FaitsExtraits = ExtracteurParRegles.extraire(disruption),
    ): Analyse {
        val maintenant = maintenant(disruption, journey)
        val categorie = faits.categorie
        val responsabilite = faits.responsabilite
        val busAnnonce = faits.busDeRemplacement

        // --- Heures de fin de service ce soir et de premier train demain ---
        val debutJour = debutJourLocal(maintenant)
        val heureLocale = (maintenant + decalageHoraireMillis).mod(JOUR_MILLIS)
        val finService = if (heureLocale < 5 * HEURE_MILLIS) debutJour + heureFinServiceMillis
        else debutJour + JOUR_MILLIS + heureFinServiceMillis
        val premierTrain = if (heureLocale < heurePremierTrainMillis) debutJour + heurePremierTrainMillis
        else debutJour + JOUR_MILLIS + heurePremierTrainMillis

        // --- Chance qu'un train repasse avant la fin du service ---
        val heuresRestantes = (finService - maintenant).toDouble() / HEURE_MILLIS
        val chanceSelonLaCause = if (heuresRestantes <= 0.0) 0.0
        else 1.0 - exp(-heuresRestantes / dureeTypiqueHeures(categorie, disruption.severity))

        val fin = disruption.estimatedEndMillis
        val confiance = confianceEffective(disruption, faits)
        val chanceBrute = when {
            fin == null -> chanceSelonLaCause                  // fin inconnue : on estime selon la cause
            fin <= finService -> confiance                     // reprise annoncée avant la fin du service
            else -> (1.0 - confiance) * chanceSelonLaCause     // reprise annoncée après la fin du service
        }
        // Les messages disent que ça s'aggrave ou que ça s'améliore : on ajuste un peu.
        val chance = if (faits.resolue) 1.0 else when (faits.tendance) {
            Tendance.AGGRAVATION -> chanceBrute * 0.7
            Tendance.AMELIORATION -> chanceBrute * 1.2
            else -> chanceBrute
        }.coerceIn(0.0, 1.0)

        val niveau = when {
            chance < 0.2 -> NiveauChance.TRES_FAIBLE
            chance < SEUIL_REPRISE_IMPROBABLE -> NiveauChance.FAIBLE
            chance < 0.7 -> NiveauChance.MOYENNE
            else -> NiveauChance.ELEVEE
        }

        val conseil = buildString {
            if (faits.resolue) {
                append("Le trafic a repris normalement. Quelques retards restent possibles : vérifie ton train dans l'horaire en ligne.")
            } else if (chance < SEUIL_REPRISE_IMPROBABLE) {
                append("Trafic interrompu (${disruption.cause}). ")
                val bloqueJusquAuMatin = fin == null || fin > finService
                if (bloqueJusquAuMatin) {
                    append("Il est peu probable qu'un train circule encore avant le matin (chance estimée : ${pct(chance)}). ")
                    if (faits.reportsDeLaFin > 0) append("L'heure de reprise a déjà été repoussée ${faits.reportsDeLaFin} fois. ")
                } else {
                    append("Reprise annoncée vers ${heure(fin!!)}, mais ")
                    if (faits.reportsDeLaFin > 0) append("cette heure a déjà été repoussée ${faits.reportsDeLaFin} fois : ")
                    append("ne compte pas trop dessus. ")
                }
                if (busAnnonce) append("Des bus de remplacement sont annoncés : renseigne-toi sur le quai ou au point d'information. ")
                else append("Va voir le personnel CFF et demande s'il y a un bus de remplacement. ")
                if (bloqueJusquAuMatin) when (responsabilite) {
                    Responsabilite.PAS_LA_COMPAGNIE ->
                        append("La cause est météorologique ou naturelle : un taxi ou un hôtel resteraient à tes frais. ")
                    else ->
                        append("S'il n'y a plus aucune solution avant le matin, demande-leur un bon pour un taxi ou un hôtel AVANT de payer quoi que ce soit. ")
                }
                append("Garde ton billet et toutes tes quittances.")
            } else if (fin != null) {
                append("Reprise annoncée vers ${heure(fin)}. ")
                if (faits.reportsDeLaFin > 0) {
                    val fois = if (faits.reportsDeLaFin == 1) "une fois" else "${faits.reportsDeLaFin} fois"
                    append("Attention : cette heure a déjà été repoussée $fois, garde une marge.")
                } else {
                    append("Information encore susceptible de changer : vérifie les mises à jour.")
                }
            } else {
                append("La fin de la perturbation n'est pas encore connue. ")
                append("Une reprise avant la fin du service reste possible (chance estimée : ${pct(chance)}).")
            }
        }

        return Analyse(categorie, responsabilite, chance, niveau, finService, premierTrain, busAnnonce, conseil)
    }

    // =========================================================================
    // 1. GÉNÉRER LES OPTIONS
    // =========================================================================

    override fun generateOptions(disruption: Disruption, journey: Journey): List<Option> =
        generateOptions(disruption, journey, ExtracteurParRegles.extraire(disruption))

    fun generateOptions(disruption: Disruption, journey: Journey, faits: FaitsExtraits): List<Option> {
        val a = analyser(disruption, journey, faits)
        val options = mutableListOf<Option>()

        // Perturbation terminée : une seule option, prendre son train normalement.
        if (faits.resolue) {
            val maintenantResolu = maintenant(disruption, journey)
            val arrivee = maxOf(
                journey.plannedArrivalMillis,
                maintenantResolu + (journey.plannedArrivalMillis - journey.departureMillis),
            )
            return listOf(
                Option(
                    type = OptionType.WAIT,
                    title = "Prendre ton train normalement",
                    etaMillis = arrivee,
                    costToTravellerChf = 0.0,
                    reimbursable = false,
                    effort = EFFORT_ATTENDRE,
                    reliability = FIABILITE_TRAFIC_REPRIS,
                    explanation = "Le trafic a repris. Prends ton train comme prévu (arrivée vers ${heure(arrivee)}). Quelques retards restent possibles.",
                ),
            )
        }

        val maintenant = maintenant(disruption, journey)
        val dureeTrajet = journey.plannedArrivalMillis - journey.departureMillis
        val finConnue = disruption.estimatedEndMillis

        // ---- Quand arriverait-on en ATTENDANT ? ---------------------------------
        val arriveeAttente = when {
            a.repriseImprobable && (finConnue == null || finConnue > a.finDuServiceMillis) ->
                maxOf(finConnue ?: 0L, a.premierTrainMillis) + dureeTrajet // demain matin
            finConnue != null -> maxOf(finConnue, journey.departureMillis) + dureeTrajet
            else -> maxOf(maintenant + ATTENTE_SI_FIN_INCONNUE_MILLIS, journey.departureMillis) + dureeTrajet
        }
        val retardAttenteMin = enMinutes(arriveeAttente - journey.plannedArrivalMillis)

        // Le voyageur est-il BLOQUÉ jusqu'au premier train du matin ?
        //  - CERTAIN : la reprise est annoncée (fin connue) et elle tombe après le dernier train de la
        //    soirée, ou fait arriver le lendemain → la « dernière correspondance » est perdue.
        //  - PROBABLE : la fin est inconnue mais la chance d'un train avant le matin est faible.
        // Dans les deux cas, on propose l'hôtel (quelle que soit l'heure, même à 2 h du matin).
        val bloqueCertain = finConnue != null && (
                finConnue > a.finDuServiceMillis ||
                        jour(arriveeAttente) > jour(journey.plannedArrivalMillis)
                )
        val nuitProbable = a.repriseImprobable && finConnue == null
        val bloqueProbable = bloqueCertain || nuitProbable

        // ---- Option ATTENDRE -----------------------------------------------------
        options += if (a.repriseImprobable && (finConnue == null || finConnue > a.finDuServiceMillis)) {
            Option(
                type = OptionType.WAIT,
                title = "Attendre à la gare (pas de train probable avant le matin)",
                etaMillis = arriveeAttente,
                costToTravellerChf = 0.0,
                reimbursable = Rules.aDroitAIndemnite(retardAttenteMin),
                effort = EFFORT_NUIT_EN_GARE,
                reliability = FIABILITE_NUIT_EN_GARE,
                explanation = "Il est peu probable qu'un train circule encore avant le matin (chance estimée : ${pct(a.chanceRepriseCeSoir)}). " +
                        "En attendant à la gare, tu partirais au mieux avec le premier train du matin et arriverais vers ${heure(arriveeAttente)}.",
            )
        } else {
            Option(
                type = OptionType.WAIT,
                title = if (a.repriseImprobable) "Attendre la reprise annoncée (heure peu fiable)" else "Attendre la reprise du trafic",
                etaMillis = arriveeAttente,
                costToTravellerChf = 0.0,
                reimbursable = Rules.aDroitAIndemnite(retardAttenteMin),
                effort = EFFORT_ATTENDRE,
                reliability = if (finConnue == null) FIABILITE_FIN_INCONNUE else confianceEffective(disruption, faits),
                explanation = buildString {
                    append("Arrivée estimée à ${heure(arriveeAttente)}, soit environ $retardAttenteMin min de retard.")
                    if (finConnue == null) append(" La fin de la perturbation n'est pas encore connue : cette estimation est incertaine.")
                    else if (faits.reportsDeLaFin > 0) append(" L'heure de reprise a déjà été repoussée : estimation fragile.")
                },
            )
        }

        // ---- Options ALTERNATIVES : seulement des choses réelles ------------------
        // a) Itinéraires fournis par les vraies données (A). Aucun par défaut.
        options += alternatives(disruption, journey)
        // b) Bus de remplacement, SEULEMENT s'ils sont annoncés dans les messages CFF.
        if (a.busDeRemplacementAnnonce) {
            val arriveeBus = maintenant + BUS_ATTENTE_MILLIS + (dureeTrajet * FACTEUR_DUREE_BUS).toLong()
            options += Option(
                type = OptionType.ALTERNATIVE_ROUTE,
                title = "Bus de remplacement (annoncé par les CFF)",
                etaMillis = arriveeBus,
                costToTravellerChf = 0.0,
                reimbursable = false,
                effort = EFFORT_BUS,
                reliability = FIABILITE_BUS,
                explanation = "Des bus de remplacement sont annoncés. Arrivée estimée vers ${heure(arriveeBus)} " +
                        "(estimation : le bus est plus lent que le train). Ton billet reste valable.",
            )
        }

        // ---- Option TAXI (seulement si on connaît la distance) --------------------
        val km = distanceKm(journey.origin, journey.destination)
        if (km != null && km > 0.0) {
            val prixTaxi = round(TAXI_PRISE_EN_CHARGE_CHF + TAXI_PRIX_PAR_KM_CHF * km)
            val vitesse = if (km > 20.0) TAXI_VITESSE_AUTOROUTE_KMH else TAXI_VITESSE_VILLE_KMH
            val arriveeTaxi = maintenant + TAXI_ATTENTE_MILLIS + (km / vitesse * HEURE_MILLIS).toLong()
            val taxiPaye = bloqueCertain && a.responsabilite == Responsabilite.COMPAGNIE &&
                    Rules.TAXI_SI_PAS_ARRIVE_LE_JOUR_MEME
            options += Option(
                type = OptionType.TAXI,
                title = "Prendre un taxi",
                etaMillis = arriveeTaxi,
                costToTravellerChf = if (taxiPaye) 0.0 else prixTaxi,
                reimbursable = taxiPaye,
                effort = EFFORT_TAXI,
                reliability = FIABILITE_TAXI,
                explanation = buildString {
                    append("Arrivée estimée vers ${heure(arriveeTaxi)}. Prix estimé : environ ${prixTaxi.toInt()} CHF.")
                    when {
                        taxiPaye ->
                            append(" Comme il n'y a plus de train possible avant le matin, ce taxi devrait être pris en charge (garde la quittance).")
                        a.responsabilite == Responsabilite.PAS_LA_COMPAGNIE && bloqueProbable ->
                            append(" Attention : la cause est météorologique ou naturelle, la compagnie ne paie donc pas le taxi.")
                        bloqueProbable ->
                            append(" Avant de payer, demande un bon taxi au personnel CFF : s'il n'y a plus de solution avant le matin, " +
                                    "la compagnie peut le prendre en charge. Sans bon, c'est à tes frais.")
                    }
                },
            )
        }

        // ---- Option HÔTEL (si on est bloqué, au moins probablement, jusqu'au matin) ----
        if (bloqueProbable) {
            val hotelPaye = bloqueCertain && a.responsabilite == Responsabilite.COMPAGNIE &&
                    Rules.HOTEL_SI_PAS_ARRIVE_LE_JOUR_MEME
            val arriveeDemain = maxOf(finConnue ?: 0L, a.premierTrainMillis) + dureeTrajet
            options += Option(
                type = OptionType.HOTEL,
                title = "Dormir à l'hôtel et repartir avec le premier train",
                etaMillis = arriveeDemain,
                costToTravellerChf = if (hotelPaye) 0.0 else HOTEL_PRIX_ESTIME_CHF,
                reimbursable = hotelPaye,
                effort = EFFORT_HOTEL,
                reliability = FIABILITE_HOTEL,
                explanation = buildString {
                    when {
                        hotelPaye -> append("Plus de train possible avant le matin : la nuit d'hôtel et le trajet gare-hôtel devraient être pris en charge.")
                        a.responsabilite == Responsabilite.PAS_LA_COMPAGNIE ->
                            append("La cause est météorologique ou naturelle : la compagnie ne paie pas l'hôtel " +
                                    "(environ ${HOTEL_PRIX_ESTIME_CHF.toInt()} CHF, estimation).")
                        else -> append("Demande un bon d'hôtel au personnel CFF : s'il n'y a plus de solution avant le matin, " +
                                "la compagnie peut prendre la nuit en charge. Sans bon : environ ${HOTEL_PRIX_ESTIME_CHF.toInt()} CHF (estimation).")
                    }
                    append(" Départ avec le premier train, arrivée vers ${heure(arriveeDemain)}.")
                },
            )
        }

        // ---- Option INDEMNITÉ (complément, jamais une action principale) ----------
        if (Rules.aDroitAIndemnite(retardAttenteMin)) {
            val certain = finConnue != null && !a.repriseImprobable
            options += if (certain) {
                val pourcentage = (Rules.tauxIndemnite(retardAttenteMin) * 100).toInt()
                Option(
                    type = OptionType.REFUND,
                    title = "Demander l'indemnité de retard ($pourcentage % du billet)",
                    etaMillis = arriveeAttente,
                    costToTravellerChf = 0.0,
                    reimbursable = true,
                    effort = EFFORT_INDEMNITE,
                    reliability = confianceEffective(disruption, faits),
                    explanation = "Avec environ $retardAttenteMin min de retard, tu peux demander $pourcentage % du prix de ton billet.",
                )
            } else {
                Option(
                    type = OptionType.REFUND,
                    title = "Indemnité de retard (si tu arrives avec 60 min de retard ou plus)",
                    etaMillis = arriveeAttente,
                    costToTravellerChf = 0.0,
                    reimbursable = true,
                    effort = EFFORT_INDEMNITE,
                    reliability = FIABILITE_FIN_INCONNUE,
                    explanation = "Garde ton billet : si tu arrives avec 60 min de retard ou plus, tu peux demander 25 % " +
                            "du prix du billet (50 % dès 2 h de retard), quelle que soit la cause.",
                )
            }
        }

        return options
    }

    // =========================================================================
    // 2. NOTER ET CLASSER
    // =========================================================================

    /** Trie par note décroissante. L'indemnité est toujours à la fin : c'est un complément, pas un moyen de rentrer. */
    override fun rank(options: List<Option>, profile: Profile): List<Option> =
        scored(options, profile)
            .sortedWith(compareBy<Pair<Option, Double>> { it.first.type == OptionType.REFUND }.thenByDescending { it.second })
            .map { it.first }

    /**
     * Donne une note entre 0 et 1 à chaque option (1 = meilleure).
     * Public pour que l'interface (C) puisse afficher la note si elle le veut.
     */
    fun scored(options: List<Option>, profile: Profile): List<Pair<Option, Double>> {
        if (options.isEmpty()) return emptyList()

        // Échelles FIXES (et non min-max) : une option extrême, comme « hôtel puis train demain matin »,
        // ne doit pas écraser les différences entre les autres. Constaté sur les données réelles :
        // avec le min-max, « attendre jusqu'à 00:35 » battait un bus arrivant à 22:37.
        //  - temps : minutes de plus que l'option la plus rapide (0 → 0, 4 h ou plus → 1)
        //  - coût  : 0 CHF → 0, [COUT_REFERENCE_CHF] ou plus → 1
        //  - effort : déjà entre 0 et 1
        val etaMin = options.mapNotNull { it.etaMillis }.minOrNull()

        val p = poids(profile)

        return options.map { option ->
            // 0 = meilleur, 1 = pire. Heure d'arrivée inconnue = pire cas pour le temps.
            val eta = option.etaMillis
            val t = if (eta == null || etaMin == null) 1.0
            else ((eta - etaMin).toDouble() / ECHELLE_TEMPS_MILLIS).coerceIn(0.0, 1.0)
            val c = (option.costToTravellerChf.toDouble() / COUT_REFERENCE_CHF).coerceIn(0.0, 1.0)
            val e = option.effort.toDouble().coerceIn(0.0, 1.0)

            val noteBrute = 1.0 - (p.temps * t + p.cout * c + p.effort * e)
            // Pénalité pour les options peu fiables : fiabilité 1 → ×1, fiabilité 0 → ×0.5
            val fiabilite = option.reliability.toDouble().coerceIn(0.0, 1.0)
            val note = noteBrute * (1.0 - PENALITE_FIABILITE_MAX * (1.0 - fiabilite))

            option to note.coerceIn(0.0, 1.0)
        }
    }

    /** Les trois poids (temps, coût, effort), qui s'additionnent toujours à 1. */
    data class Poids(val temps: Double, val cout: Double, val effort: Double)

    fun poids(profile: Profile): Poids {
        val urgence = profile.urgency.toDouble().coerceIn(0.0, 1.0)
        val budget = profile.budgetSensitivity.toDouble().coerceIn(0.0, 1.0)
        val flexibilite = profile.flexibility.toDouble().coerceIn(0.0, 1.0)

        // POIDS_MINIMUM évite qu'un critère compte pour zéro.
        val brutTemps = POIDS_MINIMUM + urgence              // plus pressé → le temps compte plus
        val brutCout = POIDS_MINIMUM + budget                // plus économe → l'argent compte plus
        val brutEffort = POIDS_MINIMUM + (1.0 - flexibilite) // plus flexible → l'effort compte moins

        val somme = brutTemps + brutCout + brutEffort
        return Poids(brutTemps / somme, brutCout / somme, brutEffort / somme)
    }

    // =========================================================================
    // Petits outils
    // =========================================================================

    /** « Maintenant » = le plus tard entre l'heure de départ prévue et la dernière info reçue. */
    private fun maintenant(d: Disruption, j: Journey): Long = maxOf(j.departureMillis, d.lastUpdateMillis)

    /**
     * Durée typique d'une interruption selon sa cause, en heures.
     * HYPOTHÈSES DU MOTEUR (pas des chiffres SBB) : à ajuster avec les données historiques
     * de septembre 2026 fournies par SBB (durée réelle des perturbations passées).
     */
    private fun dureeTypiqueHeures(categorie: CategorieCause, severity: Severity): Double {
        val base = when (categorie) {
            CategorieCause.INFRASTRUCTURE_ENDOMMAGEE -> 12.0 // voie / caténaire à réparer
            CategorieCause.ACCIDENT_DE_PERSONNE -> 2.0
            CategorieCause.PANNE_TECHNIQUE -> 1.0
            CategorieCause.INCONNUE -> 2.0
        }
        return if (severity == Severity.CRITICAL) base * 1.5 else base
    }

    /**
     * Confiance dans l'heure de reprise annoncée, réduite à chaque report
     * (×[FACTEUR_PAR_REPORT] par report, jamais sous [CONFIANCE_MIN]).
     */
    private fun confianceEffective(d: Disruption, faits: FaitsExtraits): Double {
        var c = d.confidence.toDouble().coerceIn(0.0, 1.0)
        repeat(faits.reportsDeLaFin) { c *= FACTEUR_PAR_REPORT }
        return c.coerceAtLeast(CONFIANCE_MIN)
    }

    private fun enMinutes(millis: Long): Long = millis / MINUTE_MILLIS

    /** Numéro du jour (heure suisse), pour savoir si on arrive le lendemain. */
    private fun jour(millis: Long): Long = (millis + decalageHoraireMillis).floorDiv(JOUR_MILLIS)

    /** Minuit (heure suisse) du jour de [millis], en millisecondes UTC. */
    private fun debutJourLocal(millis: Long): Long = jour(millis) * JOUR_MILLIS - decalageHoraireMillis

    /** Heure suisse au format « 23:05 ». */
    private fun heure(millis: Long): String {
        val dansLaJournee = (millis + decalageHoraireMillis).mod(JOUR_MILLIS)
        val h = dansLaJournee / HEURE_MILLIS
        val m = (dansLaJournee % HEURE_MILLIS) / MINUTE_MILLIS
        return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    }

    private fun pct(x: Double): String = "${round(x * 100).toInt()} %"

    companion object {
        // --- Unités de temps ---
        const val MINUTE_MILLIS: Long = 60_000
        const val HEURE_MILLIS: Long = 60 * MINUTE_MILLIS
        const val JOUR_MILLIS: Long = 24 * HEURE_MILLIS
        const val HEURE_ETE_SUISSE_MILLIS: Long = 2 * HEURE_MILLIS

        // --- Score ---
        const val POIDS_MINIMUM = 0.2
        const val PENALITE_FIABILITE_MAX = 0.5
        /** Écarts de temps au-delà de 4 h comptés comme « le pire ». */
        const val ECHELLE_TEMPS_MILLIS: Long = 4 * HEURE_MILLIS
        /** Coût à partir duquel une option compte comme « la plus chère » (environ un taxi Genève–Lausanne). */
        const val COUT_REFERENCE_CHF = 200.0
        /** Chaque report de l'heure de reprise fait baisser la confiance (données réelles : 3 reports en une soirée). */
        const val FACTEUR_PAR_REPORT = 0.75
        const val CONFIANCE_MIN = 0.2
        const val FIABILITE_TRAFIC_REPRIS = 0.9
        /** En dessous de cette chance, on considère qu'aucun train ne repassera ce soir. */
        const val SEUIL_REPRISE_IMPROBABLE = 0.4

        // --- Estimations techniques (PAS des règles SBB) : à ajuster ---
        const val ATTENTE_SI_FIN_INCONNUE_MILLIS: Long = 2 * HEURE_MILLIS
        const val FIABILITE_FIN_INCONNUE = 0.3
        const val TAXI_PRISE_EN_CHARGE_CHF = 7.0       // estimation, à vérifier
        const val TAXI_PRIX_PAR_KM_CHF = 3.5           // estimation, à vérifier
        const val TAXI_VITESSE_VILLE_KMH = 40.0
        const val TAXI_VITESSE_AUTOROUTE_KMH = 70.0
        const val TAXI_ATTENTE_MILLIS: Long = 10 * MINUTE_MILLIS
        const val HOTEL_PRIX_ESTIME_CHF = 150.0        // estimation, à vérifier
        const val BUS_ATTENTE_MILLIS: Long = 20 * MINUTE_MILLIS
        const val FACTEUR_DUREE_BUS = 2.0              // un bus met environ 2× plus de temps (estimation)

        // Effort (0 = facile, 1 = pénible) et fiabilité (0 = incertain, 1 = sûr) par défaut
        const val EFFORT_ATTENDRE = 0.1
        const val EFFORT_NUIT_EN_GARE = 0.9
        const val EFFORT_BUS = 0.6
        const val EFFORT_TAXI = 0.3
        const val EFFORT_HOTEL = 0.5
        const val EFFORT_INDEMNITE = 0.3
        const val FIABILITE_NUIT_EN_GARE = 0.4
        const val FIABILITE_BUS = 0.8
        const val FIABILITE_TAXI = 0.9
        const val FIABILITE_HOTEL = 0.8
    }
}
