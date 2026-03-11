package dev.milinko.workoutapp.exercise

import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.acos
import kotlin.math.sqrt

class PushUpAnalyzer : ExerciseAnalyzer {
    private var lastDown = false
    private var count = 0
    private val angleBuffer = mutableListOf<Double>()
    private val BUFFER_SIZE = 3

    override fun analyze(poseLandmarks: Map<Int, PoseLandmark>): ExerciseResult {
        val leftShoulder = poseLandmarks[PoseLandmark.LEFT_SHOULDER]
        val leftElbow = poseLandmarks[PoseLandmark.LEFT_ELBOW]
        val leftWrist = poseLandmarks[PoseLandmark.LEFT_WRIST]

        val rightShoulder = poseLandmarks[PoseLandmark.RIGHT_SHOULDER]
        val rightElbow = poseLandmarks[PoseLandmark.RIGHT_ELBOW]
        val rightWrist = poseLandmarks[PoseLandmark.RIGHT_WRIST]

        // Biramo stranu koja je bolje detektovana (veći in-frame confidence)
        val leftConfidence = (leftShoulder?.inFrameLikelihood ?: 0f) + 
                         (leftElbow?.inFrameLikelihood ?: 0f) + 
                         (leftWrist?.inFrameLikelihood ?: 0f)
        
        val rightConfidence = (rightShoulder?.inFrameLikelihood ?: 0f) + 
                          (rightElbow?.inFrameLikelihood ?: 0f) + 
                          (rightWrist?.inFrameLikelihood ?: 0f)

        val (shoulder, elbow, wrist, hip, knee) = if (leftConfidence >= rightConfidence) {
            val h = poseLandmarks[PoseLandmark.LEFT_HIP]
            val k = poseLandmarks[PoseLandmark.LEFT_KNEE]
            listOf(leftShoulder, leftElbow, leftWrist, h, k)
        } else {
            val h = poseLandmarks[PoseLandmark.RIGHT_HIP]
            val k = poseLandmarks[PoseLandmark.RIGHT_KNEE]
            listOf(rightShoulder, rightElbow, rightWrist, h, k)
        }

        // Provera da li je ceo korisnik u frejmu (bar ključne tačke za sklek)
        // Za sklekove su kritični rame, lakat, zglob ruke, kuk i bar koleno da bi se videla forma
        val isUserInFrame = shoulder != null && elbow != null && wrist != null && hip != null && knee != null
        
        if (!isUserInFrame) {
            return ExerciseResult(count, false, 0.0, isUserInFrame = false)
        }

        // Sigurni smo da nisu null zbog isUserInFrame provere
        val rawAngle = calculateAngle(shoulder!!, elbow!!, wrist!!)
        
        // Smoothing
        angleBuffer.add(rawAngle)
        if (angleBuffer.size > BUFFER_SIZE) {
            angleBuffer.removeAt(0)
        }
        val angle = angleBuffer.average()

        if (angle < 70) {
            lastDown = true
        } else if (angle > 160 && lastDown) {
            count++
            lastDown = false
        }

        return ExerciseResult(count, angle in 60.0..175.0, angle, isUserInFrame = true)
    }

    override fun reset() {
        count = 0
        lastDown = false
        angleBuffer.clear()
    }

    private fun calculateAngle(a: PoseLandmark, b: PoseLandmark, c: PoseLandmark): Double {
        val abX = a.position.x - b.position.x
        val abY = a.position.y - b.position.y
        val cbX = c.position.x - b.position.x
        val cbY = c.position.y - b.position.y

        val dot = abX * cbX + abY * cbY
        val magAB = sqrt(abX * abX + abY * abY)
        val magCB = sqrt(cbX * cbX + cbY * cbY)

        return Math.toDegrees(acos(dot / (magAB * magCB)).toDouble())
    }
}
