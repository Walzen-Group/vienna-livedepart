package com.walzengroup.viennadepart.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.PositionIndicatorState
import androidx.wear.compose.material.PositionIndicatorVisibility
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.walzengroup.viennadepart.AppViewModel
import com.walzengroup.viennadepart.R
import com.walzengroup.viennadepart.data.DeparturesRepository
import com.walzengroup.viennadepart.data.DeparturesUi
import com.walzengroup.viennadepart.data.DepartureUi
import com.walzengroup.viennadepart.data.DirectionPage
import com.walzengroup.viennadepart.data.PlatformGroup
import com.walzengroup.viennadepart.data.RouteRepository
import com.walzengroup.viennadepart.data.stops.PhysicalStop
import com.walzengroup.viennadepart.ui.common.Glyph
import com.walzengroup.viennadepart.ui.common.Loadable
import com.walzengroup.viennadepart.ui.common.MutedText
import com.walzengroup.viennadepart.ui.common.RowSurface
import com.walzengroup.viennadepart.ui.common.SquareBadge
import com.walzengroup.viennadepart.ui.theme.ModeColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun DeparturesScreen(stop: PhysicalStop, line: String, app: AppViewModel) {
    val context = LocalContext.current
    val repo = remember { DeparturesRepository() }
    LaunchedEffect(Unit) { app.ensureLoaded(context) }

    // The route the user is riding (empty until the live termini match a pattern).
    var chain by remember(line) { mutableStateOf<List<PhysicalStop>>(emptyList()) }
    var lineType by remember(line) { mutableStateOf<String?>(null) }
    // Stops to crown through: the matched chain if it includes this stop, else just it.
    val stops = remember(chain, stop.diva) {
        val idx = chain.indexOfFirst { it.diva == stop.diva }
        if (idx >= 0) chain else listOf(stop)
    }

    // Recreate the pager when the stop list resolves (empty -> full chain).
    key(stops) {
        val startIndex = stops.indexOfFirst { it.diva == stop.diva }.coerceAtLeast(0)
        val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { stops.size })
        val focusRequester = rememberActiveFocusRequester()
        var acc by remember { mutableFloatStateOf(0f) }
        var targetPage by remember { mutableIntStateOf(startIndex) }
        var everScrolled by remember { mutableStateOf(false) }
        // Chrome (background, header, star, position indicator) is persistent and lives
        // ABOVE the pager, so crowning between stops slides only the departures body over
        // a single fixed sheet — and no nested Scaffold steals the crown's focus.
        val lineColor = ModeColor.forLine(line, lineType)
        val ground = groundBrush(lerp(Color.Black, lineColor, 0.45f))
        val currentStop = stops[pagerState.currentPage.coerceIn(0, stops.lastIndex)]

        // Curved rotary position tracker that hugs the bezel (native Wear indicator).
        val stopIndicator = remember(stops.size) { StopIndicatorState(stops.size) }
        stopIndicator.current = pagerState.currentPage.coerceIn(0, stops.lastIndex)
        Scaffold(
            timeText = { TimeText() },
            positionIndicator = {
                if (stops.size > 1) {
                    PositionIndicator(
                        state = stopIndicator,
                        indicatorHeight = 50.dp,
                        indicatorWidth = 4.dp,
                        paddingHorizontal = 5.dp,
                    )
                }
            },
        ) {
            Box(Modifier.fillMaxSize().background(ground)) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(22.dp)) // clear the TimeText curve
                    Header(line, currentStop.name, lineColor)
                    VerticalPager(
                        state = pagerState,
                        userScrollEnabled = false, // crown-only; the inner list keeps touch-scroll
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            // The crown steps between stops (a detent slides to the neighbour).
                            .onRotaryScrollEvent { e ->
                                acc += e.verticalScrollPixels
                                val threshold = 60f
                                var moved = false
                                while (acc >= threshold) { targetPage = (targetPage + 1).coerceAtMost(stops.lastIndex); acc -= threshold; moved = true }
                                while (acc <= -threshold) { targetPage = (targetPage - 1).coerceAtLeast(0); acc += threshold; moved = true }
                                if (moved) { everScrolled = true; pagerState.requestScrollToPage(targetPage) }
                                true
                            }
                            .focusRequester(focusRequester)
                            .focusable(),
                    ) { page ->
                        val pageStop = stops[page]
                        // The starting stop loads automatically; stops reached by crowning show
                        // a reload button, so scrolling through the line fires no requests.
                        var load by remember(pageStop.diva) { mutableStateOf(!everScrolled) }
                        if (load) {
                            Loadable(
                                loader = { repo.departuresForLine(pageStop, line) },
                                refreshMs = 30_000,
                                key = pageStop.diva,
                            ) { ui ->
                                LaunchedEffect(ui.lineType) { lineType = ui.lineType }
                                val termini = ui.directions.map { it.label }
                                LaunchedEffect(termini) {
                                    if (chain.isEmpty() && termini.isNotEmpty()) {
                                        chain = RouteRepository.chainFor(context, line, termini)
                                    }
                                }
                                DeparturesBody(ui, app)
                            }
                        } else {
                            ReloadBody(onReload = { load = true })
                        }
                    }
                }
            }
        }
    }
}

