package com.refmanager.tablet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Drops focus and hides the IME once an overlay dialog closes.
 *
 * When a dialog window is dismissed Android gives focus back to the host window,
 * and Compose hands it to the first focusable field in it — on this screen that is
 * the library search box, which then pops the keyboard up over the library. Waiting
 * for the dialog to be torn down before releasing focus avoids that.
 */
@Composable
fun ClearFocusWhenDialogsClose(anyDialogOpen: Boolean) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(anyDialogOpen) {
        if (!anyDialogOpen) {
            withFrameNanos { }
            withFrameNanos { }
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
}
