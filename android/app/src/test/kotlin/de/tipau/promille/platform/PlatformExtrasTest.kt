package de.tipau.promille.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlatformExtrasTest {

    @Test
    fun `weather service parses open meteo response correctly`() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"current":{"temperature_2m":28.5}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val weatherService = WeatherService(client = HttpClient(mockEngine))
        val temp = weatherService.fetchCurrentTemperature(52.52, 13.405)

        assertNotNull(temp)
        assertEquals(28.5, temp)

        // Calculate sweat loss at 28.5 C over 3 hours
        val sweatLoss = weatherService.calculateSweatLoss(temp, 3.0, comfortC = 22.0)
        assertTrue(sweatLoss > 0.0, "Sweat loss should be positive for temp above comfort")
    }
}
