package ch.kudoz.app.data

import ch.kudoz.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/** Prüft https://kudoz.ch/android-version.json auf eine neuere APK. */
object Updates {
    @Serializable
    data class Latest(val versionCode: Int, val versionName: String = "", val url: String = "https://github.com/picts-dotcom/kudoz-android/releases")

    suspend fun check(): Latest? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("${BuildConfig.WEB_URL}/android-version.json").openConnection() as HttpURLConnection
            c.connectTimeout = 5000; c.readTimeout = 5000
            val text = c.inputStream.bufferedReader().use { it.readText() }
            Json { ignoreUnknownKeys = true }.decodeFromString<Latest>(text)
        }.getOrNull()?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    }
}
