package me.terevo.ui.tree

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File
import org.jetbrains.skia.Image as SkiaImage

object ThumbnailCache {
    private val cache = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>): Boolean = size > CAPACITY
    }

    @Synchronized
    fun get(path: String): ImageBitmap? {
        cache[path]?.let { return it }
        val bitmap = runCatching { SkiaImage.makeFromEncoded(File(path).readBytes()).toComposeImageBitmap() }
            .getOrNull() ?: return null
        cache[path] = bitmap
        return bitmap
    }

    private const val CAPACITY: Int = 200
}
