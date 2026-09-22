package com.chronocube.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chronocube.app.ui.ChronoCubeApp
import com.chronocube.app.ui.theme.ChronoCubeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChronoCubeTheme {
                ChronoCubeApp()
            }
        }
    }
}
