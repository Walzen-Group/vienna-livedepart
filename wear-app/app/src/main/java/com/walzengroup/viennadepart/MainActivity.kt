package com.walzengroup.viennadepart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.walzengroup.viennadepart.ui.DeparturesScreen
import com.walzengroup.viennadepart.ui.NearbyScreen
import com.walzengroup.viennadepart.ui.StopLinesScreen
import com.walzengroup.viennadepart.ui.theme.ViennaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ViennaTheme {
                val nav = rememberSwipeDismissableNavController()
                val app: AppViewModel = viewModel()

                SwipeDismissableNavHost(navController = nav, startDestination = "nearby") {
                    composable("nearby") {
                        NearbyScreen(onSelect = { stop ->
                            app.selectedStop = stop
                            nav.navigate("lines")
                        })
                    }
                    composable("lines") {
                        val stop = app.selectedStop
                        if (stop == null) {
                            PopBack(nav)
                        } else {
                            StopLinesScreen(stop, onSelect = { line ->
                                app.selectedLine = line
                                nav.navigate("departures")
                            })
                        }
                    }
                    composable("departures") {
                        val stop = app.selectedStop
                        val line = app.selectedLine
                        if (stop == null || line == null) {
                            PopBack(nav)
                        } else {
                            DeparturesScreen(stop, line)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PopBack(nav: NavHostController) {
    LaunchedEffect(Unit) { nav.popBackStack() }
}
