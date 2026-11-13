package com.refmanager.tablet.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Mendeley's current backend, as used by Mendeley Reference Manager itself:
 * the user signs in on www.mendeley.com (cookie session), and the app exchanges that
 * session for an API access token at /reference-manager-desktop/refresh-token.
 * The token then authorizes the ordinary public API at api.mendeley.com.
 *
 * This class only stores the token; the sign-in happens in [com.refmanager.tablet.ui.MendeleyAuthActivity].
 */
object MendeleyAuth {
    private const val PREFS = "mendeley_auth"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_TOKEN_AT = "access_token_at"

    const val SIGN_IN_URL =
        "https://www.mendeley.com/sign/in/?routeTo=https%3A%2F%2Fwww.mendeley.com%2Freference-manager-desktop%2F"

    const val REFRESH_TOKEN_URL =
        "https://www.mendeley.com/reference-manager-desktop/refresh-token"

    private fun prefs(context: Context) = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private const val KEY_SIGNED_IN = "signed_in"

    fun isSignedIn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SIGNED_IN, false) ||
            prefs(context).getString(KEY_TOKEN, null) != null

    fun token(context: Context): String? {
        val prefs = prefs(context)
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val savedAt = prefs.getLong(KEY_TOKEN_AT, 0L)
        // Tokens live about an hour; refresh well before that.
        if (System.currentTimeMillis() - savedAt > 45 * 60 * 1000L) return null
        return token
    }

    fun saveToken(context: Context, token: String) {
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_TOKEN_AT, System.currentTimeMillis())
            .apply()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SIGNED_IN, true).apply()
    }

    fun signOut(context: Context) {
        prefs(context).edit().clear().apply()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SIGNED_IN, false).apply()
    }
}

data class RemoteDocument(
    val id: String,
    val title: String,
    val authors: String,
    val year: Int?,
    val journal: String,
    val doi: String,
    val docType: String = "Journal Article",
    val abstractText: String = "",
    val tags: List<String> = emptyList(),
    val starred: Boolean = false,
    val authored: Boolean = false,
    val folderUuids: List<String> = emptyList()
)

data class RemoteFolder(
    val id: String,
    val name: String,
    val parentId: String? = null
)

data class RemoteFile(
    val id: String,
    val documentId: String,
    val mimeType: String,
    val fileName: String
)

/**
 * Minimal client for api.mendeley.com. Uses HttpURLConnection so the release APK gains
 * no new libraries; the API is small enough that this stays readable.
 */
class MendeleyClient(private val token: String) {

    /** All documents in the signed-in user's library, following the Link: rel="next" pages. */
    fun documents(): List<RemoteDocument> {
        var url = "$API/documents?limit=100&view=all"
        val out = ArrayList<RemoteDocument>()
        var guard = 0
        while (url.isNotEmpty() && guard++ < 200) {
            val (body, headers) = get(url)
            val array = JSONArray(body)
            for (i in 0 until array.length()) {
                out += parseDocument(array.getJSONObject(i))
            }
            url = nextLink(headers["link"]) ?: ""
        }
        return out
    }

