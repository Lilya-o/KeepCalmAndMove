package lauzhack.sbb.winners

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform