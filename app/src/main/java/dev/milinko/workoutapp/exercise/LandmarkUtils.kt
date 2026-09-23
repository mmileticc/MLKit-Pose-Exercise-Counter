package dev.milinko.workoutapp.exercise

import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.acos
import kotlin.math.sqrt

/** Minimum confidence a landmark must have before we trust it for angle calculations. */
const val MIN_LANDMARK_CONFIDENCE = 0.25f

fun landmarkConfidence(lm: PoseLandmark?) = lm?.inFrameLikelihood ?: 0f

/** Angle at [b], between rays b->a and b->c, in degrees. */
fun calculateAngle(a: PoseLandmark, b: PoseLandmark, c: PoseLandmark): Double {
    val abX = a.position.x - b.position.x
    val abY = a.position.y - b.position.y
    val cbX = c.position.x - b.position.x
    val cbY = c.position.y - b.position.y

    val dot = abX * cbX + abY * cbY
    val mag = sqrt((abX * abX + abY * abY).toDouble()) * sqrt((cbX * cbX + cbY * cbY).toDouble())
    return if (mag < 1e-6) 180.0 else Math.toDegrees(acos((dot / mag).coerceIn(-1.0, 1.0)))
}

/**
 * Picks whichever body side (left/right) has better-tracked landmarks this frame, with light
 * hysteresis so it doesn't flip-flop between sides when both are near-equally confident.
 */
class SideSelector {
    private var chosenSide = -1 // -1 unknown, 0 left, 1 right

    fun chooseLeft(leftScore: Float, rightScore: Float): Boolean {
        val useLeft = when (chosenSide) {
            0 -> leftScore >= rightScore - 0.5f
            1 -> leftScore > rightScore + 0.5f
            else -> leftScore >= rightScore
        }
        chosenSide = if (useLeft) 0 else 1
        return useLeft
    }

    fun reset() {
        chosenSide = -1
    }
}