    /** All folders in the signed-in user's Mendeley library. */
    fun folders(): List<RemoteFolder> {
        var url = "$API/folders?limit=100"
        val out = ArrayList<RemoteFolder>()
        var guard = 0
        while (url.isNotEmpty() && guard++ < 50) {
            val (body, headers) = get(url, "application/vnd.mendeley-folder.1+json, application/json")
            val array = JSONArray(body)
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                val id = json.optString("id").takeIf { it.isNotBlank() } ?: continue
                val name = json.optString("name").ifBlank { "Untitled Folder" }
                val parentId = json.optString("parent_id").takeIf { it.isNotBlank() }
                out += RemoteFolder(id = id, name = name, parentId = parentId)
            }
            url = nextLink(headers["link"]) ?: ""
        }
        android.util.Log.d("MendeleyClient", "Fetched ${out.size} folders")
        return out
    }

    /** Document IDs belonging to a Mendeley folder. */
    fun folderDocumentIds(folderId: String): Set<String> {
        var url = "$API/folders/${enc(folderId)}/documents?limit=100"
        val out = LinkedHashSet<String>()
        var guard = 0
        while (url.isNotEmpty() && guard++ < 100) {
            val (body, headers) = get(url, "application/vnd.mendeley-document.1+json, application/json")
            val array = JSONArray(body)
            for (i in 0 until array.length()) {
                val id = array.optJSONObject(i)?.optString("id").orEmpty()
                if (id.isNotBlank()) out += id
            }
            url = nextLink(headers["link"]) ?: ""
        }
        android.util.Log.d("MendeleyClient", "Folder $folderId has ${out.size} docs")
        return out
    }

    /**
     * Every file in the signed-in user's library, keyed by document on the caller's
     * side. One paginated listing beats one request per document.
     */
    fun allFiles(): List<RemoteFile> {
        var url = "$API/files?limit=100"
        val out = ArrayList<RemoteFile>()
        var guard = 0
        while (url.isNotEmpty() && guard++ < 100) {
            val (body, headers) = get(url, "application/vnd.mendeley-file.1+json")
            val array = JSONArray(body)
            for (index in 0 until array.length()) {
                val json = array.getJSONObject(index)
                val id = json.optString("id").takeIf { it.isNotBlank() } ?: continue
                out += RemoteFile(
                    id = id,
                    documentId = json.optString("document_id"),
                    mimeType = json.optString("mime_type"),
                    fileName = json.optString("file_name").ifBlank { "document.pdf" }
                )
            }
            url = nextLink(headers["link"]) ?: ""
        }
        return out
    }

    /**
     * Downloads a file. The API answers 303 with a pre-signed S3 URL, and S3 rejects
     * requests that also carry an Authorization header, so the redirect is followed
     * manually without it.
     */
    fun download(fileId: String, destination: File) {
        val first = (URL("$API/files/${enc(fileId)}").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/pdf")
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 60_000
        }
        try {
            val code = first.responseCode
            if (code !in 300..399) error("File request failed ($code)")
            val location = first.getHeaderField("Location") ?: error("No download location")
            first.disconnect()

            val second = (URL(location).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 120_000
            }
            try {
                if (second.responseCode !in 200..299) error("Download failed (${second.responseCode})")
                second.inputStream.use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
            } finally {
                second.disconnect()
            }
        } finally {
            first.disconnect()
        }
    }

    private fun get(
        url: String,
        accept: String = "application/vnd.mendeley-document.1+json"
    ): Pair<String, Map<String, String>> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", accept)
            connectTimeout = 20_000
            readTimeout = 30_000
        }
        try {
            val code = connection.responseCode
            if (code == 401) throw UnauthorizedException()
            if (code !in 200..299) {
                val text = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("Mendeley API $code: ${text.take(200)}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val headers = buildMap {
                connection.headerFields.forEach { (key, values) ->
                    if (key != null && values != null) put(key.lowercase(), values.joinToString(","))
                }
            }
            return body to headers
        } finally {
            connection.disconnect()
        }
    }

    private fun parseDocument(json: JSONObject): RemoteDocument {
        val authors = json.optJSONArray("authors")?.let { array ->
            (0 until array.length()).joinToString(", ") { index ->
                val author = array.optJSONObject(index) ?: return@joinToString ""
                listOf(author.optString("first_name"), author.optString("last_name"))
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            }.trim(',', ' ')
        }.orEmpty()

        val rawType = json.optString("type")
        val docType = when (rawType.lowercase()) {
            "journal", "journal_article", "" -> "Journal Article"
            "book" -> "Book"
            "book_section" -> "Book Chapter"
            "conference_proceedings" -> "Conference"
            "thesis" -> "Thesis"
            "report" -> "Report"
            "web_page" -> "Web Page"
            "preprint" -> "Preprint"
            else -> rawType.replace('_', ' ').split(' ')
                .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
                .ifBlank { "Journal Article" }
        }

        val tags = json.optJSONArray("tags")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).trim().takeIf { it.isNotEmpty() } }
        }.orEmpty()
        val folderUuids = json.optJSONArray("folder_uuids")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).trim().takeIf { it.isNotEmpty() } }
        }.orEmpty()

        return RemoteDocument(
            id = json.optString("id"),
            title = json.optString("title").ifBlank { "Untitled" },
            authors = authors,
            year = if (json.has("year")) json.optInt("year").takeIf { it > 0 } else null,
            journal = json.optString("source"),
            doi = json.optJSONObject("identifiers")?.optString("doi").orEmpty(),
            docType = docType,
            abstractText = json.optString("abstract"),
            tags = tags,
            starred = json.optBoolean("starred", false),
            authored = json.optBoolean("authored", false),
            folderUuids = folderUuids
        )
    }

    private fun nextLink(link: String?): String? {
        if (link.isNullOrBlank()) return null
        val match = Regex("<([^>]+)>\\s*;\\s*rel=\"?next\"?").find(link) ?: return null
        return match.groupValues[1]
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val API = "https://api.mendeley.com"
    }
}

class UnauthorizedException : IllegalStateException("Mendeley session expired")
