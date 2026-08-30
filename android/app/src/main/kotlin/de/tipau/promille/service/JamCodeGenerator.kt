package de.tipau.promille.service

object JamCodeGenerator {
    private const val CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generate(): String = (1..8).map { CHARACTERS.random() }.joinToString("")
}

