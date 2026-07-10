package com.walzengroup.viennadepart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.walzengroup.viennadepart.ui.DeparturesScreen
import com.walzengroup.viennadepart.ui.theme.ViennaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ViennaTheme {
                DeparturesScreen()
            }
        }
    }
}
