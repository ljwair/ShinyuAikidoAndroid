package org.shinyuembody.aikido

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.shinyuembody.aikido.data.AikiTheme
import org.shinyuembody.aikido.data.ClassSession
import org.shinyuembody.aikido.data.ShinyuData
import org.shinyuembody.aikido.data.LiveContentRepository
import org.shinyuembody.aikido.data.SitePage
import org.shinyuembody.aikido.data.SpecialSession
import org.shinyuembody.aikido.ui.theme.Forest
import org.shinyuembody.aikido.ui.theme.Forest2
import org.shinyuembody.aikido.ui.theme.Sage
import org.shinyuembody.aikido.ui.theme.ShinyuTheme
import org.shinyuembody.aikido.trainer.AikidoTrainerView
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShinyuTheme {
                ShinyuApp()
            }
        }
    }
}

enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    SCHEDULE("Schedule", Icons.Default.Schedule),
    TRAINER("Trainer", Icons.Default.SportsMartialArts),
    EXPLORE("Explore", Icons.Default.Explore),
    CONTACT("Contact", Icons.Default.Email)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShinyuApp() {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var webPage by remember { mutableStateOf<SitePage?>(null) }
    var liveHome by remember { mutableStateOf<LiveContentRepository.HomeContent?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            liveHome = LiveContentRepository.load(context)
            delay(15 * 60 * 1000L)
        }
    }

    if (webPage != null) {
        WebPageScreen(page = webPage!!, onClose = { webPage = null })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.shinyu_logo_header),
                            contentDescription = "Shinyu Aikido logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Column(modifier = Modifier.padding(start = 10.dp)) {
                            Text("SHINYU AIKIDO", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Presence • Connection • Aikido", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Forest,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            AppTab.HOME -> HomeScreen(padding, liveHome, onOpenPage = { webPage = it }, onSchedule = { selectedTab = AppTab.SCHEDULE })
            AppTab.SCHEDULE -> ScheduleScreen(padding, liveHome)
            AppTab.TRAINER -> TrainerScreen(padding)
            AppTab.EXPLORE -> ExploreScreen(padding, onOpenPage = { webPage = it })
            AppTab.CONTACT -> ContactScreen(padding)
        }
    }
}

