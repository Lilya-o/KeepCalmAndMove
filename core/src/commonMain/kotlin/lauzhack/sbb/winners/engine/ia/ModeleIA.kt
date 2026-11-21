
package lauzhack.sbb.winners.engine.ia

/**
 * Le « cerveau » IA, vu par le moteur : on lui donne une consigne et un message, il répond du texte.
 *
 * Volontairement minuscule :
 *  - le moteur ne dépend pas d'un fournisseur (OpenAI, Anthropic, Ollama…) ;
 *  - dans les tests, on le remplace par un faux modèle (pas de réseau, pas de clé) ;
 *  - l'implémentation réelle est [KoogModeleIA] (framework Koog de JetBrains).
 *
 * Doit renvoyer null en cas d'échec (pas de réseau, délai dépassé, erreur) :
 * le moteur continue alors SANS IA, avec ses règles et ses textes construits par code.
 */
fun interface ModeleIA {
    suspend fun repondre(consigneSysteme: String, message: String): String?
}

/** Langues de l'interface. */
enum class Langue(val nom: String) { FR("français"), DE("allemand"), EN("anglais") }

/** Compteurs pour montrer au jury que l'IA est utilisée avec parcimonie. */
class StatistiquesIA {
    var appelsIA: Int = 0
        internal set
    var reponsesDuCache: Int = 0
        internal set
    var evitesGraceAuxRegles: Int = 0
        internal set
    var reponsesRejetees: Int = 0
        internal set
    var echecsIA: Int = 0
        internal set

    override fun toString(): String =
        "appels IA : $appelsIA · cache : $reponsesDuCache · évités par les règles : $evitesGraceAuxRegles · " +
                "rejetés par les garde-fous : $reponsesRejetees · échecs : $echecsIA"
}

/** Petit cache « les N dernières réponses » (évite de rappeler l'IA pendant le replay). */
internal class CacheLimite<K, V>(private val tailleMax: Int) {
    private val map = LinkedHashMap<K, V>()

    fun get(cle: K): V? = map[cle]

    fun put(cle: K, valeur: V) {
        map.remove(cle)
        map[cle] = valeur
        while (map.size > tailleMax) map.remove(map.keys.first())
    }
}


