package de.tipau.promille.service

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import de.tipau.promille.repository.DrinkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object CsvExportService {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
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

        val csvBuilder = StringBuilder()
        // Header
        csvBuilder.append("Timestamp;Datum/Uhrzeit;Name;Kategorie;Volumen (ml);Alkoholgehalt (%);Kalorien (kcal);Alkohol (g)\n")

        drinks.forEach { drink ->
            val dateStr = formatter.format(Instant.ofEpochSecond(drink.timestampEpochSeconds))
            val alcoholGrams = drink.volume * (drink.abv / 100.0) * 0.789

            csvBuilder.append("${drink.timestampEpochSeconds};")
            csvBuilder.append("\"$dateStr\";")
            csvBuilder.append("\"${drink.name.replace("\"", "\"\"")}\";")
            csvBuilder.append("\"${drink.categoryRaw}\";")
            csvBuilder.append(String.format(Locale.GERMANY, "%.0f;", drink.volume))
            csvBuilder.append(String.format(Locale.GERMANY, "%.2f;", drink.abv))
            csvBuilder.append("${drink.calories};")
            csvBuilder.append(String.format(Locale.GERMANY, "%.2f\n", alcoholGrams))
        }

        try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, "promille_export_${System.currentTimeMillis()}.csv")
            file.writeText(csvBuilder.toString(), Charsets.UTF_8)

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
        } catch (e: Exception) {
            // Fallback plain text share if FileProvider is not yet configured in Manifest
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, csvBuilder.toString())
                putExtra(Intent.EXTRA_SUBJECT, "promille. Trinkverlauf Export (CSV)")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(intent, "Verlauf exportieren"))
            }
        }
    }
}
