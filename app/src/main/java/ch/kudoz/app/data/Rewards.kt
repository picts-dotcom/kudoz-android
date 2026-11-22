package ch.kudoz.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Belohnungsbilder (200, lokal wie im Web) mit Namen und den 10 Sammel-Kategorien. */
object Rewards {
    @Serializable data class Category(val id: String, val label: String, val items: List<Int>)
    @Serializable private data class Catalog(val labels: List<String>, val categories: List<Category>)

    private var catalog: Catalog? = null
    private val cache = LruCache<Int, Bitmap>(80)

    private fun load(context: Context): Catalog = catalog ?: Json { ignoreUnknownKeys = true }
        .decodeFromString<Catalog>(context.assets.open("rewards.json").bufferedReader().use { it.readText() })
        .also { catalog = it }

    fun categories(context: Context) = load(context).categories
    fun label(context: Context, id: Int) = load(context).labels.getOrNull(id) ?: "Belohnung"

    fun image(context: Context, id: Int): Bitmap? {
        cache.get(id)?.let { return it }
        return runCatching {
            context.assets.open("rewards/%03d.webp".format(id)).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()?.also { cache.put(id, it) }
    }
}
