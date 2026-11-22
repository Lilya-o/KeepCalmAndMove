package lauzhack.sbb.winners.engine

/**
 * Règles des droits des voyageurs utilisées par le moteur.
 *
 * Chaque valeur indique sa source.
 * « À VÉRIFIER » = pas encore confirmé sur la page officielle SBB :
 * le moteur peut s'en servir pour calculer, mais l'interface ne doit pas
 * présenter ces chiffres comme des faits officiels tant que ce n'est pas vérifié.
 *
 * Ce fichier ne contient QUE des règles SBB / droits des voyageurs.
 * Les estimations du moteur (durées typiques, prix du taxi, etc.) sont ailleurs.
 */
object Rules {

    // --- Indemnité en cas de retard à l'arrivée ---------------------------------
    // 25 % du prix du billet dès 60 min de retard, 50 % dès 120 min,
    // aussi en cas de force majeure (contrairement au taxi et à l'hôtel).
    // Sources : Beobachter (« Öffentlicher Verkehr – SBB-Verspätungen »),
    //           BLS (« Fahrgastrechte »).
    // À VÉRIFIER sur la page officielle SBB.
    const val RETARD_INDEMNITE_25_MIN: Long = 60
    const val RETARD_INDEMNITE_50_MIN: Long = 120
    const val TAUX_INDEMNITE_25: Double = 0.25
    const val TAUX_INDEMNITE_50: Double = 0.50

    // Les indemnités inférieures à 5 CHF ne sont pas versées.
    // Source : BLS (« Fahrgastrechte »). À VÉRIFIER pour SBB.
    const val INDEMNITE_MINIMUM_CHF: Double = 5.0

    // --- Taxi et hôtel ---------------------------------------------------------
    // Pris en charge SEULEMENT si :
    //   1. le voyageur ne peut plus atteindre sa destination le jour même
    //      (dernière correspondance ratée ou supprimée), OU si le personnel SBB l'a autorisé ;
    //   2. ET l'entreprise de transport est responsable : PAS en cas d'intempéries
    //      ou d'autre événement naturel.
    // Sources : réponses de collaborateurs SBB sur community.sbb.ch
    //           (« Trains en retard, dédommagement », « Remboursement », « Taxi Beleg »),
    //           qui renvoient à sbb.ch/.../indemnisation-cas-retard.html ;
    //           BLS (« Droits des voyageurs », prestations exclues).
    // Hôtel : nuit avec petit-déjeuner + trajet gare-hôtel (Beobachter, BLS).
    // À VÉRIFIER : plafonds de prix.
    const val TAXI_SI_PAS_ARRIVE_LE_JOUR_MEME: Boolean = true
    const val HOTEL_SI_PAS_ARRIVE_LE_JOUR_MEME: Boolean = true

    /** Qui est responsable de la perturbation ? (décide si taxi / hôtel peuvent être payés) */
    enum class Responsabilite {
        /** Ex. panne technique : taxi / hôtel payés si on n'arrive plus le jour même. */
        COMPAGNIE,
        /** Intempéries, catastrophe naturelle : taxi / hôtel PAS payés. */
        PAS_LA_COMPAGNIE,
        /** Ex. incendie, accident de personne : on ne sait pas → ne rien promettre, demander au personnel. */
        INCERTAINE,
    }

    /**
     * Mots qui indiquent une cause dont la compagnie n'est PAS responsable
     * (intempéries, événements naturels). En FR, DE, IT et EN.
     * TODO (Koog) : plus tard, l'agent IA pourra classer la cause plus finement.
     */
    val MOTS_HORS_RESPONSABILITE: List<String> = listOf(
        // Français
        "intempérie", "tempête", "orage", "neige", "avalanche", "inondation",
        "éboulement", "glissement", "crue", "grêle", "météo", "arbre",
        // Allemand
        "unwetter", "sturm", "schnee", "lawine", "überschwemmung", "hochwasser",
        "murgang", "erdrutsch", "felssturz", "hagel", "wetter",
        // Italien
        "maltempo", "tempesta", "nevicat", "valanga", "frana", "alluvione",
        // Anglais
        "storm", "snow", "flood", "landslide", "weather",
    )

    /**
     * Causes où l'on ne peut pas savoir, depuis le message, si la compagnie est responsable.
     * Ex. un incendie peut venir d'un câble SBB (responsable) ou d'un feu de forêt (pas responsable).
     */
    val MOTS_RESPONSABILITE_INCERTAINE: List<String> = listOf(
        "incendie", "feu", "brand", "fire", "incendio",
        "vandalisme", "vandalismus", "sabotage", "manifestation",
    )
    val EXPRESSIONS_RESPONSABILITE_INCERTAINE: List<String> = listOf(
        "accident de personne", "personenunfall", "incidente a una persona", "person struck",
        "objet sur la voie", "gegenstand auf", "colis suspect", "verdächtig",
    )

    /** Vrai si la cause n'est pas de la responsabilité de la compagnie (intempéries…). */
    fun causeHorsResponsabilite(texte: String): Boolean =
        contientUnMot(texte, MOTS_HORS_RESPONSABILITE)

    /** Classe la responsabilité à partir de la cause (et du texte des messages). */
    fun responsabilite(texte: String): Responsabilite = when {
        causeHorsResponsabilite(texte) -> Responsabilite.PAS_LA_COMPAGNIE
        contientUnMot(texte, MOTS_RESPONSABILITE_INCERTAINE) ||
            EXPRESSIONS_RESPONSABILITE_INCERTAINE.any { texte.lowercase().contains(it) } ->
            Responsabilite.INCERTAINE
        else -> Responsabilite.COMPAGNIE
    }

    /** Taux d'indemnité (0.0, 0.25 ou 0.5) pour un retard donné, en minutes. */
    fun tauxIndemnite(retardMinutes: Long): Double = when {
        retardMinutes >= RETARD_INDEMNITE_50_MIN -> TAUX_INDEMNITE_50
        retardMinutes >= RETARD_INDEMNITE_25_MIN -> TAUX_INDEMNITE_25
        else -> 0.0
    }

    /** Vrai si le retard donne droit à une indemnité. */
    fun aDroitAIndemnite(retardMinutes: Long): Boolean =
        retardMinutes >= RETARD_INDEMNITE_25_MIN
}

/**
 * Vrai si le texte contient l'un des mots-clés.
 * Un mot du texte compte s'il est ÉGAL au mot-clé, ou s'il COMMENCE par lui quand le
 * mot-clé fait au moins 5 lettres (« intempéries » → « intempérie »).
 * Les mots-clés courts (« feu », « crue ») doivent être exacts, pour que « feux » (signaux)
 * ou « Genève » ne déclenchent rien par erreur.
 */
internal fun contientUnMot(texte: String, motsCles: List<String>): Boolean {
    val mots = texte.lowercase()
        .split(' ', ',', '.', ';', ':', '-', '(', ')', '/', '\'', '’', '"', '\n', '\t', '!', '?')
        .filter { it.isNotBlank() }
    return mots.any { mot ->
        motsCles.any { cle -> mot == cle || (cle.length >= 5 && mot.startsWith(cle)) }
    }
}