@Composable
private fun HomeScreen(
    padding: PaddingValues,
    liveHome: LiveContentRepository.HomeContent?,
    onOpenPage: (SitePage) -> Unit,
    onSchedule: () -> Unit
) {
    val context = LocalContext.current
    val classes = liveHome?.classes ?: ShinyuData.classes
    val theme = liveHome?.theme ?: ShinyuData.weeklyAikiTheme
    val today = LocalDate.now().dayOfWeek
    val todayClasses = classes.filter { it.day == today }
    val nextClass = findNextClass(classes)
    val specialSessions = liveHome?.specialSessions ?: ShinyuData.specialSessions.filter { !it.date.isBefore(LocalDate.now()) }
    val nextSpecial = specialSessions.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                color = Forest,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Find your centre.\nMove with connection.",
                        color = Color.White,
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Shinyu Aikido in Amsterdam and The Hague.",
                        color = Color.White.copy(alpha = 0.86f),
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = { openExternal(context, ShinyuData.classPass) }) {
                        Text("Try a class")
                    }
                }
            }
        }

        if (nextSpecial != null) {
            item {
                SectionTitle("Live schedule")
                SpecialSessionCard(
                    session = nextSpecial,
                    context = context,
                    isLive = liveHome?.liveUpdatesFromNetwork == true,
                    compact = true
                )
            }
        }

        item {
            SectionTitle("Training now")
            if (todayClasses.isNotEmpty()) {
                todayClasses.forEach { CompactClassCard(it, if (liveHome?.scheduleFromNetwork == true) "TODAY • LIVE" else "TODAY") }
            } else if (nextClass != null) {
                CompactClassCard(nextClass, if (liveHome?.scheduleFromNetwork == true) "NEXT • LIVE" else "NEXT CLASS")
            }
            TextButton(onClick = onSchedule) {
                Text("See full weekly schedule")
            }
        }

        item {
            WeeklyAikiThemeCard(
                theme = theme,
                isLive = liveHome?.themeFromNetwork == true,
                onLearnMore = {
                    onOpenPage(
                        SitePage(
                            title = "Aiki Theme",
                            description = "The current weekly Aiki training theme.",
                            url = liveHome?.themeUrl ?: LiveContentRepository.AIKI_THEME_URL
                        )
                    )
                }
            )
        }

        liveHome?.latestEvent?.let { event ->
            item {
                SectionTitle("Latest Aikido update")
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onOpenPage(ShinyuData.explore.first { it.title == "Aikido Events" })
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(event.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                            if (liveHome?.eventFromNetwork == true) {
                                Surface(color = Sage, shape = RoundedCornerShape(9.dp)) {
                                    Text("LIVE", color = Forest, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(event.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            }
        }

        item {
            SectionTitle("Explore Shinyu")
            QuickLink(
                icon = Icons.Default.Event,
                title = "Aikido events",
                subtitle = "Seminars and special training",
                onClick = { onOpenPage(ShinyuData.explore.first { it.title == "Aikido Events" }) }
            )
            QuickLink(
                icon = Icons.Default.SportsMartialArts,
                title = "Aikido practice",
                subtitle = "Principles, training and how to begin",
                onClick = { onOpenPage(ShinyuData.explore.first { it.title == "Aikido Practice" }) }
            )
            QuickLink(
                icon = Icons.Default.MenuBook,
                title = "Aiki Leadership Lab",
                subtitle = "Embodied leadership through Aikido principles",
                onClick = { onOpenPage(ShinyuData.explore.first { it.title == "Aiki Leadership Lab" }) }
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Shinyu", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("Enhancing life through presence, connection and Aikido.")
                }
            }
        }
    }
}

@Composable
private fun WeeklyAikiThemeCard(
    theme: AikiTheme,
    isLive: Boolean,
    onLearnMore: () -> Unit
) {
    Column {
        SectionTitle("This week's Aiki Theme")
        Card(
            colors = CardDefaults.cardColors(containerColor = Sage),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        theme.title,
                        color = Forest,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (isLive) {
                        Surface(color = Color.White.copy(alpha = 0.7f), shape = RoundedCornerShape(9.dp)) {
                            Text("LIVE", color = Forest, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    theme.focus,
                    color = Forest2,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = Color.White.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "Practice this week",
                            color = Forest,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            theme.practicePrompt,
                            color = Forest,
                            fontSize = 15.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
                TextButton(onClick = onLearnMore) {
                    Text("Open this week's Aiki Theme")
                }
            }
        }
    }
}

@Composable
private fun ScheduleScreen(padding: PaddingValues, liveHome: LiveContentRepository.HomeContent?) {
    val context = LocalContext.current
    val classes = liveHome?.classes ?: ShinyuData.classes
    val specialSessions = liveHome?.specialSessions ?: ShinyuData.specialSessions.filter { !it.date.isBefore(LocalDate.now()) }
    val grouped = classes.groupBy { it.day }
    val orderedDays = DayOfWeek.entries.filter { grouped.containsKey(it) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Schedule", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "Regular Aikido classes plus current live and special sessions.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (specialSessions.isNotEmpty()) {
            item {
                Text("Live & special sessions", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (liveHome?.liveUpdatesFromNetwork == true) "Live updates from shinyuembody.org" else "Current Shinyu update",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            specialSessions.forEach { session ->
                item { SpecialSessionCard(session, context, liveHome?.liveUpdatesFromNetwork == true, compact = false) }
            }
            item {
                Text("Weekly Aikido schedule", fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                Text(
                    if (liveHome?.scheduleFromNetwork == true) "Live schedule from shinyuembody.org" else "Regular classes in Amsterdam and The Hague.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            item {
                Text("Weekly Aikido schedule", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (liveHome?.scheduleFromNetwork == true) "Live schedule from shinyuembody.org" else "Regular classes in Amsterdam and The Hague.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        orderedDays.forEach { day ->
            grouped[day].orEmpty().forEach { session ->
                item { FullClassCard(session, context) }
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("New to Shinyu?", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Start with a trial class. No previous martial arts experience is required.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { openExternal(context, ShinyuData.classPass) }) {
                        Text("Book a trial")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreScreen(padding: PaddingValues, onOpenPage: (SitePage) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Explore", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "Live content from shinyuembody.org opens inside the app.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(ShinyuData.explore) { page ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenPage(page) },
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Language, null, tint = Forest, modifier = Modifier.size(28.dp))
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(page.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(page.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.OpenInBrowser, null)
                }
            }
        }
    }
}

@Composable
private fun TrainerScreen(padding: PaddingValues) {
    var trainerView by remember { mutableStateOf<AikidoTrainerView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            trainerView?.onHostPause()
            trainerView = null
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize().padding(padding),
        factory = { ctx ->
            AikidoTrainerView(ctx).also { view ->
                trainerView = view
                view.onHostResume()
            }
        },
        update = { view -> trainerView = view }
    )
}

@Composable
private fun ContactScreen(padding: PaddingValues) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Contact Shinyu", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Questions about classes, training or events? Get in touch.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            ActionCard(Icons.Default.Email, "Email", ShinyuData.email) {
                openExternal(context, "mailto:${ShinyuData.email}")
            }
        }
        item {
            ActionCard(Icons.Default.Call, "Call", "+31 6 18144004") {
                openExternal(context, "tel:${ShinyuData.phone}")
            }
        }
        item {
            ActionCard(Icons.Default.Language, "Website", "shinyuembody.org") {
                openExternal(context, ShinyuData.website)
            }
        }
        item {
            ActionCard(Icons.Default.Share, "Share Shinyu Aikido", "Send the Aikido club page to someone") {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Shinyu Aikido - ${ShinyuData.website}/aikido-practice")
                }
                context.startActivity(Intent.createChooser(intent, "Share Shinyu Aikido"))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebPageScreen(page: SitePage, onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.shinyu_logo_header),
                            contentDescription = "Shinyu Aikido logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Text(
                            page.title,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
                },
                actions = {
                    IconButton(onClick = { openExternal(context, page.url) }) {
                        Icon(Icons.Default.OpenInBrowser, "Open in browser")
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setSupportZoom(false)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val uri = request?.url ?: return false
                            return if (uri.host?.endsWith("shinyuembody.org") == true) {
                                false
                            } else {
                                openExternal(ctx, uri.toString())
                                true
                            }
                        }
                    }
                    loadUrl(page.url)
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun CompactClassCard(session: ClassSession, badge: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = Sage) {
                Text(badge, color = Forest, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text("${session.city} • ${session.time}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(session.venue, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FullClassCard(session: ClassSession, context: Context) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                session.day.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                fontWeight = FontWeight.Bold,
                color = Forest,
                fontSize = 15.sp
            )
            Text(session.city, fontWeight = FontWeight.Bold, fontSize = 23.sp)
            Text(session.time, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = Forest, modifier = Modifier.size(19.dp))
                Text("${session.venue} • ${session.address}", modifier = Modifier.padding(start = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { openMaps(context, session.address) }) {
                    Icon(Icons.Default.LocationOn, null)
                    Text(" Directions", modifier = Modifier.padding(start = 4.dp))
                }
                TextButton(onClick = { addToCalendar(context, session) }) {
                    Icon(Icons.Default.CalendarMonth, null)
                    Text(" Add to calendar", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun SpecialSessionCard(
    session: SpecialSession,
    context: Context,
    isLive: Boolean,
    compact: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Sage.copy(alpha = 0.58f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(if (compact) 16.dp else 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = Forest) {
                    Text(
                        relativeDateLabel(session.date),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Surface(color = Color.White.copy(alpha = 0.72f), shape = RoundedCornerShape(9.dp)) {
                    Text(
                        if (isLive) "LIVE" else "SPECIAL",
                        color = Forest,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(session.title, fontWeight = FontWeight.Bold, fontSize = if (compact) 20.sp else 22.sp, color = Forest)
            Text(session.detail, color = Forest2, fontSize = 15.sp)
            if (!compact && (!session.meetingId.isNullOrBlank() || !session.passcode.isNullOrBlank())) {
                Spacer(Modifier.height(8.dp))
                session.meetingId?.let { Text("Meeting ID: $it", color = Forest2, fontSize = 13.sp) }
                session.passcode?.let { Text("Passcode: $it", color = Forest2, fontSize = 13.sp) }
            }
            session.joinUrl?.let { url ->
                Spacer(Modifier.height(10.dp))
                Button(onClick = { openExternal(context, url) }) {
                    Icon(Icons.Default.VideoCall, null)
                    Text(" Join Zoom")
                }
            }
        }
    }
}

private fun relativeDateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "TODAY"
        today.plusDays(1) -> "TOMORROW"
        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)).uppercase(Locale.ENGLISH)
    }
}

@Composable
private fun QuickLink(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(46.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = Forest) }
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Icon(Icons.Default.OpenInBrowser, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Forest, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun findNextClass(classes: List<ClassSession>): ClassSession? {
    val now = LocalDateTime.now()
    return classes
        .map { session -> session to nextOccurrence(now, session) }
        .minByOrNull { it.second }
        ?.first
}

private fun nextOccurrence(now: LocalDateTime, session: ClassSession): LocalDateTime {
    val start = session.time.substringBefore(" - ")
    val time = LocalTime.parse(start)
    var date = now.toLocalDate().with(TemporalAdjusters.nextOrSame(session.day))
    var occurrence = LocalDateTime.of(date, time)
    if (!occurrence.isAfter(now)) {
        date = date.plusWeeks(1)
        occurrence = LocalDateTime.of(date, time)
    }
    return occurrence
}

private fun openExternal(context: Context, uri: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    }
}

private fun openMaps(context: Context, address: String) {
    val geo = Uri.parse("geo:0,0?q=${Uri.encode(address)}")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, geo)) }
        .onFailure { openExternal(context, "https://www.google.com/maps/search/?api=1&query=${Uri.encode(address)}") }
}

private fun addToCalendar(context: Context, session: ClassSession) {
    val now = LocalDateTime.now()
    val start = nextOccurrence(now, session)
    val startMillis = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val durationMinutes = when (session.time) {
        "17:30 - 19:00" -> 90L
        else -> 60L
    }
    val endMillis = start.plusMinutes(durationMinutes).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
        putExtra(CalendarContract.Events.TITLE, "Shinyu Aikido - ${session.city}")
        putExtra(CalendarContract.Events.EVENT_LOCATION, "${session.venue}, ${session.address}")
        putExtra(CalendarContract.Events.DESCRIPTION, "Regular Shinyu Aikido practice. ${ShinyuData.website}")
    }
    runCatching { context.startActivity(intent) }
}
