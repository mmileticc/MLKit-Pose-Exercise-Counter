package dev.milinko.workoutapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.max

/**
 * Crta skelet PREKO live kamere - `sourceWidth`/`sourceHeight` su dimenzije analizirane slike
 * (iz [dev.milinko.workoutapp.pose.PoseDetectorProcessor], već svedene na "upright" orijentaciju)
 * u čijem su koordinatnom sistemu `landmarks` pozicije. Ovaj Canvas se crta preko
 * [CameraPreview]-a koji koristi PreviewView-ov podrazumevani FILL_CENTER scale type (uveličaj
 * dok ne popuni ceo prikaz, centriraj, isečeni viškovi se ne vide) - mapiranje ispod namerno
 * računa istu transformaciju da bi se tačke poklopile sa slikom koju korisnik stvarno vidi.
 * `mirror = true` (prednja kamera) vodi računa o tome da PreviewView front-kameru sam ogleda
 * horizontalno, dok sirovi landmarci iz ImageAnalysis-a NISU ogledani.
 */
@Composable
fun PoseOverlay(
    landmarks: Map<Int, PoseLandmark>,
    sourceWidth: Int,
    sourceHeight: Int,
    mirror: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || size.width <= 0f || size.height <= 0f) {
            return@Canvas
        }

        // FILL_CENTER: skaliraj tako da slika popuni ceo Canvas (veći od dva scale faktora),
        // pa centriraj - isto ponašanje kao PreviewView-ov podrazumevani scale type.
        val scale = max(size.width / sourceWidth.toFloat(), size.height / sourceHeight.toFloat())
        val offsetX = (size.width - sourceWidth * scale) / 2f
        val offsetY = (size.height - sourceHeight * scale) / 2f

        fun mapPoint(x: Float, y: Float): Offset {
            val scaledX = x * scale + offsetX
            val scaledY = y * scale + offsetY
            return Offset(if (mirror) size.width - scaledX else scaledX, scaledY)
        }

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
                    center = mapPoint(landmark.position.x, landmark.position.y)
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
                    start = mapPoint(start.position.x, start.position.y),
                    end = mapPoint(end.position.x, end.position.y),
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
                        center = mapPoint(landmark.position.x, landmark.position.y)
                    )
                }
            }
        }
    }
}
