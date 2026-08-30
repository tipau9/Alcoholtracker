package de.tipau.promille.service

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import de.tipau.promille.bac.DrinkCategory
import de.tipau.promille.bac.germanName
import de.tipau.promille.repository.DrinkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds a CSV of the full drink history for sharing.
 * Mirrors Alcoholtracker/Services/ExportService.swift 1:1.
 * German Excel conventions: semicolon separator, comma decimals, UTF-8 BOM.
 */
object CsvExportService {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        .withZone(ZoneId.systemDefault())

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())

    suspend fun exportAndShare(
        context: Context,
        drinkRepository: DrinkRepository
    ) = withContext(Dispatchers.IO) {
        val drinks = drinkRepository.getAllDrinksSortedOnce()

        if (drinks.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Keine Getränke zum Exportieren vorhanden", Toast.LENGTH_SHORT).show()
            }
            return@withContext
        }

        val lines = mutableListOf<String>()
        lines.add("Datum;Uhrzeit;Name;Kategorie;Volumen (ml);Alkohol (%);Alkohol (g);Kalorien")

        drinks.forEach { drink ->
            val instant = Instant.ofEpochSecond(drink.timestampEpochSeconds)
            val dateStr = dateFormatter.format(instant)
            val timeStr = timeFormatter.format(instant)
            val categoryName = DrinkCategory.from(drink.categoryRaw).germanName
            val alcoholGrams = drink.volume * (drink.abv / 100.0) * 0.789

            val fields = listOf(
                dateStr,
                timeStr,
                escape(drink.name),
                categoryName,
                String.format(Locale.GERMANY, "%.0f", drink.volume),
                String.format(Locale.GERMANY, "%.1f", drink.abv),
                String.format(Locale.GERMANY, "%.1f", alcoholGrams),
                "${drink.calories}"
            )
            lines.add(fields.joinToString(";"))
        }

        try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, "promille-verlauf.csv")

            // UTF-8 BOM so Excel automatically recognizes Umlauts
            FileOutputStream(file).use { fos ->
                fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                fos.write(lines.joinToString("\r\n").toByteArray(Charsets.UTF_8))
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "promille. Trinkverlauf Export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(intent, "Verlauf exportieren"))
            }
        } catch (_: Exception) {
            val plainText = lines.joinToString("\n")
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, plainText)
                putExtra(Intent.EXTRA_SUBJECT, "promille. Trinkverlauf Export (CSV)")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(intent, "Verlauf exportieren"))
            }
        }
    }

    private fun escape(field: String): String {
        if (field.contains(";") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"${field.replace("\"", "\"\"")}\""
        }
        return field
    }
}
