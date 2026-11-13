package com.refmanager.tablet.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.refmanager.tablet.data.MendeleyAuth
import com.refmanager.tablet.ui.theme.RefManagerTheme
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Signs in to Mendeley the same way Mendeley Reference Manager does: a real browser
 * session on www.mendeley.com, which is then exchanged for an API token. The password
 * is typed into Mendeley's own page and never reaches our code; we only keep the token.
 */
class MendeleyAuthActivity : ComponentActivity() {

    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var finishedWithResult = false

    private val probe = Runnable { runProbe() }
    private val readToken = Runnable { readTokenResult() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val view = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    scheduleProbe(1_200)
                }
            }
            loadUrl(MendeleyAuth.SIGN_IN_URL)
        }
        webView = view

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = webView
                if (current != null && current.canGoBack()) current.goBack() else finish()
            }
        })

        setContent {
            RefManagerTheme {
                Column(Modifier.fillMaxSize()) {
                    SignInBar(
                        onClose = { finish() },
                        onUseSession = {
                            // Mint a fresh token right now, then read it.
                            runProbe()
                        }
                    )
                    AndroidView(
                        factory = { view },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }

    @Composable
    private fun SignInBar(onClose: () -> Unit, onUseSession: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
            }
            Column(Modifier.weight(1f)) {
                Text("Sign in to Mendeley", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Your password goes to Mendeley, not to this app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onUseSession) { Text("Continue") }
        }
    }

    private fun scheduleProbe(delayMillis: Long) {
        if (finishedWithResult) return
        handler.postDelayed(probe, delayMillis)
    }

    /**
     * Asks the page (same origin as www.mendeley.com, so its cookies are sent) to mint a
     * token. HttpOnly session cookies are used by the browser itself, never read by us.
     */
    private fun runProbe() {
        val view = webView ?: return
        if (finishedWithResult) return
        val url = view.url.orEmpty()
        if (!url.contains("mendeley.com")) {
            scheduleProbe(1_500)
            return
        }
        view.evaluateJavascript(
            """
            (function () {
              if (window.__mrmBusy) return;
              window.__mrmBusy = true;
              fetch('${MendeleyAuth.REFRESH_TOKEN_URL}', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
              }).then(function (r) { return r.text(); })
                .then(function (t) { window.__mrmToken = t; window.__mrmBusy = false; })
                .catch(function () { window.__mrmToken = ''; window.__mrmBusy = false; });
            })();
            """.trimIndent(),
            null
        )
        handler.postDelayed(readToken, 900)
    }

    private fun readTokenResult() {
        val view = webView ?: return
        if (finishedWithResult) return
        view.evaluateJavascript("window.__mrmToken || ''") { raw ->
            val payload = raw?.let { decodeJsString(it) }.orEmpty()
            val token = runCatching {
                JSONObject(payload).optString("access_token")
            }.getOrNull().orEmpty()

            if (token.isNotBlank()) {
                MendeleyAuth.saveToken(this, token)
                finishedWithResult = true
                handler.removeCallbacksAndMessages(null)
                setResult(RESULT_OK)
                finish()
            } else {
                // Not signed in yet (or the session is not established): keep waiting.
                scheduleProbe(1_500)
            }
        }
    }

    /** evaluateJavascript returns a JSON-encoded string; unwrap it. */
    private fun decodeJsString(raw: String): String? = runCatching {
        when (val value = JSONTokener(raw).nextValue()) {
            is String -> value
            null -> null
            else -> value.toString()
        }
    }.getOrNull()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView?.let {
            it.stopLoading()
            it.destroy()
        }
        webView = null
        super.onDestroy()
    }
}
