package de.tipau.promille.bac

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Pins all Phase 0 services to the live Swift implementation.
 * Tolerance is strictly 1e-6.
 */
class Phase0VectorTest {

    private val root = JsonParser.parseReader(vectorFile().reader()).asJsonObject
    private val tolerance = root["tolerance"].asDouble

    private fun vectorFile(): File {
        val path = System.getProperty("bac.vectors")
            ?: fail("bac.vectors system property not set; see bac/build.gradle.kts")
        val file = File(path)
        assertTrue(file.isFile, "golden vectors not found at $path")
        return file
    }

    private fun near(what: String, expected: Double, actual: Double) {
        if (abs(expected - actual) > tolerance) {
            fail("$what: expected $expected, got $actual (delta ${abs(expected - actual)})")
        }
    }

    @Test
    fun hydrationPerDrinkVectorsMatch() {
        val hyd = root["hydration"]?.asJsonObject ?: return
        val perDrink = hyd["perDrink"].asJsonArray
        perDrink.forEach { element ->
            val obj = element.asJsonObject
            val name = obj["name"].asString
            val drink = Drink(
                name = name,
                volumeML = obj["volumeML"].asDouble,
                abv = obj["abv"].asDouble,
                category = DrinkCategory.from(obj["category"]?.asString ?: "beer"),
                mixerVolumeML = obj["mixerVolume"]?.asDouble ?: 0.0,
                mixerWaterContentPercent = obj["mixerWaterContent"]?.asDouble ?: 0.0
            )

            near("$name waterIn", obj["waterIn"].asDouble, HydrationCalculator.waterIn(drink))
            near("$name diuresisLoss", obj["diuresisLoss"].asDouble, HydrationCalculator.diuresisLoss(drink))
            near("$name netHydration", obj["netHydration"].asDouble, HydrationCalculator.netHydration(drink))
            near(
                "$name mixerWaterContribution",
                obj["mixerWaterContribution"].asDouble,
                HydrationCalculator.mixerWaterContribution(drink)
            )
        }
    }

    @Test
    fun hydrationSessionsAndStatusVectorsMatch() {
        val hyd = root["hydration"]?.asJsonObject ?: return

        // Status absolute
        hyd["statusAbsolute"]?.asJsonArray?.forEach {
            val item = it.asJsonObject
            val net = item["netML"].asDouble
            val expected = item["status"].asString
            val actual = when (HydrationCalculator.status(net)) {
                HydrationStatus.OK -> "ok"
                HydrationStatus.NEEDS_LITTLE -> "needsLittle"
                HydrationStatus.NEEDS_MORE -> "needsMore"
                HydrationStatus.NEEDS_LOTS -> "needsLots"
            }
            assertEquals(expected, actual, "statusAbsolute at net $net")
        }

        // Status relative
        val p75 = Profile(weightKg = 75.0, heightCm = 180.0, age = 25, gender = Gender.MALE)
        val p55 = Profile(weightKg = 55.0, heightCm = 165.0, age = 28, gender = Gender.FEMALE)

        hyd["statusRelative"]?.asJsonArray?.forEach {
            val item = it.asJsonObject
            val weight = item["profileWeight"].asDouble
            val net = item["netML"].asDouble
            val profile = if (weight == 75.0) p75 else p55
            near(
                "dehydrationFraction at net $net w=$weight",
                item["fraction"].asDouble,
                HydrationCalculator.dehydrationFraction(net, profile)
            )
            val expected = item["status"].asString
            val actual = when (HydrationCalculator.status(net, profile)) {
                HydrationStatus.OK -> "ok"
                HydrationStatus.NEEDS_LITTLE -> "needsLittle"
                HydrationStatus.NEEDS_MORE -> "needsMore"
                HydrationStatus.NEEDS_LOTS -> "needsLots"
            }
            assertEquals(expected, actual, "statusRelative at net $net w=$weight")
        }

        // Compensation
        hyd["compensation"]?.asJsonArray?.forEach {
            val item = it.asJsonObject
            val net = item["netML"].asDouble
            assertEquals(
                item["compensationWaterMl"].asInt,
                HydrationCalculator.compensationWaterMl(net),
                "compensation at net $net"
            )
        }

        // Sweat loss
        hyd["sweatLoss"]?.asJsonArray?.forEach {
            val item = it.asJsonObject
            val id = item["id"].asString
            near(
                "sweatLoss $id",
                item["sweatLossMl"].asDouble,
                HydrationCalculator.heatSweatLossMl(
                    item["tempC"].asDouble,
                    item["hours"].asDouble,
                    item["comfortC"].asDouble
                )
            )
        }
    }

    @Test
    fun hangoverVectorsMatch() {
        val hangover = root["hangover"]?.asJsonArray ?: return
        hangover.forEach { element ->
            val item = element.asJsonObject
            val peak = item["peakBAC"].asDouble
            val hours = item["durationHours"].asDouble
            val water = item["waterGlasses"].asDouble
            val count = item["drinksCount"].asInt

            val level = HangoverPredictor.predict(peak, hours, water, count)
            assertEquals(item["level"].asInt, level.ordinal, "level at peak $peak")
            assertEquals(item["label"].asString, level.germanLabel, "label at peak $peak")
            assertEquals(item["isPositive"].asBoolean, level.isPositive, "isPositive at peak $peak")
            assertEquals(item["isLethal"].asBoolean, level.isLethal, "isLethal at peak $peak")
        }
    }

