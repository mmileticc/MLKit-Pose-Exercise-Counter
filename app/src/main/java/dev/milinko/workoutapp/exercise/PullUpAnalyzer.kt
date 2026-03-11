package dev.milinko.workoutapp.exercise

import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.acos
import kotlin.math.sqrt

class PullUpAnalyzer : ExerciseAnalyzer {
    private var lastUp = false
    private var count = 0
    private val angleBuffer = mutableListOf<Double>()
    private val BUFFER_SIZE = 8
    private var lastSmoothAngle = 0.0
    private val ALPHA = 0.2

    override fun analyze(poseLandmarks: Map<Int, PoseLandmark>): ExerciseResult {
        val leftShoulder = poseLandmarks[PoseLandmark.LEFT_SHOULDER]
        val leftElbow = poseLandmarks[PoseLandmark.LEFT_ELBOW]
        val leftWrist = poseLandmarks[PoseLandmark.LEFT_WRIST]
        val rightShoulder = poseLandmarks[PoseLandmark.RIGHT_SHOULDER]
        val rightElbow = poseLandmarks[PoseLandmark.RIGHT_ELBOW]
        val rightWrist = poseLandmarks[PoseLandmark.RIGHT_WRIST]

        // Za zgibove je bitnije da vidimo gornji deo tela
        val leftConf = (leftShoulder?.inFrameLikelihood ?: 0f) + (leftElbow?.inFrameLikelihood ?: 0f) + (leftWrist?.inFrameLikelihood ?: 0f)
        val rightConf = (rightShoulder?.inFrameLikelihood ?: 0f) + (rightElbow?.inFrameLikelihood ?: 0f) + (rightWrist?.inFrameLikelihood ?: 0f)

        val (shoulder, elbow, wrist) = if (leftConf >= rightConf) {
            listOf(leftShoulder, leftElbow, leftWrist)
        } else {
            listOf(rightShoulder, rightElbow, rightWrist)
        }

        // Minimalni uslov: Rame, lakat i zglob ruke
        if (shoulder == null || elbow == null || wrist == null) {
            return ExerciseResult(count, false, 0.0, isUserInFrame = false, visibilityMessage = "STANI U KADAR (RUKE I RAMENA)")
        }

        val angle = calculateAngle(shoulder, elbow, wrist)
        
        // Smoothing
        angleBuffer.add(angle)
        if (angleBuffer.size > BUFFER_SIZE) angleBuffer.removeAt(0)
        val averageAngle = angleBuffer.average()
        
        val smoothAngle = if (lastSmoothAngle == 0.0) {
            averageAngle
        } else {
            (ALPHA * averageAngle) + ((1 - ALPHA) * lastSmoothAngle)
        }
        lastSmoothAngle = smoothAngle

        // Logika za zgib:
        // Gore (brada iznad šipke): Ugao u laktu je mali (npr. < 60 stepeni)
        // Dole (ruke opružene): Ugao u laktu je veliki (npr. > 150 stepeni)
        if (smoothAngle < 65) {
            lastUp = true
        } else if (smoothAngle > 150 && lastUp) {
            count++
            lastUp = false
        }

        return ExerciseResult(
            count = count,
            isCorrectForm = smoothAngle in 45.0..180.0,
            currentAngle = smoothAngle,
            isUserInFrame = true,
            visibilityMessage = if (shoulder.position.y > wrist.position.y) "ŠIPKA TREBA DA BUDE IZNAD GLAVE" else null
        )
    }

    override fun reset() {
        count = 0
        lastUp = false
        angleBuffer.clear()
        lastSmoothAngle = 0.0
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
