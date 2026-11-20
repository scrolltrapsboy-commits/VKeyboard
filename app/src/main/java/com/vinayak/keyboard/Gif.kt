package com.vinayak.keyboard

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import android.widget.ImageView
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class GifItem(val thumb: String, val gif: String, val page: String, val ratio: Float)

object Net {
    val pool = Executors.newFixedThreadPool(4)
    val main = Handler(Looper.getMainLooper())

    fun get(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "VKeyboard/1.0")
        try {
            if (c.responseCode != 200) throw java.io.IOException("HTTP " + c.responseCode)
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }
}

/** GIF + sticker search through the Giphy API (needs a free key, entered in settings). */
object Giphy {
    fun fetch(key: String, sticker: Boolean, q: String?, cb: (List<GifItem>?, String?) -> Unit) {
        Net.pool.execute {
            try {
                val kind = if (sticker) "stickers" else "gifs"
                val url = "https://api.giphy.com/v1/$kind/" +
                    (if (q.isNullOrBlank()) "trending?" else "search?q=" + URLEncoder.encode(q, "UTF-8") + "&") +
                    "api_key=" + URLEncoder.encode(key, "UTF-8") + "&limit=36&rating=pg-13"
                val arr = JSONObject(String(Net.get(url))).getJSONArray("data")
                val out = ArrayList<GifItem>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val im = o.getJSONObject("images")
                    val th = im.optJSONObject("fixed_width_small_still") ?: im.optJSONObject("fixed_width_still") ?: continue
                    val g = im.optJSONObject("fixed_width") ?: continue
                    val w = th.optString("width", "1").toFloatOrNull() ?: 1f
                    val h = th.optString("height", "1").toFloatOrNull() ?: 1f
                    out.add(GifItem(th.getString("url"), g.getString("url"), o.optString("url", ""), if (w > 0) h / w else 1f))
                }
                Net.main.post { cb(out, null) }
            } catch (e: Exception) {
                Net.main.post { cb(null, e.message ?: "Network error") }
            }
        }
    }

    /** Downloads the GIF into the cache and returns a content:// uri other apps can read. */
    fun download(ctx: Context, item: GifItem, cb: (Uri?) -> Unit) {
        Net.pool.execute {
            try {
                val dir = File(ctx.cacheDir, "gifs")
                dir.mkdirs()
                val name = "g" + Integer.toHexString(item.gif.hashCode()) + ".gif"
                val f = File(dir, name)
                if (!f.exists()) f.writeBytes(Net.get(item.gif))
                Net.main.post { cb(GifProvider.uriFor(name)) }
            } catch (e: Exception) {
                Net.main.post { cb(null) }
            }
        }
    }
}

object ImageLoader {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(url: String, iv: ImageView) {
        iv.tag = url
        val hit = cache.get(url)
        if (hit != null) { iv.setImageBitmap(hit); return }
        iv.setImageDrawable(null)
        Net.pool.execute {
            try {
                val bytes = Net.get(url)
                val b = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (b != null) {
                    cache.put(url, b)
                    Net.main.post { if (iv.tag == url) iv.setImageBitmap(b) }
                }
            } catch (_: Exception) {
            }
        }
    }
}

class GifProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    private fun file(uri: Uri): File? {
        val name = uri.lastPathSegment ?: return null
        val dir = File(context?.cacheDir ?: return null, "gifs")
        val f = File(dir, name)
        return if (f.exists() && f.parentFile == dir) f else null
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val f = file(uri) ?: return null
        val c = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
        c.addRow(arrayOf<Any>(f.name, f.length()))
        return c
    }

    override fun getType(uri: Uri): String = "image/gif"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val f = file(uri) ?: throw FileNotFoundException()
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    companion object {
        const val AUTH = "com.vinayak.keyboard.files"
        fun uriFor(name: String): Uri = Uri.parse("content://$AUTH/gifs/$name")
    }
}
