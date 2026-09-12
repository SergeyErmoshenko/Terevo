package me.terevo.ui.export

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.layout.Layout
import me.terevo.layout.Point
import me.terevo.layout.Rect
import me.terevo.ui.theme.TerevoColors
import me.terevo.ui.theme.TerevoTheme
import me.terevo.ui.tree.Camera
import me.terevo.ui.tree.TreeHighlight
import me.terevo.ui.tree.TreeVisuals
import me.terevo.ui.tree.drawTree
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.io.IOException

const val EXPORT_MIN_SCALE: Double = 0.3

@OptIn(ExperimentalComposeUiApi::class)
fun exportTreePng(
    layout: Layout,
    visuals: TreeVisuals,
    colors: TerevoColors,
    bounds: Rect,
    scale: Double,
    path: String,
): Outcome<Unit> {
    if (bounds.width <= 0.0 || bounds.height <= 0.0) {
        return Outcome.Err(DomainError.Storage.Failure("Пустая область экспорта"))
    }
    val effectiveScale = scale.coerceAtLeast(EXPORT_MIN_SCALE)
    val width = (bounds.width * effectiveScale).toInt().coerceAtLeast(1)
    val height = (bounds.height * effectiveScale).toInt().coerceAtLeast(1)
    val camera = Camera(
        scale = effectiveScale,
        offset = Point(x = -bounds.left * effectiveScale, y = -bounds.top * effectiveScale),
    )
    val scene = ImageComposeScene(width, height) {
        val textMeasurer = rememberTextMeasurer()
        val cornerRadiusPx = with(LocalDensity.current) { TerevoTheme.spacing.cornerRadius.toPx() }
        val density = LocalDensity.current.density
        Canvas(Modifier.size(width.dp, height.dp)) {
            drawRect(color = colors.canvas, size = size)
            drawTree(layout, visuals, camera, colors, textMeasurer, TreeHighlight.NONE, cornerRadiusPx, density)
        }
    }
    return try {
        val bytes = scene.render(0L)
            .encodeToData(EncodedImageFormat.PNG, PNG_QUALITY)
            ?.bytes
            ?: return Outcome.Err(DomainError.Storage.Failure("Не удалось закодировать изображение"))
        File(path).writeBytes(bytes)
        Outcome.Ok(Unit)
    } catch (failure: IOException) {
        Outcome.Err(DomainError.Storage.Failure(failure.message.orEmpty()))
    } finally {
        scene.close()
    }
}

private const val PNG_QUALITY: Int = 100
