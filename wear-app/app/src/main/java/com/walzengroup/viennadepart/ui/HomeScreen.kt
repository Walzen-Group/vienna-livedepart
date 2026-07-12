package com.walzengroup.viennadepart.ui

import android.app.RemoteInput
import android.content.Intent
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.WearableRemoteInputExtender
import com.walzengroup.viennadepart.AppViewModel
import com.walzengroup.viennadepart.R
import com.walzengroup.viennadepart.data.AppSettings
import com.walzengroup.viennadepart.data.Favorite
import com.walzengroup.viennadepart.data.HistoryStop
import com.walzengroup.viennadepart.data.RecentStop
import com.walzengroup.viennadepart.data.stops.PhysicalStop
import com.walzengroup.viennadepart.data.stops.StopRepository
import com.walzengroup.viennadepart.ui.common.ClearAllButton
import com.walzengroup.viennadepart.ui.common.ConfirmDeleteOverlay
import com.walzengroup.viennadepart.ui.common.MutedText
import com.walzengroup.viennadepart.ui.common.SquareBadge
import com.walzengroup.viennadepart.ui.theme.ModeColor
import kotlinx.coroutines.launch

// Neutral Material grey for the pills; the red pin and line badges are the accents.
private val PillColor = Color(0xFF2B2930)

private const val SEARCH_KEY = "vienna_stop_query"

/**
 * Home is a three-page pager: Favorites · Nearby (default) · Search. Horizontal
 * swipe flips pages; vertical scroll stays free for the lists inside a page.
 */
@Composable
fun HomeScreen(
    app: AppViewModel,
    onLocate: () -> Unit,
    onStopSelected: (PhysicalStop) -> Unit,     // a search result -> pick a line
    onOpenLast: (PhysicalStop, String) -> Unit, // recent -> straight to departures
    onOpenFavorite: (Favorite) -> Unit,         // favorite line -> nearest stop -> departures
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { app.ensureLoaded(context) }
    // Open on Favorites (page 0) or Nearby (page 1) per the Settings preference.
    val startPage = remember { if (AppSettings.openToFavorites(context)) 0 else 1 }
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { 4 })
    Scaffold(timeText = { TimeText() }) {
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> FavoritesPage(favorites = app.favorites, onOpen = onOpenFavorite)
                    1 -> NearbyPage(app = app, onLocate = onLocate, onOpenLast = onOpenLast)
                    2 -> SearchPage(app = app, onStopSelected = onStopSelected)
                    else -> SettingsPage()
                }
            }
            PageDots(
                current = pagerState.currentPage,
                count = 4,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
            )
        }
    }
}

/**
 * Nearby page: the split pill (Locate + the two most recent stations) at the top, and,
 * once there are more than two recents, the rest scroll below as chips. Tapping any
 * recent reopens departures on the line last ridden there.
 */
