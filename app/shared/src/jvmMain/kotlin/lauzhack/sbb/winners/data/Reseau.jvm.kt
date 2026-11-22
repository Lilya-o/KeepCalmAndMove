package lauzhack.sbb.winners.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

/** Desktop/JVM : moteur HTTP CIO (pur Kotlin, sans dépendance système). */
actual fun creerClientHttp(): HttpClient = HttpClient(CIO) { configurerJson() }

/** Le Desktop tourne sur la même machine que le serveur. */
actual val urlServeurParDefaut: String = "http://localhost:8080"
