package lauzhack.sbb.winners.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/** Android : moteur HTTP OkHttp. */
actual fun creerClientHttp(): HttpClient = HttpClient(OkHttp) { configurerJson() }

/** Depuis l'émulateur Android, la machine hôte est joignable via 10.0.2.2. */
actual val urlServeurParDefaut: String = "http://10.0.2.2:8080"
