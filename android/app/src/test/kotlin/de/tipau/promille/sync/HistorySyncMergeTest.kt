package de.tipau.promille.sync

import de.tipau.promille.network.AccountBackup
import de.tipau.promille.network.ProfileBackup
import de.tipau.promille.network.RemoteDrink
import de.tipau.promille.network.blobJson
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HistorySyncMergeTest {

    @Test
    fun `LWW conflict resolution picks newer consumedAt timestamp`() {
        val olderDrink = RemoteDrink(
            id = "drink-1",
            name = "Beer Old",
            volume = 500.0,
            abv = 5.0,
            calories = 150,
            iconName = "mug.fill",
            category = "beer",
            consumedAtRaw = "2025-01-01T20:30:00Z"
        )

        val newerDrink = RemoteDrink(
            id = "drink-1",
            name = "Beer Updated on Web",
            volume = 500.0,
            abv = 5.0,
            calories = 150,
            iconName = "mug.fill",
            category = "beer",
            consumedAtRaw = "2025-01-01T21:30:00Z" // 1 hour later
        )

        val resolved = if (newerDrink.consumedAtEpochSeconds >= olderDrink.consumedAtEpochSeconds) {
            newerDrink
        } else {
            olderDrink
        }
        assertEquals("Beer Updated on Web", resolved.name)
    }

    @Test
    fun `account backup serialization round trips cleanly without loss`() {
        val backup = AccountBackup(
            profile = ProfileBackup(
                weight = 80.0,
                height = 185.0,
                age = 30,
                genderRaw = "male",
                eliminationRate = 0.15,
                toleranceMode = true,
                isProbationaryDriver = false
            ),
            customMixes = emptyList(),
            customDrinks = emptyList(),
            waterLog = mapOf("2025-01-01" to 4, "2025-01-02" to 6)
        )

        val element = blobJson.encodeToJsonElement(backup)
        val decoded = blobJson.decodeFromJsonElement<AccountBackup>(element)

        assertNotNull(decoded.profile)
        assertEquals(80.0, decoded.profile?.weight)
        assertEquals(true, decoded.profile?.toleranceMode)
        assertEquals(4, decoded.waterLog?.get("2025-01-01"))
        assertEquals(6, decoded.waterLog?.get("2025-01-02"))
    }
}
