package lauzhack.sbb.winners.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/** Android : moteur HTTP OkHttp. */
actual fun creerClientHttp(): HttpClient = HttpClient(OkHttp) { configurerJson() }

/**
 * Émulateur : on passe par `adb reverse tcp:8080 tcp:8080`, qui tunnelise `localhost:8080`
 * du téléphone vers le serveur de l'hôte (fiable, contrairement à 10.0.2.2 sur certains réseaux).
 * Téléphone réel sur le même Wi-Fi : remplacer par l'IP LAN de la machine (ex. http://192.168.x.x:8080).
 */
actual val urlServeurParDefaut: String = "http://localhost:8080"
