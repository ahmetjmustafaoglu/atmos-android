package dev.ahmedabusada.atmos.ui.format

import dev.ahmedabusada.atmos.domain.settings.Temperature
import dev.ahmedabusada.atmos.domain.settings.TemperatureUnit
import dev.ahmedabusada.atmos.domain.settings.WindSpeed
import dev.ahmedabusada.atmos.domain.settings.WindSpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.Bidi
import java.time.LocalDateTime
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.random.Random

/**
 * Hidden tests for task 001 (see ../ISSUE.md). They check what a reader **sees**, not which
 * characters the code adds: the oracle is the Unicode Bidirectional Algorithm in the JDK
 * (`java.text.Bidi`, which knows isolates since JDK 21). So any correct fix passes (isolates,
 * direction marks, another rule), and a fix for the reported sentence only fails.
 */
class ValueOrderHiddenTest {
    private val arabic24 = WeatherFormatter(Locale.forLanguageTag("ar"), use24HourClock = true)
    private val arabic12 = WeatherFormatter(Locale.forLanguageTag("ar"), use24HourClock = false)

    /** The app's real Arabic sentences that carry values, from the resources. */
    private val sentences: Map<String, String> = run {
        val file = listOf("src/main/res/values-ar/strings.xml", "app/src/main/res/values-ar/strings.xml")
            .map(::File).first { it.exists() }
        val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            .getElementsByTagName("string")
        (0 until strings.length).associate { i ->
            val node = strings.item(i)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    @Test
    fun `the reported sentence shows the temperature as it is written`() {
        val value = arabic24.temperature(Temperature(11.0), TemperatureUnit.Celsius)
        assertReadsNaturally("home_feels_like", value)
    }

    @Test
    fun `every value reads as it is written, in every Arabic sentence that carries one`() {
        val seed = System.getenv("TASK_SEED")?.toLongOrNull() ?: System.nanoTime() % 1_000_000
        val random = Random(seed)
        val problems = mutableListOf<String>()
        repeat(400) {
            val celsius = random.nextInt(-60, 61).toDouble()
            val wind = random.nextDouble(0.0, 150.0)
            val percent = random.nextInt(0, 101)
            val time = LocalDateTime.of(2026, 11, 11, random.nextInt(0, 24), random.nextInt(0, 60))
            val unit = TemperatureUnit.entries[random.nextInt(TemperatureUnit.entries.size)]
            val windUnit = WindSpeedUnit.entries[random.nextInt(WindSpeedUnit.entries.size)]
            val format = if (random.nextBoolean()) arabic12 else arabic24
            val cases = listOf(
                "home_feels_like" to listOf(format.temperature(Temperature(celsius), unit)),
                "home_humidity" to listOf(format.percentage(percent)),
                "home_precipitation_chance" to listOf(format.percentage(percent)),
                "home_wind" to listOf(format.windSpeed(WindSpeed(wind), windUnit)),
                "home_min_max" to listOf(
                    format.temperature(Temperature(celsius), unit),
                    format.temperature(Temperature(celsius + 7), unit),
                ),
                "home_sunrise_sunset" to listOf(format.hour(time), format.hour(time.plusHours(10))),
            )
            for ((name, values) in cases) {
                problem(name, values)?.let(problems::add)
            }
        }
        assertTrue("Replay with TASK_SEED=$seed. ${problems.size} wrong, e.g. ${problems.take(5)}", problems.isEmpty())
    }

    @Test
    fun `the formatted text itself is unchanged apart from invisible marks`() {
        assertEquals("15°", plain(arabic24.temperature(Temperature(14.9), TemperatureUnit.Celsius)))
        assertEquals("25 km/h", plain(arabic24.windSpeed(WindSpeed(24.8), WindSpeedUnit.KilometersPerHour)))
        assertEquals("69%", plain(arabic24.percentage(69)))
        assertEquals("14:00", plain(arabic24.hour(LocalDateTime.of(2026, 11, 11, 14, 0))))
    }

    private fun assertReadsNaturally(name: String, vararg values: String) {
        problem(name, values.toList())?.let { throw AssertionError(it) }
    }

    /** Null when every value in the sentence looks the same as it does on its own. */
    private fun problem(name: String, values: List<String>): String? {
        val template = sentences.getValue(name)
        val sentence = values.foldIndexed(template) { i, text, value -> text.replace("%${i + 1}\$s", value) }
        val shown = visual(sentence, rightToLeft = true)
        val missing = values.map(::alone).filterNot(shown::contains)
        return if (missing.isEmpty()) null else "$name: ${missing.joinToString()} is shown as \"$shown\""
    }

    /** How a value looks on its own: in its own direction, from its first letter (left to right if none). */
    private fun alone(value: String): String {
        val text = plain(value)
        val first = text.firstOrNull { Character.getDirectionality(it) in STRONG }
        val rightToLeft = first != null && Character.getDirectionality(first) != Character.DIRECTIONALITY_LEFT_TO_RIGHT
        return visual(text, rightToLeft)
    }

    /** The order the characters appear on screen, from left to right (the oracle). */
    private fun visual(text: String, rightToLeft: Boolean): String {
        val bidi = Bidi(text, if (rightToLeft) Bidi.DIRECTION_RIGHT_TO_LEFT else Bidi.DIRECTION_LEFT_TO_RIGHT)
        val levels = ByteArray(bidi.runCount) { bidi.getRunLevel(it).toByte() }
        val runs = Array<Any>(bidi.runCount) { i ->
            val run = text.substring(bidi.getRunStart(i), bidi.getRunLimit(i))
            if (bidi.getRunLevel(i) % 2 == 1) run.reversed() else run
        }
        Bidi.reorderVisually(levels, 0, runs, 0, runs.size)
        return plain(runs.joinToString(""))
    }

    private fun plain(text: String) = text.filterNot { it in MARKS }

    private companion object {
        /** Invisible direction marks and isolates: never shown, so ignored when comparing. */
        const val MARKS = "‎‏؜⁦⁧⁨⁩‪‫‬‭‮"
        val STRONG = setOf(
            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
        )
    }
}
