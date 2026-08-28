package de.tipau.promille.platform

import de.tipau.promille.bac.HydrationCalculator
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Open-Meteo client for weather-aware hydration insights.
 * Requires no API key. Gracefully returns null on failure or offline.
 */
class WeatherService(
    private val client: HttpClient = HttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    @Serializable
    private data class CurrentWeather(
        val temperature_2m: Double? = null
    )

    @Serializable
    private data class OpenMeteoResponse(
        val current: CurrentWeather? = null
    )

    /**
     * Fetches current temperature in Celsius at the given coordinates.
     */
    suspend fun fetchCurrentTemperature(latitude: Double, longitude: Double): Double? = runCatching {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m"
        val response = client.get(url)
        val body = response.bodyAsText()
        val parsed = json.decodeFromString<OpenMeteoResponse>(body)
        parsed.current?.temperature_2m
    }.getOrNull()

    /**
     * Calculates additional sweat loss in ml based on local temperature and duration.
     */
    fun calculateSweatLoss(temperatureC: Double, hours: Double, comfortC: Double = 22.0): Double =
        HydrationCalculator.heatSweatLossMl(temperatureC, hours, comfortC)
}
