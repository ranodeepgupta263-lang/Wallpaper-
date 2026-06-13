package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object ImageSegmenterHelper {

    suspend fun segmentImage(context: Context, imageUri: Uri): Pair<String, String> = suspendCancellableCoroutine { continuation ->
        try {
            val contentResolver = context.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: throw Exception("Failed to decode image bitmap")
            inputStream?.close()

            // Prepare ML Kit input image
            val inputImage = InputImage.fromBitmap(originalBitmap, 0)

            // Setup Options: enable foreground bitmap
            val options = SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build()

            val segmenter = SubjectSegmentation.getClient(options)
            segmenter.process(inputImage)
                .addOnSuccessListener { result ->
                    val foregroundBitmap = result.foregroundBitmap
                    if (foregroundBitmap != null) {
                        try {
                            // Save original source image as background in internal files
                            val bgFile = File(context.filesDir, "custom_bg.png")
                            FileOutputStream(bgFile).use { out ->
                                originalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }

                            // Save transparent cutout as foreground in internal file
                            val fgFile = File(context.filesDir, "custom_fg.png")
                            FileOutputStream(fgFile).use { out ->
                                foregroundBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }

                            continuation.resume(Pair(bgFile.absolutePath, fgFile.absolutePath))
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        }
                    } else {
                        continuation.resumeWithException(Exception("Segmentation completed but no foreground subject was detected."))
                    }
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }

    suspend fun saveVideo(context: Context, videoUri: Uri): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val resolver = context.contentResolver
        val videoFile = File(context.filesDir, "custom_video.mp4")
        resolver.openInputStream(videoUri)?.use { inputStream ->
            FileOutputStream(videoFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw Exception("Failed to open video stream")
        videoFile.absolutePath
    }
}
