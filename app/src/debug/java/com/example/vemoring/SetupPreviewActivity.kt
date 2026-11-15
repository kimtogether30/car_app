package com.example.vemoring

import android.os.Bundle
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Debug entry into the real onboarding/save/home flow, retaining existing care records. */
class SetupPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Exercise first registration without clearing or overwriting the user's saved garage.
        val freshStore = if (intent.getBooleanExtra("freshInstall", false)) {
            val session = savedInstanceState?.getString("freshSession")
                ?: java.util.UUID.randomUUID().toString()
            freshSession = session
            GarageStore(object : ContextWrapper(this) {
                override fun getSharedPreferences(name: String, mode: Int) =
                    super.getSharedPreferences("first_install_${session}_$name", mode)
            })
        } else null
        setContent {
            MaterialTheme(
                typography = com.example.vemoring.ui.theme.Typography,
                colorScheme = GarageColors
            ) {
                GarageApp(storeOverride = freshStore, startWithSetup = true)
            }
        }
    }

    private var freshSession: String? = null

    override fun onSaveInstanceState(outState: Bundle) {
        freshSession?.let { outState.putString("freshSession", it) }
        super.onSaveInstanceState(outState)
    }
}