    @Test
    fun achievementsAll49VectorsMatch() {
        val achievements = root["achievements"]?.asJsonArray ?: return
        assertEquals(49, achievements.size(), "achievement count must be 49")

        val profile = Profile(weightKg = 75.0, heightCm = 180.0, age = 25, gender = Gender.MALE)
        val zone = ZoneId.of("UTC")

        achievements.forEach { element ->
            val item = element.asJsonObject
            val id = item["id"].asString

            // Test earned case
            val earnedDrinks = parseDrinks(item["earnedDrinks"]?.asJsonArray)
            val earnedStats = item["earnedStats"].asJsonObject
            val earnedCtx = AchievementCatalog.EvalContext(
                drinks = earnedDrinks,
                profile = profile,
                nowEpochSeconds = 1735763400L,
                installDateEpochSeconds = 1735763400L,
                zone = zone,
                customPeakDayBAC = earnedStats["peakBAC"].asDouble,
                customSoberStreak = earnedStats["streak"].asInt
            )
            val isEarned = AchievementCatalog.isEarned(
                id = id,
                drinks = earnedDrinks,
                hasCustomTemplate = earnedStats["hasCustom"].asBoolean,
                crewCount = earnedStats["crewCount"].asInt,
                photoCount = earnedStats["photoCount"].asInt,
                jamsCreated = earnedStats["jamsCreated"].asInt,
                cache = earnedCtx
            )
            assertTrue(isEarned, "$id earned case failed")

            // Test not-earned case
            val notDrinks = parseDrinks(item["notDrinks"]?.asJsonArray)
            val notStats = item["notStats"].asJsonObject
            val notCtx = AchievementCatalog.EvalContext(
                drinks = notDrinks,
                profile = profile,
                nowEpochSeconds = 1735763400L,
                installDateEpochSeconds = 1735763400L,
                zone = zone,
                customPeakDayBAC = notStats["peakBAC"].asDouble,
                customSoberStreak = notStats["streak"].asInt
            )
            val isNotEarned = AchievementCatalog.isEarned(
                id = id,
                drinks = notDrinks,
                hasCustomTemplate = notStats["hasCustom"].asBoolean,
                crewCount = notStats["crewCount"].asInt,
                photoCount = notStats["photoCount"].asInt,
                jamsCreated = notStats["jamsCreated"].asInt,
                cache = notCtx
            )
            assertFalse(isNotEarned, "$id not-earned case failed")
        }
    }

    @Test
    fun statusSkinVectorsMatch() {
        val statusSkins = root["statusSkin"]?.asJsonArray ?: return
        assertEquals(50, statusSkins.size(), "statusSkin count must be 50")

        statusSkins.forEach { element ->
            val item = element.asJsonObject
            val skinRaw = item["skin"].asString
            val statusRaw = item["status"].asString
            val expected = item["expectedLabel"].asString

            val skin = StatusSkin.entries.first { it.raw == skinRaw }
            val status = when (statusRaw) {
                "sober" -> BacStatus.SOBER
                "tipsy" -> BacStatus.TIPSY
                "drunk" -> BacStatus.DRUNK
                "careful" -> BacStatus.CAREFUL
                "danger" -> BacStatus.DANGER
                else -> fail("unknown status $statusRaw")
            }

            assertEquals(expected, status.label(skin), "label for skin $skinRaw status $statusRaw")
        }
    }

    @Test
    fun mixersDatabaseVectorsMatch() {
        val mixers = root["mixers"]?.asJsonObject ?: return
        val allMixers = mixers["all"].asJsonArray
        assertEquals(55, allMixers.size(), "mixer count must be 55")

        allMixers.forEach { element ->
            val item = element.asJsonObject
            val name = item["name"].asString
            val found = MixerDatabase.ALL.firstOrNull { it.name == name }
            assertNotNull(found, "mixer $name not found in Kotlin catalog")
            assertEquals(item["category"].asString, found.category.raw, "category for $name")
            assertEquals(item["caloriesPer100ml"].asInt, found.caloriesPer100ml, "calories for $name")
            near("waterContent for $name", item["waterContentPercent"].asDouble, found.waterContentPercent)
            assertEquals(item["icon"].asString, found.icon, "icon for $name")
        }

        // Search tests
        mixers["searches"]?.asJsonArray?.forEach {
            val searchObj = it.asJsonObject
            val q = searchObj["query"].asString
            val queryStr = when (q) {
                "empty" -> ""
                "unknown" -> "nonexistent_xyz"
                else -> q
            }
            val expectedNames = searchObj["results"].asJsonArray.map { e -> e.asString }
            val actualNames = MixerDatabase.search(queryStr).map { m -> m.name }
            assertEquals(expectedNames, actualNames, "search for '$q'")
        }
    }

    @Test
    fun logicalDayVectorsMatch() {
        val logicalDays = root["logicalDay"]?.asJsonArray ?: return
        val utc = ZoneId.of("UTC")

        logicalDays.forEach { element ->
            val item = element.asJsonObject
            val ts = item["epochSeconds"].asLong
            val expectedDate = item["logicalDateString"].asString
            val expectedStart = item["logicalDayStartEpoch"].asLong

            assertEquals(
                expectedDate,
                LogicalDay.dateOf(ts, utc).toString(),
                "dateOf at $ts"
            )
            assertEquals(
                expectedStart,
                LogicalDay.startOf(ts, utc),
                "startOf at $ts"
            )
        }
    }

    private fun parseDrinks(array: com.google.gson.JsonArray?): List<Drink> {
        if (array == null) return emptyList()
        return array.map {
            val o = it.asJsonObject
            Drink(
                name = o["name"].asString,
                volumeML = o["volumeML"].asDouble,
                abv = o["abv"].asDouble,
                category = DrinkCategory.from(o["category"].asString),
                timestampEpochSeconds = o["timestampEpoch"].asLong,
                mixerVolumeML = o["mixerVolume"]?.asDouble ?: 0.0
            )
        }
    }
}
