package dev.androidpoet.depot.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.androidpoet.depot.ui.DepotApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val engine = (application as DepotApplication).engine
        setContent {
            DepotApp(engine, onBack = { enabled, action -> BackHandler(enabled, action) })
        }
    }

    override fun onResume() {
        super.onResume()
        (application as DepotApplication).engine.refreshDevice()
    }
}
