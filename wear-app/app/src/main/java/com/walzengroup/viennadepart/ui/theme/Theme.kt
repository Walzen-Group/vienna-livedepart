package com.walzengroup.viennadepart.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/** Wear Material theme: OLED-black ground, Wiener Linien red as the accent. */
@Composable
fun ViennaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = Colors(
            primary = Color(0xFFE20613),
            onPrimary = Color.White,
            background = Color.Black,
            onBackground = Color(0xFFF4F4F5),
            surface = Color(0xFF1E1E25),
            onSurface = Color(0xFFF4F4F5),
            error = Color(0xFFE2001A),
        ),
        content = content,
    )
}
