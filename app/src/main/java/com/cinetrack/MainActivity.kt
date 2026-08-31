package com.cinetrack

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.cinetrack.ui.CineTrackApp
import com.cinetrack.ui.theme.CineTrackTheme
import kotlinx.coroutines.flow.MutableStateFlow

data class SimklAuthCallback(
    val code: String? = null,
    val state: String? = null,
    val error: String? = null,
)

class MainActivity : AppCompatActivity() {
    private val authCallback = MutableStateFlow<SimklAuthCallback?>(null)
    private val navigationRequest = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            CineTrackTheme {
                CineTrackApp(authCallback = authCallback, navigationRequest = navigationRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "cinetrack" && uri.host == "simkl") {
            authCallback.value = SimklAuthCallback(
                code = uri.getQueryParameter("code"),
                state = uri.getQueryParameter("state"),
                error = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error"),
            )
        } else if (uri.scheme == "cinetrack" && uri.host == "app") {
            navigationRequest.value = when (uri.path) {
                "/search" -> "search/discover"
                "/up-next", "/progress" -> "progress"
                "/sync" -> "sync"
                "/library" -> "library"
                else -> null
            }
        } else if (uri.scheme == "cinetrack" && uri.host == "episode") {
            val parts = uri.pathSegments
            if (parts.size >= 3) navigationRequest.value = "episode/${parts[0]}/${parts[1]}/${parts[2]}"
        } else if (uri.scheme == "cinetrack" && uri.host == "detail") {
            val parts = uri.pathSegments
            if (parts.size >= 2) navigationRequest.value = "detail/${parts[0].uppercase()}/${parts[1]}"
        }
    }
}