@Composable
private fun NearbyPage(
    app: AppViewModel,
    onLocate: () -> Unit,
    onOpenLast: (PhysicalStop, String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recents = app.recentStops

    val openRecent: (RecentStop) -> Unit = { r ->
        scope.launch {
            val stop = StopRepository.stops(context).firstOrNull { it.diva == r.diva }
            if (stop != null) onOpenLast(stop, r.line)
        }
    }

    // No recents: just the centered split pill (Locate + an empty prompt).
    if (recents.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SplitPill(onLocate, recents, openRecent, Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.58f))
        }
        return
    }

    // With recents: the pill sits dead-center on the first screenful (0.21 top gap + 0.58
    // pill height, both fractions of the viewport). The "Recent" header and overflow chips
    // flow below, so the first chip peeks up as a scroll hint; Clear all is docked at the
    // end. It's a LazyColumn so the overflow chips beyond the visible one compose only as
    // they scroll into view — swiping onto this page builds just the pill (+ peeking chip),
    // not every recent at once. Layout is unchanged from the old verticalScroll version;
    // the fillParentMaxHeight fractions replace the measure-then-size round-trip.
    // Long-press a recent (pill blob or chip) to bring up the remove confirm.
    var pendingDelete by remember { mutableStateOf<RecentStop?>(null) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Spacer(Modifier.fillParentMaxHeight(0.21f)) }
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().fillParentMaxHeight(0.58f),
                    contentAlignment = Alignment.Center,
                ) {
                    SplitPill(
                        onLocate, recents.take(2), openRecent,
                        onLongPress = { pendingDelete = it },
                        modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(),
                    )
                }
            }
            if (recents.size > 2) {
                item {
                    // top gap = bottom gap (4 here + the first chip's own 6dp top padding)
                    Text("Recent", color = MutedText, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                }
                recents.drop(2).forEach { r ->
                    item(key = r.diva) {
                        RecentChip(
                            line = r.line, type = r.type, name = r.name,
                            onOpen = { openRecent(r) },
                            onLongPress = { pendingDelete = r },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(5.dp))
                    ClearAllButton(onClick = { app.clearRecents(context) })
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        pendingDelete?.let { r ->
            ConfirmDeleteOverlay(
                name = r.name,
                onConfirm = { app.removeRecent(context, r.diva); pendingDelete = null },
                onCancel = { pendingDelete = null },
            )
        }
    }
}

/** Locate on the left; the top two recent stations stacked on the right. */
@Composable
private fun SplitPill(
    onLocate: () -> Unit,
    recents: List<RecentStop>,
    onOpen: (RecentStop) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (RecentStop) -> Unit = {},
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PillHalf(
            shape = RoundedCornerShape(topStart = 40.dp, bottomStart = 40.dp, topEnd = 12.dp, bottomEnd = 12.dp),
            onClick = onLocate,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_pin),
                contentDescription = "Find nearby stops",
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text("Locate", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (recents.size) {
                0 -> RecentBlob(
                    shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 40.dp, bottomEnd = 40.dp),
                    recent = null, big = true, onOpen = null, modifier = Modifier.fillMaxSize(),
                )
                1 -> RecentBlob(
                    shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 40.dp, bottomEnd = 40.dp),
                    recent = recents[0], big = true, onOpen = { onOpen(recents[0]) },
                    onLongPress = { onLongPress(recents[0]) }, modifier = Modifier.fillMaxSize(),
                )
                else -> {
                    RecentBlob(
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 40.dp, bottomStart = 6.dp, bottomEnd = 6.dp),
                        recent = recents[0], big = false, onOpen = { onOpen(recents[0]) },
                        onLongPress = { onLongPress(recents[0]) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    RecentBlob(
                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 12.dp, bottomEnd = 40.dp),
                        recent = recents[1], big = false, onOpen = { onOpen(recents[1]) },
                        onLongPress = { onLongPress(recents[1]) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }
}

/** One recent-station blob: line badge + stop name, or an empty prompt. */
@Composable
private fun RecentBlob(
    shape: Shape,
    recent: RecentStop?,
    big: Boolean,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
) {
    PillHalf(
        shape = shape, onClick = onOpen, onLongClick = onLongPress,
        modifier = modifier, verticalPadding = if (big) 12.dp else 6.dp,
    ) {
        if (recent != null) {
            SquareBadge(recent.line, ModeColor.forLine(recent.line, recent.type), size = if (big) 26 else 20)
            Spacer(Modifier.height(if (big) 6.dp else 3.dp))
            Text(
                recent.name,
                color = Color.White,
                fontSize = if (big) 10.5.sp else 9.sp,
                lineHeight = if (big) 12.sp else 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        } else {
            Text("No recent trip", color = MutedText, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

/** A section of the split pill: a rounded, optionally-clickable column of content. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PillHalf(
    shape: Shape,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 12.dp,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(PillColor)
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 8.dp, vertical = verticalPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/**
 * A recent/history row that looks like a secondary chip but supports long-press. The Wear
 * `Chip` has no onLongClick, so a transparent [combinedClickable] overlay sits on top and
 * handles both tap (open) and long-press (delete); the chip below is visual only.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentChip(
    line: String?,
    type: String?,
    name: String,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Chip(
            onClick = onOpen,
            icon = if (line != null) {
                { SquareBadge(line, ModeColor.forLine(line, type), size = 26) }
            } else {
                null
            },
            label = { Text(name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(50))
                .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        )
    }
}

private val FavoriteGold = Color(0xFFF5C518)

@Composable
private fun FavoritesPage(favorites: List<Favorite>, onOpen: (Favorite) -> Unit) {
    val ground = Brush.verticalGradient(
        0f to lerp(Color.Black, FavoriteGold, 0.32f), 0.45f to Color.Black, 1f to Color.Black,
    )
    Box(Modifier.fillMaxSize().background(ground)) {
        if (favorites.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FavoritesTitle()
                Spacer(Modifier.height(8.dp))
                Text(
                    "Star a line on its departures screen to pin it here",
                    color = MutedText,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            val listState = rememberScalingLazyListState()
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item { FavoritesTitle() }
                items(favorites, key = { it.line }) { fav ->
                    Chip(
                        onClick = { onOpen(fav) },
                        // Fixed-width badge slot so the mode label starts at the same x for
                        // every row regardless of badge width ("9" vs "44" vs "3A").
                        icon = {
                            Box(Modifier.width(42.dp), contentAlignment = Alignment.Center) {
                                SquareBadge(fav.line, ModeColor.forLine(fav.line, fav.type), size = 30)
                            }
                        },
                        label = { Text(ModeColor.modeName(fav.line, fav.type), fontSize = 13.sp, maxLines = 1) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoritesTitle() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_star),
            contentDescription = null,
            colorFilter = ColorFilter.tint(FavoriteGold),
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text("Favorites", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

/**
 * Search page: a prominent search button on top; below it the results when a
 * search is active, otherwise the recent stops with a Clear at the bottom.
 */
@Composable
private fun SearchPage(app: AppViewModel, onStopSelected: (PhysicalStop) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Transient: leaving the Search page clears the query so returning shows the
    // Recent list, not the previous results.
    var query by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<PhysicalStop>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    // Recents come from the ViewModel so they survive home page swipes.
    val history = app.searchHistory

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val text = res.data?.let { RemoteInput.getResultsFromIntent(it) }
            ?.getCharSequence(SEARCH_KEY)?.toString()?.trim()
        if (!text.isNullOrEmpty()) query = text
    }
    fun launchInput() = launcher.launch(buildSearchInputIntent())

    fun choose(stop: PhysicalStop) {
        app.addSearch(context, stop)
        onStopSelected(stop)
    }

    fun openHistory(h: HistoryStop) {
        scope.launch {
            val stop = StopRepository.stops(context).firstOrNull { it.diva == h.diva }
            if (stop != null) choose(stop)
        }
    }

    LaunchedEffect(query) {
        val q = query
        if (q.isNullOrBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        searching = true
        results = StopRepository.search(context, q)
        searching = false
    }

    val hasListBelow = query != null || history.isNotEmpty()

    // Empty state: a big, centered rounded-rectangle, like the start-page pills.
    if (!hasListBelow) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SearchButton(
                heightDp = 112,
                modifier = Modifier.fillMaxWidth(0.86f),
                shape = RoundedCornerShape(40.dp),
                onClick = { launchInput() },
            )
        }
        return
    }

    // With a list, the button is the first list item so it scrolls with the
    // results/recents; inset from the sides, and the list is padded down so the
    // clock doesn't overlap the button.
    val listState = rememberScalingLazyListState()
    // Long-press a history row to bring up the remove confirm.
    var pendingDelete by remember { mutableStateOf<HistoryStop?>(null) }
    Box(Modifier.fillMaxSize()) {
    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 34.dp, bottom = 0.dp),
        autoCentering = null,
        // No edge fisheye: keep every item (incl. Clear all) full size, like the Nearby page.
        scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            SearchButton(
                heightDp = 80,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                onClick = { launchInput() },
            )
        }
        if (query != null) {
            item {
                Text(query ?: "", color = MutedText, fontSize = 10.5.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp))
            }
            when {
                searching -> item { CircularProgressIndicator() }
                results.isEmpty() -> item {
                    Text("No matches", color = MutedText, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp))
                }
                else -> items(results, key = { it.diva }) { stop ->
                    Chip(
                        onClick = { choose(stop) },
                        label = { Text(stop.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            item {
                Text("Recent", color = MutedText, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 1.dp))
            }
            items(history, key = { it.diva }) { h ->
                RecentChip(
                    line = null, type = null, name = h.name,
                    onOpen = { openHistory(h) },
                    onLongPress = { pendingDelete = h },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                // Column so the spacers stack vertically; a bare item {} boxes its children.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(5.dp))
                    ClearAllButton(onClick = { app.clearSearch(context) })
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
        pendingDelete?.let { h ->
            ConfirmDeleteOverlay(
                name = h.name,
                onConfirm = { app.removeSearch(context, h.diva); pendingDelete = null },
                onCancel = { pendingDelete = null },
            )
        }
    }
}

/** The "Search" pill: a magnifier over the label. Stadium by default; the empty
 *  state passes a rounded-rectangle shape to match the start-page pills. */
@Composable
private fun SearchButton(
    heightDp: Int,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape((heightDp / 2).dp), // fully-rounded ends
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(heightDp.dp)
            .clip(shape)
            .background(PillColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = "Search a stop",
            colorFilter = ColorFilter.tint(ModeColor.forLine("U2", null)),
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text("Search", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun PageDots(current: Int, count: Int, modifier: Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val active = i == current
            Box(
                Modifier
                    .size(if (active) 6.dp else 5.dp)
                    .clip(CircleShape)
                    .background(if (active) Color.White else MutedText.copy(alpha = 0.5f)),
            )
        }
    }
}

private fun buildSearchInputIntent(): Intent {
    // Action type "search" makes the keyboard's action button commit the text; the
    // default "send" leaves the last word uncommitted, so Enter returns CANCELED.
    val builder = WearableRemoteInputExtender(RemoteInput.Builder(SEARCH_KEY).setLabel("Search stop"))
        .setEmojisAllowed(false)
        .setInputActionType(EditorInfo.IME_ACTION_SEARCH)
        .get()
    val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
    RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(builder.build()))
    return intent
}
