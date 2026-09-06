package de.tipau.promille.service

object JamCodeGenerator {
    const val CODE_LENGTH = 6
    private const val CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generate(): String = (1..CODE_LENGTH).map { CHARACTERS.random() }.joinToString("")

    fun sanitize(input: String): String =
        input.uppercase().filter { it.isLetter() || it.isDigit() }.take(CODE_LENGTH)
}

