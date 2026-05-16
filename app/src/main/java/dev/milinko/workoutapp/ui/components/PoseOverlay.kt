package dev.milinko.workoutapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.google.mlkit.vision.pose.PoseLandmark

@Composable
fun PoseOverlay(
    landmarks: Map<Int, PoseLandmark>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        // Prag za inFrameLikelihood - samo lanmarki sa većom pouzdanošću se crtaju
        val CONFIDENCE_THRESHOLD = 0.5f

        // Crtamo krugove samo za lanmarke koji su dovoljno vidljivi
        landmarks.values.forEach { landmark ->
            if (landmark.inFrameLikelihood > CONFIDENCE_THRESHOLD) {
                // Alpha zavisi od inFrameLikelihood - što je manja sigurnost, linija je prozirnija
                val alpha = landmark.inFrameLikelihood.coerceIn(0f, 1f)
                drawCircle(
                    color = Color.White.copy(alpha = alpha * 0.5f),
                    radius = 6f,
                    center = Offset(landmark.position.x, landmark.position.y)
                )
            }
        }

        // Definišemo parove tačaka koje treba povezati (kostur)
        val connections = listOf(
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
            PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
            PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE
        )

        // Crtaj linije samo ako su oba landmarka dovoljno vidljiva
        // Alpha linija zavisi od minimalne pouzdanosti dva landmarka
        connections.forEach { (startType, endType) ->
            val start = landmarks[startType]
            val end = landmarks[endType]
            if (start != null && end != null &&
                start.inFrameLikelihood > CONFIDENCE_THRESHOLD &&
                end.inFrameLikelihood > CONFIDENCE_THRESHOLD
            ) {
                // Koristi minimalnu vrednost inFrameLikelihood od oba landmarka za alpha
                val minConfidence = minOf(start.inFrameLikelihood, end.inFrameLikelihood)
                val lineAlpha = minConfidence.coerceIn(0f, 1f)

                drawLine(
                    color = Color.Cyan.copy(alpha = lineAlpha),
                    start = Offset(start.position.x, start.position.y),
                    end = Offset(end.position.x, end.position.y),
                    strokeWidth = 6f
                )
            }
        }
        
        // Istaknimo zglobove koji se analiziraju za sklekove/trakcije
        val activeLandmarks = listOf(
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST
        )
        
        activeLandmarks.forEach { type ->
            landmarks[type]?.let { landmark ->
                if (landmark.inFrameLikelihood > CONFIDENCE_THRESHOLD) {
                    val alpha = landmark.inFrameLikelihood.coerceIn(0f, 1f)
                    drawCircle(
                        color = Color.Yellow.copy(alpha = alpha),
                        radius = 10f,
                        center = Offset(landmark.position.x, landmark.position.y)
                    )
                }
            }
        }
    }
}

