package de.tipau.promille.service

object JamCodeGenerator {
    private const val CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generate(): String = (1..6).map { CHARACTERS.random() }.joinToString("")
}

