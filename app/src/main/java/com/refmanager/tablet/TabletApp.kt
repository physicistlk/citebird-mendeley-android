package com.refmanager.tablet

import android.app.Application
import android.webkit.WebView
import com.refmanager.tablet.data.AppDatabase
import com.refmanager.tablet.data.DocumentRepository

class TabletApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: DocumentRepository by lazy {
        DocumentRepository(database.documentDao(), database.annotationDao(), database.collectionDao(), filesDir)
    }

    override fun onCreate() {
        super.onCreate()
        // Debug only: lets the sign-in WebView be driven over Chrome DevTools for testing.
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
    }
}
