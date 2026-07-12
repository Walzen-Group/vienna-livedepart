package com.walzengroup.viennadepart.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
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
import com.walzengroup.viennadepart.data.AppSettings
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

// ┌───────────────────────────────────────────────────────────────────────────┐
// │ THE FAVORITE STAR RULE — do not violate, do not "improve" this away.        │
// │                                                                             │
// │ The star is ALWAYS the LAST item INSIDE the scrolling departures content.   │
// │ You reach it by scrolling to the bottom. It scrolls with the list.          │
// │                                                                             │
// │ It must NEVER be docked, pinned, floated, or placed in fixed screen space — │
// │ not a bottom bar, not a chrome overlay, not a sibling below a weight(1f)/    │
// │ fillMaxHeight column (that pins it). This applies to BOTH the single- and    │
// │ multi-platform branches of DirectionList. This has been re-broken and re-    │
// │ fixed many times; if a layout change tempts you to move it, don't.          │
// └───────────────────────────────────────────────────────────────────────────┘

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun DeparturesScreen(stop: PhysicalStop, line: String, app: AppViewModel) {
    val context = LocalContext.current
    val repo = remember { DeparturesRepository() }
    // How many upcoming departures to show per platform (user setting, 2..5, default 2).
    val futureCount = remember { AppSettings.futureDepartures(context) }
    LaunchedEffect(Unit) { app.ensureLoaded(context) }

    // The route the user is riding (empty until the live termini match a pattern).
    var chain by remember(line) { mutableStateOf<List<PhysicalStop>>(emptyList()) }
    var lineType by remember(line) { mutableStateOf<String?>(null) }
    // The opened stop's latest departures, held ABOVE the key(stops) block so that when the
    // chain resolves and rebuilds the pager, its page seeds from this instead of re-flashing
    // the spinner.
    var openedUi by remember(stop.diva, line) { mutableStateOf<DeparturesUi?>(null) }
    // Stops to crown through: the matched chain if it includes this stop, else just it.
    val stops = remember(chain, stop.diva) {
        val idx = chain.indexOfFirst { it.diva == stop.diva }
        if (idx >= 0) chain else listOf(stop)
    }

    // Recreate the pager when the stop list resolves (empty -> full chain).
    key(stops) {
        val startIndex = stops.indexOfFirst { it.diva == stop.diva }.coerceAtLeast(0)
        val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { stops.size })
        var acc by remember { mutableFloatStateOf(0f) }
        // Index (register) mode: crowning opens a route-strip chooser of the whole line; the
        // opened stop is remembered so a cancel returns to it.
        var indexMode by remember { mutableStateOf(false) }
        var highlight by remember { mutableIntStateOf(startIndex) }
        var indexReturnPage by remember { mutableIntStateOf(startIndex) }
        // Stops the user has opened, hoisted above the pager so a loaded stop stays loaded
        // when the page scrolls out of composition and back. The opened stop starts loaded.
        val loaded = remember { mutableStateListOf(stops[startIndex].diva) }
        // Chrome (background, header, star, position indicator) is persistent and lives
        // ABOVE the pager, so crowning between stops slides only the departures body over
        // a single fixed sheet — and no nested Scaffold steals the crown's focus.
        val lineColor = ModeColor.forLine(line, lineType)
        val ground = groundBrush(lerp(Color.Black, lineColor, 0.45f))

        // Curved rotary position tracker that hugs the bezel (native Wear indicator).
        val stopIndicator = remember(stops.size) { StopIndicatorState(stops.size) }
        stopIndicator.current = (if (indexMode) highlight else pagerState.currentPage).coerceIn(0, stops.lastIndex)
        Scaffold(
            timeText = { if (!indexMode) TimeText() },
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
                if (indexMode) {
                    IndexView(
                        stops = stops,
                        highlight = highlight,
                        lineColor = lineColor,
                        line = line,
                        onHighlight = { highlight = it.coerceIn(0, stops.lastIndex) },
                        onSelect = { idx ->
                            if (stops[idx].diva !in loaded) loaded.add(stops[idx].diva)
                            pagerState.requestScrollToPage(idx)
                            indexMode = false
                        },
                        onCancel = {
                            pagerState.requestScrollToPage(indexReturnPage)
                            indexMode = false
                        },
                    )
                } else {
                    val focusRequester = rememberActiveFocusRequester()
                    Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(22.dp)) // clear the TimeText curve
                    VerticalPager(
                        state = pagerState,
                        userScrollEnabled = false, // crown-only; the inner list keeps touch-scroll
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            // Any crown opens the index chooser (one detent of travel), which is
                            // the way to move between stops now.
                            .onRotaryScrollEvent { e ->
                                acc += e.verticalScrollPixels
                                val threshold = 60f
                                if ((acc >= threshold || acc <= -threshold) && stops.size > 1) {
                                    highlight = pagerState.currentPage.coerceIn(0, stops.lastIndex)
                                    indexReturnPage = highlight
                                    indexMode = true
                                    acc = 0f
                                }
                                true
                            }
                            .focusRequester(focusRequester)
                            .focusable(),
                    ) { page ->
                        val pageStop = stops[page]
                        // A stop stays loaded once opened (state hoisted in `loaded`); crowned-to
                        // stops show a reload button until tapped, so scrolling fires no requests.
                        val isLoaded = pageStop.diva in loaded
                        val isCurrent = page == pagerState.currentPage
                        // The header rides inside the page so the line badge + station name
                        // slide vertically with the stop. The name comes from bundled stop
                        // data, so it stays put (no blank) while the body loads.
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(Modifier.height(8.dp)) // drop the header into the wider band
                            Header(line, pageStop.name, lineColor)
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                if (isLoaded) {
                                    Loadable(
                                        loader = { repo.departuresForLine(pageStop, line, futureCount) },
                                        // Live refresh only for the stop on screen.
                                        refreshMs = if (isCurrent) 30_000 else 0,
                                        key = pageStop.diva,
                                        // On the on-screen stop, refresh immediately on resume if the
                                        // last load is >45s stale (the 30s loop is frozen while off).
                                        refreshOnResumeAfterMs = if (isCurrent) 45_000 else 0,
                                        // Seed the opened stop's rebuilt page so the chain-resolve
                                        // rebuild doesn't re-flash the spinner.
                                        initial = if (pageStop.diva == stop.diva) openedUi else null,
                                    ) { ui ->
                                        LaunchedEffect(ui.lineType) { lineType = ui.lineType }
                                        // Keep the opened stop's data warm for the pager rebuild.
                                        LaunchedEffect(pageStop.diva, ui) {
                                            if (pageStop.diva == stop.diva) openedUi = ui
                                        }
                                        // Opening a station's departures records it as recent
                                        // (once per stop view, not on every 30s refresh).
                                        LaunchedEffect(pageStop.diva, ui.lineType) {
                                            app.addRecent(context, pageStop, line, ui.lineType)
                                        }
                                        val termini = ui.directions.map { it.label }
                                        LaunchedEffect(termini) {
                                            if (chain.isEmpty() && termini.isNotEmpty()) {
                                                chain = RouteRepository.chainFor(context, line, termini, stop.diva)
                                            }
                                        }
                                        DeparturesBody(ui, app)
                                    }
                                } else {
                                    ReloadBody(onReload = { if (pageStop.diva !in loaded) loaded.add(pageStop.diva) })
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

/**
 * Route-strip index of the whole line: a mode-colour line bowed to the round screen, a
 * white dot per stop, station names (two lines) to the right, the highlighted stop centred.
 * Crown moves the highlight, a tap selects it, a horizontal swipe cancels.
 */
@OptIn(ExperimentalWearFoundationApi::class)
@Composable
private fun IndexView(
    stops: List<PhysicalStop>,
    highlight: Int,
    lineColor: Color,
    line: String,
    onHighlight: (Int) -> Unit,
    onSelect: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val focusRequester = rememberActiveFocusRequester()
    var acc by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    var hPx by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { hPx = it.height.toFloat() }
            .onRotaryScrollEvent { e ->
                acc += e.verticalScrollPixels
                val threshold = 60f
                var h = highlight
                while (acc >= threshold) { h = (h + 1).coerceIn(0, stops.lastIndex); acc -= threshold }
                while (acc <= -threshold) { h = (h - 1).coerceIn(0, stops.lastIndex); acc += threshold }
                if (h != highlight) onHighlight(h)
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(Unit) { detectTapGestures { onSelect(highlight) } }
            .pointerInput(Unit) {
                var total = 0f
                var fired = false
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f; fired = false },
                    onHorizontalDrag = { _, d ->
                        total += d
                        if (!fired && kotlin.math.abs(total) > 40.dp.toPx()) { fired = true; onCancel() }
                    },
                )
            },
    ) {
        if (hPx <= 0f) return@Box // not measured yet (first frame)
        val centerY = hPx / 2f
        val half = hPx / 2f
        val spacing = with(density) { 46.dp.toPx() }
        val leftBase = with(density) { 30.dp.toPx() }
        val curveK = with(density) { 44.dp.toPx() } // how far the line bows in near the edges
        val nameGap = with(density) { 14.dp.toPx() }
        val rowH = with(density) { 42.dp.toPx() }

        fun yOf(i: Int) = centerY + (i - highlight) * spacing
        // Bow the strip: points further from centre inset toward centre (a parabola in y), so
        // the line arcs with the round bezel instead of clipping at the top/bottom corners.
        fun xAtY(y: Float): Float {
            val t = (y - centerY) / half
            return leftBase + curveK * t * t
        }
        fun xOf(i: Int) = xAtY(yOf(i))
        // One extra stop past each edge keeps the line running to the screen edges.
        val visible = stops.indices.filter { yOf(it) in -spacing..(hPx + spacing) }

        Canvas(Modifier.fillMaxSize()) {
            // Sample the parabola in small steps so the line is a smooth curve, not a polyline
            // between far-apart dots, and always spans the full screen height.
            val path = Path()
            path.moveTo(xAtY(0f), 0f)
            var yy = 0f
            while (yy <= hPx) { path.lineTo(xAtY(yy), yy); yy += 6f }
            path.lineTo(xAtY(hPx), hPx)
            drawPath(path, color = lineColor, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            visible.forEach { i ->
                val c = Offset(xOf(i), yOf(i))
                if (i == highlight) {
                    drawCircle(color = lineColor, radius = 10.dp.toPx(), center = c)
                    drawCircle(color = Color.White, radius = 6.dp.toPx(), center = c)
                } else {
                    drawCircle(color = Color.White, radius = 4.dp.toPx(), center = c)
                }
            }
        }

        visible.forEach { i ->
            val x = xOf(i)
            val y = yOf(i)
            val dist = kotlin.math.abs(i - highlight)
            val nameColor =
                if (i == highlight) Color.White
                else MutedText.copy(alpha = (1f - dist * 0.2f).coerceIn(0.3f, 1f))
            Box(
                modifier = Modifier
                    .offset { IntOffset((x + nameGap).roundToInt(), (y - rowH / 2f).roundToInt()) }
                    .width(150.dp)
                    .height(42.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    stops[i].name,
                    color = nameColor,
                    fontSize = if (i == highlight) 14.sp else 12.sp,
                    fontWeight = if (i == highlight) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp,
                )
            }
        }

        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
            SquareBadge(line, lineColor, size = 20)
        }
    }
}

/** Centered reload button shown for a crowned-to station until tapped (no request until then). */
@Composable
private fun ReloadBody(onReload: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
        Spacer(Modifier.height(8.dp))
        Text("Tap to load stations", color = MutedText, fontSize = 12.sp)
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
        // A touch wider than before (it sits lower now, where the circle is wider) with a
        // small horizontal inset so the badge never reaches the round clip edge.
        modifier = Modifier.fillMaxWidth(0.72f).padding(horizontal = 6.dp, vertical = 2.dp),
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

    // Single platform: the whole thing is ONE scrollable column. The pills + star center
    // in the available space when they fit (a top spacer sized from the leftover space);
    // once they overflow the viewport the leftover is 0 and the touch-scroll takes over.
    // The star is the LAST item INSIDE the scroll content — reached by scrolling to the
    // bottom. NEVER dock/pin/float it in fixed screen space (see the star rule in the
    // file header / HANDOFF). The departures-count setting (up to 5) makes overflow real.
    if (!multi) {
        val density = LocalDensity.current
        var viewportPx by remember { mutableIntStateOf(0) }
        var contentPx by remember { mutableIntStateOf(0) }
        val padTop = with(density) { ((viewportPx - contentPx).coerceAtLeast(0) / 2).toDp() }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 8.dp)
                .onSizeChanged { viewportPx = it.height }
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(padTop))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { contentPx = it.height },
                verticalArrangement = Arrangement.spacedBy(1.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                page.platforms.firstOrNull()?.departures?.forEach { dep ->
                    DepartureRow(dep, lineColor)
                }
                FavoriteStar(favorited, onToggleFavorite)
            }
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 1.dp) // inset the pills; headers stay full width
            .clip(RoundedCornerShape(14.dp))
            .background(RowSurface)
            .padding(horizontal = 12.dp, vertical = 4.dp), // matches the design draft (.dep 4px 13px)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
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
        // Wall-clock departure time at the card's true horizontal center (overlaid so the
        // leading countdown and trailing glyphs don't shift it off-centre).
        Text(
            dep.time,
            color = MutedText,
            fontSize = 10.sp,
            maxLines = 1,
            modifier = Modifier.align(Alignment.Center),
        )
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
