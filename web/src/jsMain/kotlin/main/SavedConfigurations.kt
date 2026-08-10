/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.main

import kotlinx.browser.window
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.js.Date

internal const val SAVED_CONFIGURATION_FORMAT_VERSION = 1
internal const val SAVED_CONFIGURATION_KEY_PREFIX = "polyhedra-explorer.save.v1."

@Serializable
internal data class SavedConfiguration(
    val formatVersion: Int = SAVED_CONFIGURATION_FORMAT_VERSION,
    val id: String,
    val name: String,
    val savedAtEpochMillis: Long,
    /** Exact compact state used after `#/` in application URLs. */
    val urlState: String,
    val previewDataUrl: String,
)

internal interface SavedConfigurationStorage {
    val length: Int
    fun key(index: Int): String?
    fun getItem(key: String): String?
    fun setItem(key: String, value: String)
}

private object BrowserSavedConfigurationStorage : SavedConfigurationStorage {
    override val length: Int get() = window.localStorage.length
    override fun key(index: Int): String? = window.localStorage.key(index)
    override fun getItem(key: String): String? = window.localStorage.getItem(key)
    override fun setItem(key: String, value: String) = window.localStorage.setItem(key, value)
}

internal class SavedConfigurationStore(
    private val storage: SavedConfigurationStorage = BrowserSavedConfigurationStorage,
    private val now: () -> Long = { Date.now().toLong() },
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun load(): List<SavedConfiguration> = runCatching {
        buildList {
            for (index in 0 until storage.length) {
                val key = storage.key(index) ?: continue
                if (!key.startsWith(SAVED_CONFIGURATION_KEY_PREFIX)) continue
                val raw = storage.getItem(key) ?: continue
                val saved = runCatching { json.decodeFromString<SavedConfiguration>(raw) }.getOrNull() ?: continue
                if (
                    saved.formatVersion == SAVED_CONFIGURATION_FORMAT_VERSION &&
                    saved.name.isNotBlank() &&
                    saved.previewDataUrl.startsWith("data:image/")
                ) add(saved)
            }
        }.sortedWith(
            compareByDescending<SavedConfiguration> { it.savedAtEpochMillis }
                .thenByDescending { it.id.substringAfter('-', "0").toIntOrNull() ?: 0 }
        )
    }.getOrDefault(emptyList())

    fun save(name: String, urlState: String, previewDataUrl: String): SavedConfiguration {
        require(previewDataUrl.startsWith("data:image/")) { "A rendered preview is required" }
        val savedAt = now()
        var sequence = 0
        var id: String
        var key: String
        do {
            id = if (sequence == 0) savedAt.toString() else "$savedAt-$sequence"
            key = "$SAVED_CONFIGURATION_KEY_PREFIX$id"
            sequence++
        } while (storage.getItem(key) != null)
        val saved = SavedConfiguration(
            id = id,
            name = name.trim().ifEmpty { "Untitled polyhedron" },
            savedAtEpochMillis = savedAt,
            urlState = urlState,
            previewDataUrl = previewDataUrl,
        )
        storage.setItem(key, json.encodeToString(saved))
        return saved
    }
}

internal fun relativeSavedTime(savedAtEpochMillis: Long, nowEpochMillis: Long): String {
    val seconds = ((nowEpochMillis - savedAtEpochMillis).coerceAtLeast(0L) / 1_000L).toInt()
    return when {
        seconds < 10 -> "just now"
        seconds < 60 -> "$seconds sec ago"
        seconds < 120 -> "1 min ago"
        seconds < 3_600 -> "${seconds / 60} min ago"
        seconds < 7_200 -> "1 hour ago"
        seconds < 86_400 -> "${seconds / 3_600} hours ago"
        seconds < 172_800 -> "yesterday"
        seconds < 2_592_000 -> "${seconds / 86_400} days ago"
        seconds < 5_184_000 -> "1 month ago"
        seconds < 31_536_000 -> "${seconds / 2_592_000} months ago"
        seconds < 63_072_000 -> "1 year ago"
        else -> "${seconds / 31_536_000} years ago"
    }
}

internal fun loadSavedConfiguration(urlState: String) {
    window.location.hash = "/$urlState"
    window.location.reload()
}
