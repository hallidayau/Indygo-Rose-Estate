package au.halliday.clipsync

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Minimal client for the free ntfy.sh relay. Everything sent is already encrypted. */
object Ntfy {
    const val BASE_URL = "https://ntfy.sh"

    fun publishMessage(topic: String, message: String) {
        send("POST", "$BASE_URL/$topic", message.toByteArray(Charsets.UTF_8), emptyMap())
    }

    fun publishAttachment(topic: String, message: String, data: ByteArray) {
        send("PUT", "$BASE_URL/$topic", data, mapOf("X-Message" to message, "X-Filename" to "clip.bin"))
    }

    fun download(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        try {
            if (c.responseCode !in 200..299) throw IOException("Download failed (${c.responseCode})")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    private fun send(method: String, url: String, body: ByteArray, headers: Map<String, String>) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.doOutput = true
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            c.setFixedLengthStreamingMode(body.size)
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            if (code !in 200..299) {
                val err = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("Relay error $code ${err.take(200)}")
            }
        } finally {
            c.disconnect()
        }
    }
}
