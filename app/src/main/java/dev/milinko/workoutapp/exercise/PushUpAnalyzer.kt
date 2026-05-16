package dev.milinko.workoutapp.exercise

import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.acos
import kotlin.math.sqrt

class PushUpAnalyzer : ExerciseAnalyzer {
    private var count = 0
    private var isUp = true // Stanje: Gore (ruke opružene)
    private val angleBuffer = mutableListOf<Double>()
    private val BUFFER_SIZE = 6  // Smanjeno sa 8 jer će ViewModel već da filtira
    private var lastSmoothAngle = 0.0
    private val ALPHA = 0.15f  // Smanjeno sa 0.2f jer ViewModel već filtira sa 0.08f

    private var initialShoulderWristDist: Float? = null

    override fun analyze(poseLandmarks: Map<Int, PoseLandmark>): ExerciseResult {
        val leftShoulder = poseLandmarks[PoseLandmark.LEFT_SHOULDER]
        val leftElbow = poseLandmarks[PoseLandmark.LEFT_ELBOW]
        val leftWrist = poseLandmarks[PoseLandmark.LEFT_WRIST]
        val rightShoulder = poseLandmarks[PoseLandmark.RIGHT_SHOULDER]
        val rightElbow = poseLandmarks[PoseLandmark.RIGHT_ELBOW]
        val rightWrist = poseLandmarks[PoseLandmark.RIGHT_WRIST]

        val leftHip = poseLandmarks[PoseLandmark.LEFT_HIP]
        val leftKnee = poseLandmarks[PoseLandmark.LEFT_KNEE]
        val rightHip = poseLandmarks[PoseLandmark.RIGHT_HIP]
        val rightKnee = poseLandmarks[PoseLandmark.RIGHT_KNEE]

        // Provera poverenja (confidence) za obe strane
        val leftConf = (leftShoulder?.inFrameLikelihood ?: 0f) + (leftElbow?.inFrameLikelihood ?: 0f) + (leftWrist?.inFrameLikelihood ?: 0f)
        val rightConf = (rightShoulder?.inFrameLikelihood ?: 0f) + (rightElbow?.inFrameLikelihood ?: 0f) + (rightWrist?.inFrameLikelihood ?: 0f)

        val (shoulder, elbow, wrist, hip, knee) = if (leftConf >= rightConf) {
            listOf(leftShoulder, leftElbow, leftWrist, leftHip, leftKnee)
        } else {
            listOf(rightShoulder, rightElbow, rightWrist, rightHip, rightKnee)
        }

        // Minimalni uslov za brojanje: Rame, lakat i zglob ruke
        val hasArms = shoulder != null && elbow != null && wrist != null
        if (!hasArms) {
            return ExerciseResult(
                count = count,
                isCorrectForm = false,
                currentAngle = 0.0,
                isUserInFrame = false,
                visibilityMessage = "GET IN FRAME (ARMS)",
                areHandsFixed = false
            )
        }

        // Provera da li se vidi donji deo tela (za preciznu formu)
        val hasLowerBody = hip != null && knee != null
        val visibilityMessage = if (!hasLowerBody) {
            "FULL BODY NOT VISIBLE (FORM MAY BE INACCURATE)"
        } else null

        val angle = calculateAngle(shoulder!!, elbow!!, wrist!!)
        
        // Poboljšani smoothing (kombinacija Moving Average i EMA)
        angleBuffer.add(angle)
        if (angleBuffer.size > BUFFER_SIZE) angleBuffer.removeAt(0)
        
        val averageAngle = angleBuffer.average()
        
        // Primena EMA (Exponential Moving Average) za dodatnu stabilnost bez prevelikog laga
        val smoothAngle = if (lastSmoothAngle == 0.0) {
            averageAngle
        } else {
            (ALPHA * averageAngle) + ((1 - ALPHA) * lastSmoothAngle)
        }
        lastSmoothAngle = smoothAngle

        // Rastojanje rame-šaka (za proveru da li se telo zaista spušta ka podu/šakama)
        val currentDist = sqrt(
            Math.pow((shoulder.position.x - wrist.position.x).toDouble(), 2.0) +
            Math.pow((shoulder.position.y - wrist.position.y).toDouble(), 2.0)
        ).toFloat()

        if (isUp && smoothAngle > 155) {
            initialShoulderWristDist = currentDist
        }

        val distRatio = if (initialShoulderWristDist != null) currentDist / initialShoulderWristDist!! else 1.0f

        // DETEKCIJA SKLEKA
        // 1. Spuštanje (DOWN)
        // Uslovi: Ugao < 100 stepeni I rame se približilo šaci za bar 15%
        if (isUp && smoothAngle < 100 && distRatio < 0.85f) {  // Promenjeno: 85→100, 0.80→0.85
            isUp = false
        } 
        // 2. Podizanje (UP) - Kraj ponavljanja
        // Uslovi: Ugao > 130 stepeni I bili smo dole
        else if (!isUp && smoothAngle > 130) {  // Promenjeno: 145→130
            count++
            isUp = true
        }

        // Forma je "OK" ako je ugao u granicama, ali upozoravamo ako ne vidimo celo telo
        return ExerciseResult(
            count = count,
            isCorrectForm = smoothAngle in 60.0..180.0,
            currentAngle = smoothAngle,
            isUserInFrame = true,
            visibilityMessage = visibilityMessage,
            areHandsFixed = true // Uvek true za sklekove da ne bi izlazila poruka za stabilizaciju
        )
    }

    override fun reset() {
        count = 0
        isUp = true
        angleBuffer.clear()
        lastSmoothAngle = 0.0
        initialShoulderWristDist = null
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
