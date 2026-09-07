package org.shinyuembody.aikido.data

import android.content.Context
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import java.util.Locale

/**
 * Loads the pieces of the Shinyu website that are useful on the native Home screen.
 *
 * The website is the source of truth. Successful responses are persisted in app storage,
 * so the last known website content is still available when the phone is temporarily offline.
 */
object LiveContentRepository {
    const val AIKI_THEME_URL = "https://www.shinyuembody.org/Aiki-theme"
    private val AIKI_THEME_URL_CANDIDATES = listOf(
        "https://www.shinyuembody.org/Aiki-theme",
        "https://www.shinyuembody.org/aiki-theme",
        "https://shinyuembody.org/Aiki-theme",
        "https://shinyuembody.org/aiki-theme"
    )
    const val SCHEDULE_URL = "https://www.shinyuembody.org/aikido-schedule"
    const val EVENTS_URL = "https://www.shinyuembody.org/aikido-events"
    const val LIVE_UPDATES_URL = "https://lingering-violet-0685.shinyu-leadership.workers.dev/"

    data class LiveEvent(
        val title: String,
        val detail: String
    )

    data class HomeContent(
        val theme: AikiTheme,
        val classes: List<ClassSession>,
        val latestEvent: LiveEvent?,
        val specialSessions: List<SpecialSession>,
        val themeFromNetwork: Boolean,
        val scheduleFromNetwork: Boolean,
        val eventFromNetwork: Boolean,
        val liveUpdatesFromNetwork: Boolean,
        val themeUrl: String
    )

    private data class CachedPage(
        val html: String?,
        val fromNetwork: Boolean,
        val resolvedUrl: String? = null
    )
    private data class HtmlText(val text: String, val endIndex: Int)

    suspend fun load(context: Context): HomeContent = coroutineScope {
        val appContext = context.applicationContext
        val themeDeferred = async(Dispatchers.IO) {
            loadFirstAvailablePage(
                appContext,
                AIKI_THEME_URL_CANDIDATES,
                "aiki_theme_live.html",
                "aiki_theme_live_url.txt"
            )
        }
        val scheduleDeferred = async(Dispatchers.IO) {
            loadPage(appContext, SCHEDULE_URL, "aikido_schedule_live.html")
        }
        val eventDeferred = async(Dispatchers.IO) {
            loadPage(appContext, EVENTS_URL, "aikido_events_live.html")
        }
        val liveUpdatesDeferred = async(Dispatchers.IO) {
            loadRawPage(appContext, LIVE_UPDATES_URL, "shinyu_app_live.json", "application/json,text/plain,*/*")
        }

        val themePage = themeDeferred.await()
        val schedulePage = scheduleDeferred.await()
        val eventPage = eventDeferred.await()
        val liveUpdatesPage = liveUpdatesDeferred.await()

        val theme = themePage.html?.let(::parseTheme) ?: ShinyuData.weeklyAikiTheme
        val parsedClasses = schedulePage.html?.let(::parseSchedule).orEmpty()
        val classes = if (parsedClasses.isNotEmpty()) parsedClasses else ShinyuData.classes
        val event = eventPage.html?.let(::parseLatestEvent)
        val remoteSpecialSessions = liveUpdatesPage.html?.let(::parseLiveSessions).orEmpty()
        val fallbackSpecialSessions = ShinyuData.specialSessions.filter { !it.date.isBefore(LocalDate.now()) }
        val specialSessions = (if (remoteSpecialSessions.isNotEmpty()) remoteSpecialSessions else fallbackSpecialSessions)
            .filter { !it.date.isBefore(LocalDate.now()) }
            .sortedBy { it.date }

        HomeContent(
            theme = theme,
            classes = classes,
            latestEvent = event,
            specialSessions = specialSessions,
            themeFromNetwork = themePage.fromNetwork,
            scheduleFromNetwork = schedulePage.fromNetwork,
            eventFromNetwork = eventPage.fromNetwork,
            liveUpdatesFromNetwork = liveUpdatesPage.fromNetwork && remoteSpecialSessions.isNotEmpty(),
            themeUrl = themePage.resolvedUrl ?: AIKI_THEME_URL
        )
    }

