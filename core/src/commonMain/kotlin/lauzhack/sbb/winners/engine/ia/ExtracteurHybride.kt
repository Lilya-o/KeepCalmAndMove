package lauzhack.sbb.winners.engine.ia

import lauzhack.sbb.winners.engine.RuleBasedOptionEngine.CategorieCause
import lauzhack.sbb.winners.engine.Rules.Responsabilite
import lauzhack.sbb.winners.model.Disruption
import lauzhack.sbb.winners.model.MessageSource

/**
 * Extraction des faits : RÈGLES d'abord, IA seulement si les règles n'ont pas tout compris.
 *
 * Pourquoi c'est efficace :
 *  1. Les règles traitent la plupart des messages (textes standardisés) : 0 appel IA.
 *  2. Cache : le même ensemble de messages n'est jamais envoyé deux fois (replay minute par minute).
 *  3. Messages raccourcis : seuls les [MAX_MESSAGES] derniers, coupés à [MAX_CARACTERES] caractères.
 *  4. Réponse en 6 lignes fixes (pas de JSON) : marche aussi avec un petit modèle local (Ollama).
 *
 * Garde-fous (l'IA ne doit rien inventer) :
 *  - une heure de reprise n'est acceptée que si elle est ÉCRITE dans les messages ;
 *  - une durée n'est acceptée que si son nombre est écrit dans les messages ;
 *  - l'IA ne peut jamais rendre la compagnie « responsable » (ça déclencherait des promesses d'argent) ;
 *  - réponse illisible → on garde les règles seules.
 */
