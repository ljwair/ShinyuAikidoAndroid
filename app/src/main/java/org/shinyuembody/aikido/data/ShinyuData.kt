package org.shinyuembody.aikido.data

import java.time.DayOfWeek
import java.time.LocalDate

data class ClassSession(
    val day: DayOfWeek,
    val city: String,
    val time: String,
    val venue: String,
    val address: String,
    val subtitle: String = "Regular Aikido practice"
)

data class SpecialSession(
    val date: LocalDate,
    val title: String,
    val detail: String,
    val joinUrl: String? = null,
    val meetingId: String? = null,
    val passcode: String? = null
)

data class SitePage(
    val title: String,
    val description: String,
    val url: String
)

data class AikiTheme(
    val title: String,
    val focus: String,
    val practicePrompt: String
)

object ShinyuData {
    const val website = "https://shinyuembody.org"
    const val email = "info@shinyuembody.org"
    const val phone = "+31618144004"
    const val classPass = "https://classpass.com/refer/JBPC499K10"

    // Bundled fallback only. The Home screen normally refreshes this from /Aiki-theme.
    val weeklyAikiTheme = AikiTheme(
        title = "Ura",
        focus = "Work with the direction of movement rather than meeting it head-on.",
        practicePrompt = "Notice the ura behaviours. Try to identify when you apply ura."
    )

    /**
     * Bundled special-session fallback. The app first checks the live Cloudflare/Zoom feed
     * and only uses these entries when no live meeting data is available.
     *
     * Friday 4 September Aiki-Stretching is at 08:30 Amsterdam time,
     * for 30 minutes, online from home.
     */
    val specialSessions = listOf(
        SpecialSession(
            date = LocalDate.of(2026, 9, 4),
            title = "Aiki-Stretching",
            detail = "08:30 • 30 min • Online from home",
            joinUrl = "https://us02web.zoom.us/j/84839045940",
            meetingId = "848 3904 5940",
            passcode = "907838"
        )
    )

    val classes = listOf(
        ClassSession(
            DayOfWeek.MONDAY,
            "The Hague",
            "18:00 - 19:00",
            "Grote Pyr",
            "Waldeck Pyrmontkade 115, The Hague, Netherlands"
        ),
        ClassSession(
            DayOfWeek.TUESDAY,
            "Amsterdam",
            "17:30 - 19:00",
            "Sporthallen Zuid",
            "Burgerweeshuispad 54, 1076 EP Amsterdam, Netherlands"
        ),
        ClassSession(
            DayOfWeek.THURSDAY,
            "Amsterdam",
            "17:30 - 19:00",
            "Sporthallen Zuid",
            "Burgerweeshuispad 54, 1076 EP Amsterdam, Netherlands"
        ),
        ClassSession(
            DayOfWeek.FRIDAY,
            "The Hague",
            "17:30 - 18:30",
            "Grote Pyr",
            "Waldeck Pyrmontkade 115, The Hague, Netherlands"
        )
    )

    val explore = listOf(
        SitePage(
            "Aikido Practice",
            "What Aikido is, how we train, and how to begin.",
            "$website/aikido-practice"
        ),
        SitePage(
            "Teaching Team",
            "Meet the Shinyu instructors and assistants.",
            "$website/teaching-team"
        ),
        SitePage(
            "Aikido Events",
            "Seminars, special trainings and current event information.",
            "$website/aikido-events"
        ),
        SitePage(
            "Aiki Theme",
            "The current weekly Aiki training theme.",
            "$website/aiki-theme"
        ),
        SitePage(
            "Aiki Leadership Lab",
            "Experiential leadership practice using Aikido principles.",
            "$website/aiki-leadership-lab"
        ),
        SitePage(
            "The Samurai Game®",
            "Leadership, responsibility and systems under pressure.",
            "$website/the-samurai-gamer"
        ),
        SitePage(
            "Shinyu Merch",
            "T-shirts, mugs and other Shinyu items.",
            "$website/shinyu-merch"
        )
    )
}
