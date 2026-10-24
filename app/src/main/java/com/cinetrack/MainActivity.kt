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
    /** RFC 9207 issuer, checked against Simkl before the code is exchanged. */
    val issuer: String? = null,
    /** Which sign-in returned: Simkl, or Floppy's app sign-in. */
    val floppy: Boolean = false,
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

    override fun onStop() {
        super.onStop()
        // Leaving the app is when the home screen widget becomes visible again.
        UpNextWidgetProvider.refresh(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "cinetrack" && (uri.host == "simkl" || uri.host == "floppy")) {
            authCallback.value = SimklAuthCallback(
                code = uri.getQueryParameter("code"),
                state = uri.getQueryParameter("state"),
                error = uri.getQueryParameter("error"),
                issuer = uri.getQueryParameter("iss"),
                floppy = uri.host == "floppy",
            )
        } else if (uri.scheme == "cinetrack") {
            DeepLinkRoutes.route(uri.host, uri.path, uri.pathSegments)?.let { navigationRequest.value = it }
        }
    }
}
