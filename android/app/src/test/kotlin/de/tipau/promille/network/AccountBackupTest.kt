package de.tipau.promille.network

import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * syncSettings uploads this blob wholesale, so a field this side does not know is
 * a field DELETED from the server, and the iOS decoder then substitutes a default
 * without any error anywhere. The round trip below is the only thing that catches
 * that: it decodes a document written by the Swift encoder and re-encodes it.
 */
class AccountBackupTest {

    /** Swift property names, Swift .iso8601 dates (no fractional seconds). */
    private val swiftBlob = """
    {
      "profile": {
        "weight": 82.5, "height": 180.0, "age": 31,
        "birthDate": "1995-03-24T00:00:00Z",
        "genderRaw": "male", "eliminationRate": 0.17,
        "emergencyContactName": "Anna", "emergencyContactPhone": "+49123",
        "homeStyleRaw": "compact", "activeWidgetsRaw": "bac,water",
        "largeText": true, "highContrast": false, "reducedMotion": true,
        "toleranceMode": true, "warningThreshold": 0.6,
        "stomachStatusRaw": "full", "statusSkinRaw": "neon",
        "tipsyThreshold": 0.02, "drunkThreshold": 0.35,
        "carefulThreshold": 0.9, "dangerThreshold": 1.6,
        "accentColorHex": "3FA7D6", "sipVolumeML": 30.0,
        "activeMedicationsRaw": "ibu", "healthKitEnabled": true,
        "weeklyDrinkLimit": 7, "soberDaysGoal": 3,
        "isProbationaryDriver": true, "drunkModeAuto": true,
        "onboardingStepsCompleted": ["welcome", "body"],
        "hasCompletedOnboarding": true
      },
      "waterLog": {"2026-08-27": 4},
      "customMixes": [
        {
          "id": "9F1C6B2A-1111-2222-3333-444455556666",
          "name": "Hugo",
          "ingredients": [{"id": "AB", "name": "Prosecco", "abv": 11.0, "volume": 100.0}],
          "createdAt": "2026-08-27T21:15:00Z"
        }
      ],
      "customDrinks": [
        {
          "id": "1A2B3C4D-5555-6666-7777-888899990000",
          "name": "Hausbier", "categoryRaw": "beer", "volume": 500.0,
          "abv": 4.9, "calories": 210, "iconName": "mug.fill",
          "usageCount": 12, "barcode": "4001234567890"
        }
      ]
    }
    """.trimIndent()

    @Test
    fun `a document written by the iOS encoder decodes field for field`() {
        val backup = blobJson.decodeFromString(AccountBackup.serializer(), swiftBlob)
        val p = requireNotNull(backup.profile)

        assertEquals(82.5, p.weight)
        assertEquals(31, p.age)
        assertEquals("1995-03-24T00:00:00Z", p.birthDate)
        assertEquals("male", p.genderRaw)
        assertEquals(0.17, p.eliminationRate)
        assertEquals("Anna", p.emergencyContactName)
        assertEquals("bac,water", p.activeWidgetsRaw)
        assertTrue(p.largeText && p.reducedMotion && p.toleranceMode)
        assertEquals("neon", p.statusSkinRaw)
        assertEquals(1.6, p.dangerThreshold)
        assertEquals("3FA7D6", p.accentColorHex)
        assertEquals(listOf("welcome", "body"), p.onboardingStepsCompleted)
        assertTrue(p.hasCompletedOnboarding)

        assertEquals(mapOf("2026-08-27" to 4), backup.waterLog)
        assertEquals("Hugo", backup.customMixes?.single()?.name)
        assertEquals(12, backup.customDrinks?.single()?.usageCount)
    }

    @Test
    fun `re-encoding keeps every key, so an upload never deletes a field`() {
        val original = blobJson.parseToJsonElement(swiftBlob).jsonObject
        val encoded = blobJson.encodeToString(
            AccountBackup.serializer(),
            blobJson.decodeFromString(AccountBackup.serializer(), swiftBlob)
        )
        val roundTripped = blobJson.parseToJsonElement(encoded).jsonObject

        assertEquals(original.keys, roundTripped.keys)
        assertEquals(
            original["profile"]!!.jsonObject.keys,
            roundTripped["profile"]!!.jsonObject.keys,
            "a missing profile key is a setting silently reset on the iPhone"
        )
        assertEquals(original["customMixes"], roundTripped["customMixes"], "ingredients pass through untouched")
        assertEquals(original["customDrinks"], roundTripped["customDrinks"])
    }

    @Test
    fun `an older backup missing newer fields still decodes as a whole`() {
        val old = """{"profile":{"weight":70.0,"height":175.0,"age":25}}"""
        val p = requireNotNull(
            blobJson.decodeFromString(AccountBackup.serializer(), old).profile
        )
        assertEquals(0.15, p.eliminationRate, "falls back to the UserProfile default")
        assertEquals("C9802F", p.accentColorHex)
        assertEquals(4, p.soberDaysGoal)
        assertNull(p.birthDate, "unknown birthDate keeps the local value instead of inventing one")
    }

    @Test
    fun `blob dates carry no fractional seconds, which the Swift decoder rejects`() {
        val formatted = BlobDates.format(1_756_331_700)
        assertTrue(formatted.endsWith("Z") && !formatted.contains("."), "got $formatted")
        assertEquals(1_756_331_700, BlobDates.parse(formatted))
    }
}