    private suspend fun loadFirstAvailablePage(
        context: Context,
        urls: List<String>,
        cacheName: String,
        urlCacheName: String
    ): CachedPage = withContext(Dispatchers.IO) {
        val cacheFile = File(context.filesDir, cacheName)
        val urlCacheFile = File(context.filesDir, urlCacheName)

        // Try the last known working URL first, then all canonical variants.
        val remembered = runCatching { urlCacheFile.readText().trim() }
            .getOrNull()
            ?.takeIf { it.startsWith("https://") }
        val candidates = listOfNotNull(remembered) + urls.filterNot { it == remembered }

        for (candidate in candidates.distinct()) {
            val online = runCatching { download(candidate) }.getOrNull()?.takeIf { it.length > 200 }
            if (online != null) {
                runCatching { cacheFile.writeText(online) }
                runCatching { urlCacheFile.writeText(candidate) }
                return@withContext CachedPage(online, true, candidate)
            }
        }

        val cachedHtml = runCatching { cacheFile.readText() }.getOrNull()
        val cachedUrl = remembered ?: urls.firstOrNull()
        CachedPage(cachedHtml, false, cachedUrl)
    }

    private suspend fun loadPage(context: Context, url: String, cacheName: String): CachedPage =
        withContext(Dispatchers.IO) {
            val cacheFile = File(context.filesDir, cacheName)
            val online = runCatching { download(url) }.getOrNull()?.takeIf { it.length > 200 }
            if (online != null) {
                runCatching { cacheFile.writeText(online) }
                CachedPage(online, true)
            } else {
                CachedPage(runCatching { cacheFile.readText() }.getOrNull(), false)
            }
        }

    private suspend fun loadRawPage(
        context: Context,
        url: String,
        cacheName: String,
        accept: String
    ): CachedPage = withContext(Dispatchers.IO) {
        val cacheFile = File(context.filesDir, cacheName)
        val online = runCatching { download(url, accept) }.getOrNull()?.takeIf { it.length > 20 }
        if (online != null) {
            runCatching { cacheFile.writeText(online) }
            CachedPage(online, true)
        } else {
            CachedPage(runCatching { cacheFile.readText() }.getOrNull(), false)
        }
    }

