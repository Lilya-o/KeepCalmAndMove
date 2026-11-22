package lauzhack.sbb.winners.data

import io.ktor.client.HttpClient

/**
 * Partagé vs spécifique à la plateforme (bon exemple pour le pitch) :
 *  - le CODE qui parle au serveur (parsing JSON, endpoints) est partagé dans `commonMain` ;
 *  - seul le MOTEUR HTTP change selon la plateforme (OkHttp sur Android, CIO sur Desktop).
 *
 * `actual` défini dans androidMain (OkHttp) et jvmMain (CIO).
 */
expect fun creerClientHttp(): HttpClient

/**
 * URL du serveur de replay (rôle A) par défaut.
 *  - Desktop : `localhost`.
 *  - Android : `10.0.2.2` est l'alias de la machine hôte vu depuis l'émulateur.
 */
expect val urlServeurParDefaut: String
