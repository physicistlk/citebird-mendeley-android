package com.refmanager.tablet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.refmanager.tablet.ui.AppRoot
import com.refmanager.tablet.ui.MainViewModel
import com.refmanager.tablet.ui.theme.RefManagerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        hideSystemBars()
        setContent {
            RefManagerTheme {
                AppRoot(viewModel)
            }
        }
        handleIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Handles "open with" / "share to" PDFs, plus a debug-only sample import. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return

        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_SAMPLE, false)) {
            viewModel.importSample()
            return
        }

        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let(viewModel::importPdf)
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                uri?.let(viewModel::importPdf)
            }
        }
    }

    private companion object {
        const val EXTRA_SAMPLE = "sample"
    }
}