/** Centered reload button shown for a crowned-to station until tapped (no request until then). */
@Composable
private fun ReloadBody(onReload: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(RowSurface)
                .clickable(onClick = onReload),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_refresh),
                contentDescription = "Load departures",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * The departures sub-page for one stop: direction tabs + the per-direction list, with
 * the favorite star at the bottom of this page (not the outer chrome). Background,
 * header, and rotary indicator are drawn by the parent.
 */
@Composable
private fun DeparturesBody(ui: DeparturesUi, app: AppViewModel) {
    val context = LocalContext.current
    val lineColor = ModeColor.forLine(ui.line, ui.lineType)
    val favorited = app.isFavorite(ui.line)
    val toggleFavorite = { app.toggleFavorite(context, ui.line, ui.lineType); Unit }

    if (ui.directions.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No upcoming departures", color = MutedText, fontSize = 13.sp)
            FavoriteStar(favorited, toggleFavorite)
        }
        return
    }

    // One page per direction; swipe flips H <-> R. Each page owns its list state so the
    // position of whichever direction is on screen survives swipes. The favorite star is
    // the last element of each direction's content (scroll to the end to reach it), so it
    // never occupies fixed screen space.
    val pagerState = rememberPagerState(pageCount = { ui.directions.size })
    val scrollStates = ui.directions.map { rememberScrollState() }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DirectionTabs(ui.directions, pagerState.currentPage) { idx ->
            scope.launch { pagerState.animateScrollToPage(idx) }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            DirectionList(ui.directions[page], scrollStates[page], lineColor, favorited, toggleFavorite)
        }
    }
}

/**
 * Position state for the native curved [PositionIndicator]: maps the current stop index
 * onto a 0..1 fraction and sizes the thumb to one stop's share of the line.
 */
private class StopIndicatorState(private val count: Int) : PositionIndicatorState {
    var current by mutableIntStateOf(0)

    override val positionFraction: Float
        get() = if (count <= 1) 0f else current.toFloat() / (count - 1)

    override fun sizeFraction(scrollableContainerSizePx: Float): Float =
        if (count <= 0) 1f else (1f / count).coerceIn(0.1f, 1f)

    override fun visibility(scrollableContainerSizePx: Float): PositionIndicatorVisibility =
        PositionIndicatorVisibility.Show
}

/**
 * The pin toggle: gold when favorited, grey otherwise. No background — it sits at the end
 * of the departures content and scrolls with it, so it never blacks out the screen.
 */
@Composable
private fun FavoriteStar(favorited: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_star),
            contentDescription = if (favorited) "Unpin line" else "Pin line",
            colorFilter = ColorFilter.tint(if (favorited) Color(0xFFF5C518) else MutedText),
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun groundBrush(deepTint: Color): Brush =
    Brush.verticalGradient(0f to deepTint, 0.45f to Color.Black, 1f to Color.Black)

// Standard Wiener Linien / German abbreviations so long destinations fit. Order
// matters: compound (lowercase) forms before standalone, longer patterns first.
private val DESTINATION_ABBREVIATIONS = listOf(
    "Hauptbahnhof" to "Hbf",
    "bahnhof" to "bhf",
    "Bahnhof" to "Bhf",
    "straße" to "str.",
    "Straße" to "Str.",
    "gasse" to "g.",
    "Gasse" to "G.",
    "platz" to "pl.",
    "Platz" to "Pl.",
    "brücke" to "br.",
    "Brücke" to "Br.",
)

private fun shortenDestination(name: String): String {
    var s = name
    for ((from, to) in DESTINATION_ABBREVIATIONS) s = s.replace(from, to)
    return s
}

@Composable
private fun Header(line: String, stopName: String, lineColor: Color) {
    // Constrained to the safe center width so the badge is always visible. When the
    // name fits, the badge + name center as a group; when it's too long the name
    // fills the row and marquees while the badge stays put.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(0.66f).padding(vertical = 2.dp),
    ) {
        SquareBadge(line, lineColor, size = 22)
        Spacer(Modifier.width(6.dp))
        Text(stopName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1,
            modifier = Modifier.weight(1f, fill = false).basicMarquee(iterations = Int.MAX_VALUE))
    }
}

