package dev.milinko.workoutapp.pose

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.*
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions

class PoseDetectorProcessor {
    companion object {
        private const val TAG = "PoseDetectorProcessor"
    }

    private val detector = PoseDetection.getClient(
        AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
    )

    @OptIn(ExperimentalGetImage::class)
    fun processImage(image: ImageProxy, onResult: (Map<Int, PoseLandmark>) -> Unit) {
        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)

        detector.process(inputImage)
            .addOnSuccessListener { pose ->
                val landmarks = pose.allPoseLandmarks.associateBy { it.landmarkType }
                // Možemo kasnije dodati i PoseWorldLandmarks ako ExerciseAnalyzer bude zahtevao
                onResult(landmarks)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Pose detection failed", e)
            }
            .addOnCompleteListener {
                image.close()
            }
    }
}
