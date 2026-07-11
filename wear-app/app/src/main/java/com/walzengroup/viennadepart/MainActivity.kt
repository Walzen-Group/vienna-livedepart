package com.walzengroup.viennadepart

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.compose.navigation.rememberSwipeDismissableNavHostState
import com.walzengroup.viennadepart.data.TransitRefreshWorker
import com.walzengroup.viennadepart.tile.EXTRA_FAVORITE_LINE
import com.walzengroup.viennadepart.ui.DeparturesScreen
import com.walzengroup.viennadepart.ui.LocalSwipeToDismissState
import com.walzengroup.viennadepart.ui.FavoriteOpenScreen
import com.walzengroup.viennadepart.ui.HomeScreen
import com.walzengroup.viennadepart.ui.NearbyScreen
import com.walzengroup.viennadepart.ui.StopLinesScreen
import com.walzengroup.viennadepart.ui.theme.ViennaTheme

class MainActivity : ComponentActivity() {
    // A favorite line to open straight into, delivered by the honeycomb Tile (null = normal launch).
    private val pendingFavorite = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TransitRefreshWorker.schedule(this) // weekly background refresh of stop + route data
        pendingFavorite.value = intent?.getStringExtra(EXTRA_FAVORITE_LINE)
        setContent {
            ViennaTheme {
                val nav = rememberSwipeDismissableNavController()
                val app: AppViewModel = viewModel()
                // One shared swipe-to-dismiss state so a nested HorizontalPager (departures
                // directions) can route left-edge swipes to Back via edgeSwipeToDismiss.
                val swipeToDismissState = rememberSwipeToDismissBoxState()
                val navHostState = rememberSwipeDismissableNavHostState(swipeToDismissState)

                // Tile deep-link: jump to the favorite's located departures, then clear it so the
                // navigation fires once (and Back from departures returns Home).
                val favoriteLine by pendingFavorite
                LaunchedEffect(favoriteLine) {
                    val line = favoriteLine ?: return@LaunchedEffect
                    app.selectedLine = line
                    nav.navigate("favorite")
                    pendingFavorite.value = null
                }

                CompositionLocalProvider(LocalSwipeToDismissState provides swipeToDismissState) {
                SwipeDismissableNavHost(
                    navController = nav,
                    startDestination = "home",
                    state = navHostState,
                ) {
                    composable("home") {
                        HomeScreen(
                            app = app,
                            onLocate = {
                                app.nearbyStops = null // a fresh Locate tap refetches
                                nav.navigate("nearby")
                            },
                            onStopSelected = { stop ->
                                app.selectedStop = stop
                                nav.navigate("lines")
                            },
                            onOpenLast = { stop, line ->
                                app.selectedStop = stop
                                app.selectedLine = line
                                nav.navigate("departures")
                            },
                            onOpenFavorite = { fav ->
                                app.selectedLine = fav.line
                                nav.navigate("favorite")
                            },
                        )
                    }
                    composable("nearby") {
                        NearbyScreen(
                            cached = app.nearbyStops,
                            onLoaded = { app.nearbyStops = it },
                            onSelect = { stop ->
                                app.selectedStop = stop
                                nav.navigate("lines")
                            },
                        )
                    }
                    composable("lines") {
                        val stop = app.selectedStop
                        if (stop == null) {
                            PopBack(nav)
                        } else {
                            StopLinesScreen(stop, onSelect = { line, _ ->
                                app.selectedLine = line
                                nav.navigate("departures")
                            })
                        }
                    }
                    composable("favorite") {
                        val line = app.selectedLine
                        if (line == null) {
                            PopBack(nav)
                        } else {
                            FavoriteOpenScreen(line) { stop ->
                                app.selectedStop = stop
                                nav.navigate("departures") {
                                    // Drop the loading screen so Back returns to Home.
                                    popUpTo("favorite") { inclusive = true }
                                }
                            }
                        }
                    }
                    composable("departures") {
                        val stop = app.selectedStop
                        val line = app.selectedLine
                        if (stop == null || line == null) {
                            PopBack(nav)
                        } else {
                            DeparturesScreen(stop, line, app)
                        }
                    }
                }
                }
            }
        }
    }

    // The Tile launches this activity again while it may already be running (singleTask); pick up
    // the newly-tapped favorite so the deep-link effect re-fires.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingFavorite.value = intent.getStringExtra(EXTRA_FAVORITE_LINE)
    }
}

@Composable
private fun PopBack(nav: NavHostController) {
    LaunchedEffect(Unit) { nav.popBackStack() }
}
