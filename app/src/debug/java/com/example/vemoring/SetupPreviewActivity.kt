package com.example.vemoring

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Debug entry into the real onboarding/save/home flow, retaining existing care records. */
class SetupPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                typography = com.example.vemoring.ui.theme.Typography,
                colorScheme = GarageColors
            ) {
                GarageApp(startWithSetup = true)
            }
        }
    }
}
