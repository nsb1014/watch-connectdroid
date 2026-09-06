package com.watchrelay.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.watchrelay.app.ui.RelayViewModel
import com.watchrelay.app.ui.WatchRelayAppUi

class MainActivity : ComponentActivity() {
    private val model: RelayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        handleIntent(intent)
        setContent { WatchRelayAppUi(model) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val data = intent.data
        if (data != null && data.scheme == "watchrelay" && data.host == "strava") {
            val code = data.getQueryParameter("code")
            if (!code.isNullOrBlank()) {
                model.handleStravaCode(code)
                model.go(com.watchrelay.app.ui.Destination.Export)
            }
            return
        }
        val uris = mutableListOf<Uri>()
        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris += it }
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris += it }
        if (uris.isNotEmpty()) {
            model.importUris(uris)
        }
    }
}