class ExtracteurHybride(
    private val modele: ModeleIA?,
    val stats: StatistiquesIA = StatistiquesIA(),
) {
    private val cache = CacheLimite<String, FaitsExtraits>(TAILLE_CACHE)

    suspend fun extraire(disruption: Disruption): FaitsExtraits {
        val regles = ExtracteurParRegles.extraire(disruption)

        // 1. Les règles suffisent, ou pas d'IA disponible : on s'arrête là.
        if (regles.suffisant || disruption.estimatedEndMillis != null) {
            stats.evitesGraceAuxRegles++
            return regles
        }
        if (modele == null) return regles

        // 2. Déjà demandé pour exactement ces messages ?
        val message = messagePourIA(disruption)
        cache.get(message)?.let { stats.reponsesDuCache++; return it }

        // 3. Appel à l'IA
        stats.appelsIA++
        val reponse = modele.repondre(CONSIGNE_EXTRACTION, message)
        if (reponse == null) {
            stats.echecsIA++
            return regles // pas de cache : on réessaiera à la prochaine mise à jour
        }
        val faits = fusionner(regles, lireReponse(reponse), ExtracteurParRegles.texteComplet(disruption))
        cache.put(message, faits)
        return faits
    }

    /** Ce que l'IA a répondu, avant vérification. */
    internal data class ReponseIA(
        val categorie: String?,
        val reprise: String?,
        val dureeMinutes: Int?,
        val bus: Boolean?,
        val tendance: String?,
    )

    internal fun lireReponse(texte: String): ReponseIA {
        val lignes = texte.lines().mapNotNull { ligne ->
            val i = ligne.indexOf(':')
            if (i <= 0) null else ligne.substring(0, i).trim().uppercase() to ligne.substring(i + 1).trim()
        }.toMap()
        return ReponseIA(
            categorie = lignes["CATEGORIE"]?.uppercase(),
            reprise = lignes["REPRISE"]?.let { Regex("^(\\d{1,2})[:h.](\\d{2})$").find(it.trim()) }
                ?.let { "${it.groupValues[1].padStart(2, '0')}:${it.groupValues[2]}" },
            dureeMinutes = lignes["DUREE"]?.trim()?.toIntOrNull(),
            bus = when (lignes["BUS"]?.uppercase()) { "OUI" -> true; "NON" -> false; else -> null },
            tendance = lignes["TENDANCE"]?.uppercase(),
        )
    }

    /** L'IA COMPLÈTE les règles, elle ne les contredit jamais. Chaque info est vérifiée. */
    internal fun fusionner(regles: FaitsExtraits, ia: ReponseIA, texteSource: String): FaitsExtraits {
        var rejete = false

        val categorie = if (regles.categorie != CategorieCause.INCONNUE) regles.categorie else when (ia.categorie) {
            "INFRASTRUCTURE_ENDOMMAGEE" -> CategorieCause.INFRASTRUCTURE_ENDOMMAGEE
            "ACCIDENT_DE_PERSONNE" -> CategorieCause.ACCIDENT_DE_PERSONNE
            "PANNE_TECHNIQUE" -> CategorieCause.PANNE_TECHNIQUE
            else -> CategorieCause.INCONNUE // METEO ou inconnu : la catégorie reste inconnue
        }

        // Responsabilité : l'IA peut seulement la rendre PLUS prudente.
        val responsabilite = when {
            regles.responsabilite == Responsabilite.PAS_LA_COMPAGNIE -> Responsabilite.PAS_LA_COMPAGNIE
            ia.categorie == "METEO" -> Responsabilite.PAS_LA_COMPAGNIE
            regles.responsabilite == Responsabilite.COMPAGNIE -> Responsabilite.COMPAGNIE // vient des règles
            else -> Responsabilite.INCERTAINE
        }

        val reprise = regles.repriseHeure ?: ia.reprise?.takeIf { heure ->
            ExtracteurParRegles.heureEcriteDans(heure, texteSource).also { if (!it) rejete = true }
        }

        val duree = ia.dureeMinutes?.takeIf { n ->
            (n in 1..(48 * 60) && Regex("(?<!\\d)$n(?!\\d)").containsMatchIn(texteSource)).also { if (!it) rejete = true }
        }

        if (rejete) stats.reponsesRejetees++

        return regles.copy(
            categorie = categorie,
            responsabilite = responsabilite,
            busDeRemplacement = regles.busDeRemplacement || ia.bus == true,
            tendance = if (regles.tendance != Tendance.INCONNUE) regles.tendance else when (ia.tendance) {
                "AMELIORATION" -> Tendance.AMELIORATION
                "STABLE" -> Tendance.STABLE
                "AGGRAVATION" -> Tendance.AGGRAVATION
                else -> Tendance.INCONNUE
            },
            repriseHeure = reprise,
            dureeMinutes = regles.dureeMinutes ?: duree,
            source = SourceDesFaits.REGLES_ET_IA,
        )
    }

    private fun messagePourIA(d: Disruption): String = buildString {
        append("Cause : ").append(d.cause.take(MAX_CARACTERES)).append('\n')
        append("Messages (du plus ancien au plus récent) :\n")
        d.messages.sortedBy { it.timestampMillis }.takeLast(MAX_MESSAGES).forEach { m ->
            val origine = when (m.source) {
                MessageSource.PUBLIC_TICKER -> "public"
                MessageSource.INTERNAL_TICKER -> "interne"
                MessageSource.TIMETABLE -> "horaire"
            }
            append("- [").append(origine).append("] ").append(m.text.take(MAX_CARACTERES).replace('\n', ' ')).append('\n')
        }
    }

    companion object {
        const val MAX_MESSAGES = 6
        const val MAX_CARACTERES = 400
        const val TAILLE_CACHE = 200

        val CONSIGNE_EXTRACTION = """
            Tu lis des messages de perturbation ferroviaire suisse (FR, DE, IT ou EN, parfois du jargon interne).
            Réponds UNIQUEMENT avec ces 5 lignes, sans aucun autre texte :
            CATEGORIE: INFRASTRUCTURE_ENDOMMAGEE ou ACCIDENT_DE_PERSONNE ou PANNE_TECHNIQUE ou METEO ou INCONNUE
            REPRISE: l'heure de reprise au format HH:MM si elle est écrite dans les messages, sinon INCONNUE
            DUREE: la durée annoncée en minutes si elle est écrite (ex. « Prognose 60' » → 60), sinon INCONNUE
            BUS: OUI si des bus de remplacement sont annoncés, sinon NON
            TENDANCE: AMELIORATION ou STABLE ou AGGRAVATION ou INCONNUE
            N'invente rien et ne calcule rien : si une information n'est pas écrite, réponds INCONNUE ou NON.
        """.trimIndent()
    }
}
