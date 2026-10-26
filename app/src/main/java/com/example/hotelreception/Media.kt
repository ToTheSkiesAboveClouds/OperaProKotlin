package com.example.hotelreception

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// =====================================================================
// Работа с фото и видео: скан паспорта и вложения к запросам.
// Файлы копируются в папку приложения (filesDir/media), а в данных хранится только путь.
// Вызывать эти функции нужно НЕ на главном потоке (см. withContext(Dispatchers.IO) в экранах).
// =====================================================================

private fun mediaDir(context: Context): File = File(context.filesDir, "media").apply { mkdirs() }

// Сохраняет снимок с камеры в JPEG
fun saveBitmap(context: Context, bitmap: Bitmap, prefix: String): String {
    val file = File(mediaDir(context), "${prefix}_${System.nanoTime()}.jpg")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    return file.absolutePath
}

// Копирует выбранный в галерее файл внутрь приложения. Возвращает путь или null при ошибке.
fun copyUriToApp(context: Context, uri: Uri, prefix: String): String? {
    val mime = context.contentResolver.getType(uri).orEmpty()
    val ext = if (mime.startsWith("video")) "mp4" else "jpg"
    val file = File(mediaDir(context), "${prefix}_${System.nanoTime()}.$ext")
    return try {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { source -> file.outputStream().use { target -> source.copyTo(target) } }
        file.absolutePath
    } catch (e: Exception) {
        null
    }
}

fun isVideoPath(path: String): Boolean = path.endsWith(".mp4")

// Читает картинку с уменьшением, чтобы не занимать много памяти
private fun decodeScaled(path: String, maxSize: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, options)
}

// Загружает картинку в фоне и возвращает её, когда готова (пока null)
@Composable
fun rememberImageBitmap(path: String?, maxSize: Int = 1024): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) { decodeScaled(path, maxSize)?.asImageBitmap() }
        }
    }
    return state.value
}
