package com.walzengroup.viennadepart.ui

import android.app.RemoteInput
import android.content.Intent
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.walzengroup.viennadepart.data.LastConnection
import com.walzengroup.viennadepart.data.LastConnectionStore
import com.walzengroup.viennadepart.data.stops.PhysicalStop
import com.walzengroup.viennadepart.data.stops.StopRepository
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
                    1 -> NearbyPage(onLocate = onLocate, onOpenLast = onOpenLast)
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
 * Nearby page: the split pill — Locate on the left, recent connections on the
 * right. One big blob for a single recent, two stacked blobs once there are two.
 */
@Composable
private fun NearbyPage(onLocate: () -> Unit, onOpenLast: (PhysicalStop, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recents = remember { LastConnectionStore.load(context) } // most-recent-first, 0..2

    fun open(recent: LastConnection): () -> Unit = {
        scope.launch {
            val stop = StopRepository.stops(context).firstOrNull { it.diva == recent.diva }
            if (stop != null) onOpenLast(stop, recent.line)
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.58f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PillHalf(
                shape = RoundedCornerShape(
                    topStart = 40.dp, bottomStart = 40.dp, topEnd = 12.dp, bottomEnd = 12.dp,
                ),
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
                        shape = RoundedCornerShape(
                            topStart = 12.dp, bottomStart = 12.dp, topEnd = 40.dp, bottomEnd = 40.dp,
                        ),
                        recent = null, big = true, onOpen = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                    1 -> RecentBlob(
                        shape = RoundedCornerShape(
                            topStart = 12.dp, bottomStart = 12.dp, topEnd = 40.dp, bottomEnd = 40.dp,
                        ),
                        recent = recents[0], big = true, onOpen = open(recents[0]),
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> {
                        RecentBlob(
                            shape = RoundedCornerShape(
                                topStart = 12.dp, topEnd = 40.dp, bottomStart = 6.dp, bottomEnd = 6.dp,
                            ),
                            recent = recents[0], big = false, onOpen = open(recents[0]),
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                        RecentBlob(
                            shape = RoundedCornerShape(
                                topStart = 6.dp, topEnd = 6.dp, bottomStart = 12.dp, bottomEnd = 40.dp,
                            ),
                            recent = recents[1], big = false, onOpen = open(recents[1]),
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** One recent-connection blob: line badge + stop name, or an empty prompt. */
@Composable
private fun RecentBlob(
    shape: Shape,
    recent: LastConnection?,
    big: Boolean,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    PillHalf(shape = shape, onClick = onOpen, modifier = modifier, verticalPadding = if (big) 12.dp else 6.dp) {
        if (recent != null) {
            SquareBadge(recent.line, ModeColor.forLine(recent.line, recent.type), size = if (big) 26 else 20)
            Spacer(Modifier.height(if (big) 6.dp else 3.dp))
            Text(
                recent.stopName,
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
@Composable
private fun PillHalf(
    shape: Shape,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(PillColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = verticalPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
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
                        icon = { SquareBadge(fav.line, ModeColor.forLine(fav.line, fav.type), size = 30) },
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
    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 34.dp, bottom = 40.dp),
        autoCentering = null,
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
                Chip(
                    onClick = { openHistory(h) },
                    label = {
                        Text(h.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = { app.clearSearch(context) },
                    label = { Text("Clear", color = MutedText) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
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
