package com.example.shoppingassistant.feature.pages.draft.create

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.shoppingassistant.domain.ugc.draft.DraftMedia
import com.example.shoppingassistant.domain.ugc.draft.DraftMediaStatus
import com.example.shoppingassistant.domain.ugc.draft.DraftMediaType
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DraftMediaImporter(
    private val context: Context,
) {
    suspend fun importPhoto(uri: Uri): DraftMedia? = withContext(Dispatchers.IO) {
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.readBytes() }.getOrNull()
            ?: return@withContext null
        val bitmap = decodeBitmap(bytes) ?: return@withContext null
        val resized = resizeBitmap(bitmap)
        buildPhotoMedia(resized)
    }

    suspend fun importBitmap(bitmap: Bitmap): DraftMedia? = withContext(Dispatchers.IO) {
        val resized = resizeBitmap(bitmap)
        buildPhotoMedia(resized)
    }

    suspend fun importVideo(uri: Uri): DraftMedia? = withContext(Dispatchers.IO) {
        val dir = ensureDir()
        val file = File(dir, "video_${System.currentTimeMillis()}.mp4")
        val digest = MessageDigest.getInstance("SHA-256")
        val output = FileOutputStream(file)
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
        input.use { stream ->
            output.use { out ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var read = stream.read(buffer)
                while (read >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read)
                        out.write(buffer, 0, read)
                    }
                    read = stream.read(buffer)
                }
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        DraftMedia(
            id = hash,
            type = DraftMediaType.VIDEO,
            localUri = file.toURI().toString(),
            status = DraftMediaStatus.LOCAL_ONLY,
            byteSize = file.length(),
        )
    }

    private fun buildPhotoMedia(bitmap: Bitmap): DraftMedia? {
        val bytes = ByteArrayOutputStream().use { stream ->
            val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, stream)
            if (!ok) return null
            stream.toByteArray()
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val dir = ensureDir()
        val file = File(dir, "photo_${System.currentTimeMillis()}_${hash.take(8)}.jpg")
        FileOutputStream(file).use { it.write(bytes) }
        return DraftMedia(
            id = hash,
            type = DraftMediaType.PHOTO,
            localUri = file.toURI().toString(),
            status = DraftMediaStatus.LOCAL_ONLY,
            width = bitmap.width,
            height = bitmap.height,
            byteSize = bytes.size.toLong(),
        )
    }

    private fun ensureDir(): File {
        val dir = File(context.filesDir, MEDIA_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun decodeBitmap(bytes: ByteArray): Bitmap? =
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

    private fun resizeBitmap(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= MAX_SIDE_PX) return bitmap
        val scale = MAX_SIDE_PX.toFloat() / maxSide.toFloat()
        val targetW = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private companion object {
        const val MEDIA_DIR = "ugc_media"
        const val MAX_SIDE_PX = 2048
        const val PHOTO_QUALITY = 92
    }
}
