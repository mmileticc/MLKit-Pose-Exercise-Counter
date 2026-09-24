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
    fun processImage(
        image: ImageProxy,
        onResult: (landmarks: Map<Int, PoseLandmark>, imageWidth: Int, imageHeight: Int) -> Unit
    ) {
        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            return
        }

        val rotationDegrees = image.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        // ML Kit rotates the buffer internally before running inference (that's what the
        // rotationDegrees argument above is for), and PoseLandmark.position comes back already
        // expressed in that UPRIGHT coordinate frame - so the reference dimensions we hand back
        // for overlay scaling need the same swap (same pattern as ML Kit's own quickstart
        // sample's GraphicOverlay.setImageSourceInfo).
        val imageWidth = if (rotationDegrees == 90 || rotationDegrees == 270) mediaImage.height else mediaImage.width
        val imageHeight = if (rotationDegrees == 90 || rotationDegrees == 270) mediaImage.width else mediaImage.height

        detector.process(inputImage)
            .addOnSuccessListener { pose ->
                val landmarks = pose.allPoseLandmarks.associateBy { it.landmarkType }
                // Možemo kasnije dodati i PoseWorldLandmarks ako ExerciseAnalyzer bude zahtevao
                onResult(landmarks, imageWidth, imageHeight)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Pose detection failed", e)
            }
            .addOnCompleteListener {
                image.close()
            }
    }
}