/** The `‹ Westbahnhof · Gersthof ›` tab row; the active direction is highlighted. */
@Composable
private fun DirectionTabs(pages: List<DirectionPage>, current: Int, onTab: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        val multi = pages.size > 1
        if (multi) Text("‹ ", color = MutedText, fontSize = 11.sp)
        pages.forEachIndexed { i, page ->
            if (i > 0) Text(" · ", color = MutedText, fontSize = 11.sp)
            val active = i == current
            Text(
                text = shortenDestination(page.label),
                color = if (active) Color.White else MutedText,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable { onTab(i) }
                    .padding(horizontal = 2.dp),
            )
        }
        if (multi) Text(" ›", color = MutedText, fontSize = 11.sp)
    }
}

@Composable
private fun DirectionList(
    page: DirectionPage,
    scrollState: ScrollState,
    lineColor: Color,
    favorited: Boolean,
    onToggleFavorite: () -> Unit,
) {
    val multi = page.platforms.size >= 2

    // Single platform: just a couple of pills — static and centered, not scrollable.
    if (!multi) {
        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            page.platforms.firstOrNull()?.departures?.forEach { dep ->
                DepartureRow(dep, lineColor)
            }
            FavoriteStar(favorited, onToggleFavorite)
        }
        return
    }

    // Multi-platform: a plain touch-scrolling column (NOT ScalingLazyColumn — that has
    // built-in rotary and swallows the crown once it gets focus). Touch scrolls this;
    // the crown bubbles up to the pager and always steps stations. Scaling was already
    // disabled (edgeScale 1.0f), so there's nothing lost.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = 2.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        page.platforms.forEach { group ->
            // Header only when 2+ platforms serve this direction; a lone platform
            // would just repeat the active tab.
            if (group.showPlatformLabel) {
                GroupHeader(group)
            }
            group.departures.forEach { dep ->
                DepartureRow(dep, lineColor)
            }
        }
        FavoriteStar(favorited, onToggleFavorite)
    }
}

@Composable
private fun GroupHeader(group: PlatformGroup) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 15.dp, top = 4.dp, bottom = 1.dp),
    ) {
        // Arrow + destination in one Text so they share a baseline (a separate
        // arrow Text sits higher than the name).
        Text(
            text = "→ " + shortenDestination(group.towards),
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        // No H/R here: the direction tabs above already say which way.
        if (group.compass.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text("${group.compass}·${group.rbl}", color = MutedText, fontSize = 9.5.sp)
        }
    }
}

@Composable
private fun DepartureRow(dep: DepartureUi, lineColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 23.dp, vertical = 1.dp) // inset the pills; headers stay full width
            .clip(RoundedCornerShape(14.dp))
            .background(RowSurface)
            .padding(horizontal = 16.dp, vertical = 4.dp), // matches the design draft (.dep 4px 13px)
    ) {
        // Fixed-height leading slot so the "boarding" row is exactly as tall as a
        // normal "N min" row (the vector stars are shorter than the number text).
        Box(Modifier.height(24.dp), contentAlignment = Alignment.CenterStart) {
            if (dep.countdown <= 0) {
                // At the platform now: the Wiener Linien blink, two asterisks
                // trading places, instead of "0 min".
                BoardingStars(lerp(lineColor, Color.White, 0.4f))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${dep.countdown}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(" min", color = MutedText, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (dep.barrierFree) Glyph("♿", Color(0xFFCFD3D6))
        if (dep.cooling) Glyph("❄️", Color(0xFF2F9BD8))
        if (dep.trafficjam) Glyph("⚠️", Color(0xFFF0A020))
    }
}

/** Two asterisks alternating left/right, `[* ] [ *]`, the "boarding now" cue. */
@Composable
private fun BoardingStars(color: Color) {
    val transition = rememberInfiniteTransition(label = "boarding")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 3000, easing = LinearEasing)),
        label = "phase",
    )
    val leftLit = phase < 0.5f
    Row(verticalAlignment = Alignment.CenterVertically) {
        BoardingStar(lit = leftLit, color = color)
        Spacer(Modifier.width(3.dp))
        BoardingStar(lit = !leftLit, color = color)
    }
}

/** The ✳ boarding glyph, tinted and blinking. */
@Composable
private fun BoardingStar(lit: Boolean, color: Color) {
    Text(
        "✳",
        color = color.copy(alpha = if (lit) 1f else 0.18f),
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
    )
}
