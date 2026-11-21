plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
}

group = "lauzhack.sbb.winners"
version = "1.0.0"
application {
    mainClass = "lauzhack.sbb.winners.ApplicationKt"
}

// Les chemins du replay (data/Daten/Daten) sont relatifs à la racine du dépôt.
// Par défaut Gradle lance `run` depuis le dossier du module (server/), d'où le
// NoSuchFileException. On force donc le répertoire de travail sur la racine.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

dependencies {
    api(project(":core"))
    implementation(libs.logback)
    implementation(libs.kotlin.csv)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serializationJson)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}