    private fun download(url: String, accept: String = "text/html,application/xhtml+xml"): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 6500
            connection.readTimeout = 6500
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android) ShinyuAikido/1.3"
            )
            connection.setRequestProperty("Accept", accept)
            connection.connect()
            if (connection.responseCode !in 200..299) {
                error("HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Parse /Aiki-theme without depending on Hostinger's exact element types.
     *
     * Older versions required the theme title to be inside an h1-h6 tag. Hostinger embed/code
     * blocks can render visible text in div/span elements instead, which caused the app to fall
     * back to the bundled theme. We now try headings first, then the complete visible-text stream.
     */
    private fun parseTheme(html: String): AikiTheme? {
        val headings = extractTags(html, "h[1-6]")
            .filter { it.text.length in 2..120 }

        val markerIndex = headings.indexOfFirst(::isThemeMarker)
        val marker = headings.getOrNull(markerIndex)
        val afterMarker = if (markerIndex >= 0) headings.drop(markerIndex + 1) else headings
        val titleNode = afterMarker.firstOrNull { isUsefulThemeHeading(it.text) }
            ?: headings.firstOrNull { isUsefulThemeHeading(it.text) }

        if (titleNode != null) {
            var title = normalizeThemeTitle(titleNode.text)
            val paragraphStart = maxOf(titleNode.endIndex, marker?.endIndex ?: 0)
            val candidates = (
                extractTags(html.substring(paragraphStart), "p").map { it.text } +
                    visibleLines(html.substring(paragraphStart))
                )
                .filter(::isUsefulBodyText)
                .filterNot { it.equals(title, ignoreCase = true) }
                .distinct()

            return buildTheme(title, candidates)
        }

        // Hostinger embed/code blocks may not expose semantic heading tags at all.
        val lines = visibleLines(html)
            .filter { it.length <= 500 }
            .distinct()
        if (lines.isEmpty()) return null

        val visibleMarkerIndex = lines.indexOfFirst(::isThemeMarker)
        val searchStart = if (visibleMarkerIndex >= 0) visibleMarkerIndex + 1 else 0

        val titleIndex = (searchStart until lines.size).firstOrNull { index ->
            isUsefulThemeHeading(lines[index]) && !isUsefulBodyText(lines[index])
        } ?: (searchStart until lines.size).firstOrNull { index ->
            isUsefulThemeHeading(lines[index])
        } ?: return null

        val title = normalizeThemeTitle(lines[titleIndex])
        val candidates = lines.drop(titleIndex + 1)
            .filter(::isUsefulBodyText)
            .filterNot { it.equals(title, ignoreCase = true) }
            .take(12)

        return buildTheme(title, candidates)
    }

    private fun isThemeMarker(text: String): Boolean {
        val s = text.lowercase(Locale.ROOT)
        return s.contains("aiki theme") ||
            s.contains("theme of the week") ||
            s.contains("this week's") ||
            s.contains("this week’s")
    }

    private fun normalizeThemeTitle(raw: String): String {
        var title = raw.trim()
        if (title.contains(':') && title.contains("theme", ignoreCase = true)) {
            title = title.substringAfter(':').trim().ifBlank { title }
        }
        return title.take(120)
    }

    private fun buildTheme(title: String, candidates: List<String>): AikiTheme {
        val focus = candidates.firstOrNull()
            ?: "Open the weekly theme for this week's focus."
        val prompt = candidates.drop(1).firstOrNull {
            val s = it.lowercase(Locale.ROOT)
            listOf("practice", "notice", "try", "exercise", "observe", "this week", "explore", "reflect").any(s::contains)
        } ?: candidates.getOrNull(1) ?: focus

        return AikiTheme(
            title = title,
            focus = focus.take(320),
            practicePrompt = prompt.take(320)
        )
    }

    /**
     * Extracts active class rows from the live schedule. Struck-through HTML is removed first,
     * so temporarily cancelled summer/holiday rows are not treated as active classes.
     */
    private fun parseSchedule(html: String): List<ClassSession> {
        val activeHtml = html
            .replace(Regex("(?is)<(?:s|del)\\b[^>]*>.*?</(?:s|del)>") , " ")
            .replace(Regex("(?is)<span\\b[^>]*text-decoration\\s*:\\s*(?:line-through|strike)[^>]*>.*?</span>"), " ")

        val lines = visibleLines(activeHtml)
        val dayRegex = Regex("(?i)\\b(mon(?:day)?|tue(?:sday)?|wed(?:nesday)?|thu(?:rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)\\b")
        val timeRegex = Regex("\\b([01]?\\d|2[0-3]):[0-5]\\d\\s*[-–—]\\s*([01]?\\d|2[0-3]):[0-5]\\d\\b")
        val result = mutableListOf<ClassSession>()

        for (i in lines.indices) {
            val line = lines[i]
            val dayMatch = dayRegex.find(line) ?: continue
            val nearby = (i..minOf(lines.lastIndex, i + 2)).joinToString(" ") { lines[it] }
            val timeMatch = timeRegex.find(nearby) ?: continue
            val day = parseDay(dayMatch.value) ?: continue

            val windowStart = maxOf(0, i - 12)
            val windowEnd = minOf(lines.lastIndex, i + 3)
            val window = lines.subList(windowStart, windowEnd + 1)

            val city = window.lastOrNull {
                it.contains("Amsterdam", true) || it.contains("The Hague", true) || it.contains("Den Haag", true)
            }?.let {
                when {
                    it.contains("Amsterdam", true) -> "Amsterdam"
                    else -> "The Hague"
                }
            } ?: continue

            val venue = window.lastOrNull {
                it.contains("Sporthallen Zuid", true) ||
                    it.contains("Grote Pyr", true) ||
                    it.contains("Uilenstede", true) ||
                    it.contains("Enso", true)
            }?.let(::shortVenue) ?: if (city == "Amsterdam") "Sporthallen Zuid" else "Grote Pyr"

            val address = window.lastOrNull {
                Regex("\\d{2,5}").containsMatchIn(it) &&
                    (it.contains("Amsterdam", true) || it.contains("Hague", true) || it.contains("Den Haag", true) || it.contains("EP ", true))
            } ?: when (city) {
                "Amsterdam" -> "Burgerweeshuispad 54, 1076 EP Amsterdam, Netherlands"
                else -> "Waldeck Pyrmontkade 115, The Hague, Netherlands"
            }

            val normalizedTime = timeMatch.value
                .replace('–', '-')
                .replace('—', '-')
                .replace(Regex("\\s*-\\s*"), " - ")

            result += ClassSession(
                day = day,
                city = city,
                time = normalizedTime,
                venue = venue,
                address = address,
                subtitle = "Live from shinyuembody.org"
            )
        }

        return result
            .distinctBy { Triple(it.day, it.city, it.time) }
            .sortedBy { dayOrder(it.day) }
    }


    private fun parseLiveSessions(json: String): List<SpecialSession> {
        return runCatching {
            val root = JSONObject(json)

            // Current live source: Cloudflare Worker backed directly by Zoom.
            root.optJSONArray("meetings")?.let { meetings ->
                return@runCatching buildList {
                    for (index in 0 until meetings.length()) {
                        val item = meetings.optJSONObject(index) ?: continue
                        val title = item.optString("title").trim()
                        if (title.isBlank()) continue

                        val startTime = item.optString("startTime").trim()
                        if (startTime.isBlank()) continue

                        val zone = runCatching {
                            ZoneId.of(item.optString("timezone").trim().ifBlank { "Europe/Amsterdam" })
                        }.getOrDefault(ZoneId.of("Europe/Amsterdam"))

                        val zonedStart = runCatching {
                            Instant.parse(startTime).atZone(zone)
                        }.recoverCatching {
                            OffsetDateTime.parse(startTime).atZoneSameInstant(zone)
                        }.getOrNull() ?: continue

                        val date = zonedStart.toLocalDate()
                        val time = zonedStart.format(DateTimeFormatter.ofPattern("HH:mm"))
                        val duration = item.optInt("durationMinutes", 0)

                        val detailParts = mutableListOf(time)
                        if (duration > 0) detailParts += "$duration min"
                        detailParts += "Zoom"

                        val meetingId = when (val id = item.opt("id")) {
                            null, JSONObject.NULL -> null
                            else -> id.toString().trim().takeIf { it.isNotBlank() }
                        }

                        add(
                            SpecialSession(
                                date = date,
                                title = title,
                                detail = detailParts.joinToString(" • "),
                                joinUrl = item.optString("joinUrl").trim().takeIf { it.startsWith("https://") },
                                meetingId = meetingId,
                                passcode = null
                            )
                        )
                    }
                }
            }

            // Backward compatibility with the original manually hosted JSON feed.
            val sessions = root.optJSONArray("sessions") ?: return@runCatching emptyList()
            buildList {
                for (index in 0 until sessions.length()) {
                    val item = sessions.optJSONObject(index) ?: continue
                    val date = runCatching { LocalDate.parse(item.optString("date")) }.getOrNull() ?: continue
                    val title = item.optString("title").trim()
                    if (title.isBlank()) continue
                    val detailParts = mutableListOf<String>()
                    item.optString("time").trim().takeIf { it.isNotBlank() }?.let(detailParts::add)
                    item.optInt("durationMinutes", 0).takeIf { it > 0 }?.let { detailParts += "$it min" }
                    item.optString("location").trim().takeIf { it.isNotBlank() }?.let(detailParts::add)
                    val detail = item.optString("detail").trim().ifBlank {
                        detailParts.joinToString(" • ").ifBlank { "Live Shinyu session" }
                    }
                    add(
                        SpecialSession(
                            date = date,
                            title = title,
                            detail = detail,
                            joinUrl = item.optString("joinUrl").trim().takeIf { it.startsWith("https://") },
                            meetingId = item.optString("meetingId").trim().takeIf { it.isNotBlank() },
                            passcode = item.optString("passcode").trim().takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseLatestEvent(html: String): LiveEvent? {
        val headings = extractTags(html, "h[1-6]")
        val markerIndex = headings.indexOfFirst {
            it.text.equals("Aikido Events", true) ||
                it.text.contains("Aikido events", true) ||
                it.text.equals("Events", true)
        }
        val candidates = if (markerIndex >= 0) headings.drop(markerIndex + 1) else headings
        val eventHeading = candidates.firstOrNull { h ->
            val s = h.text.trim()
            s.length in 4..120 &&
                !s.equals("Aikido", true) &&
                !s.equals("Schedule", true) &&
                !s.contains("Contact", true) &&
                !s.contains("Shinyu Aikido", true) &&
                !s.contains("Join", true) &&
                !s.contains("Teaching team", true)
        } ?: return null

        val detail = extractTags(html.substring(eventHeading.endIndex), "p")
            .map { it.text }
            .firstOrNull(::isUsefulBodyText)
            ?.take(220)
            ?: "Open the live events page for details."

        return LiveEvent(eventHeading.text.take(120), detail)
    }

    private fun extractTags(html: String, tagNamePattern: String): List<HtmlText> {
        val regex = Regex("(?is)<($tagNamePattern)\\b[^>]*>(.*?)</\\1>")
        return regex.findAll(html).mapNotNull { match ->
            val cleaned = cleanText(match.groupValues[2])
            if (cleaned.isBlank()) null else HtmlText(cleaned, match.range.last + 1)
        }.toList()
    }

    private fun visibleLines(html: String): List<String> {
        val noScripts = html
            .replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style\\b[^>]*>.*?</style>"), " ")
            .replace(Regex("(?is)<!--.*?-->"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(?:p|div|li|h[1-6]|section|article|tr|td|th)>"), "\n")
        val decoded = Html.fromHtml(noScripts, Html.FROM_HTML_MODE_LEGACY).toString()
        return decoded.lineSequence()
            .map { it.replace('\u00a0', ' ').trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun cleanText(fragment: String): String = Html.fromHtml(
        fragment.replace(Regex("(?is)<[^>]+>"), " "),
        Html.FROM_HTML_MODE_LEGACY
    ).toString().replace('\u00a0', ' ').trim().replace(Regex("\\s+"), " ")

    private fun isUsefulThemeHeading(text: String): Boolean {
        val s = text.trim()
        if (s.length !in 2..100) return false
        val lower = s.lowercase(Locale.ROOT)
        val banned = listOf(
            "aiki theme", "aiki themes", "this week's aiki theme", "theme of the week",
            "shinyu", "contact", "aikido", "menu", "practice this week"
        )
        return banned.none { lower == it } &&
            !lower.startsWith("contact") &&
            !lower.startsWith("shinyu aikido")
    }

    private fun isUsefulBodyText(text: String): Boolean {
        val s = text.trim()
        if (s.length !in 12..500) return false
        val lower = s.lowercase(Locale.ROOT)
        return !lower.contains("cookie") &&
            !lower.contains("privacy") &&
            !lower.contains("©") &&
            !lower.contains("info@shinyu") &&
            !lower.contains("+31") &&
            !lower.startsWith("contact") &&
            !lower.startsWith("shinyu enhancing")
    }

    private fun parseDay(value: String): DayOfWeek? = when (value.lowercase(Locale.ROOT).take(3)) {
        "mon" -> DayOfWeek.MONDAY
        "tue" -> DayOfWeek.TUESDAY
        "wed" -> DayOfWeek.WEDNESDAY
        "thu" -> DayOfWeek.THURSDAY
        "fri" -> DayOfWeek.FRIDAY
        "sat" -> DayOfWeek.SATURDAY
        "sun" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun shortVenue(value: String): String = when {
        value.contains("Sporthallen Zuid", true) -> "Sporthallen Zuid"
        value.contains("Grote Pyr", true) -> "Grote Pyr"
        value.contains("Uilenstede", true) -> "Uilenstede"
        value.contains("Enso", true) -> "Enso Aikido"
        else -> value.take(80)
    }

    private fun dayOrder(day: DayOfWeek): Int = when (day) {
        DayOfWeek.MONDAY -> 1
        DayOfWeek.TUESDAY -> 2
        DayOfWeek.WEDNESDAY -> 3
        DayOfWeek.THURSDAY -> 4
        DayOfWeek.FRIDAY -> 5
        DayOfWeek.SATURDAY -> 6
        DayOfWeek.SUNDAY -> 7
    }
}