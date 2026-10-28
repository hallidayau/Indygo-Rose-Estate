package au.halliday.clipsync

import android.net.Uri
import android.util.Patterns

object Links {
    /** Returns an openable URL if the whole text is a single link. */
    fun urlOf(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        if (!Patterns.WEB_URL.matcher(trimmed).matches()) return null
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }

    fun host(url: String): String = Uri.parse(url).host?.removePrefix("www.") ?: url
}
