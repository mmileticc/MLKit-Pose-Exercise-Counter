package dev.milinko.workoutapp.exercise

import com.google.mlkit.vision.pose.PoseLandmark
import dev.milinko.workoutapp.filters.EMA
import kotlin.math.sqrt

class PushUpAnalyzer : ExerciseAnalyzer {

    // NOTE: unlike pull-ups (calibrated/tuned through extensive physical testing), these
    // thresholds are a first estimate for the new calibrated state machine and will likely need
    // another round of physical-testing adjustment (see REFACTOR_PLAN.md Faza 3).
    private val counter = PhaseBasedRepCounter(
        PhaseBasedRepCounter.Config(
            angleDropToStart = 60.0,
            angleRecoverMargin = 15.0,
            minMovement = 15f, // min. change in shoulder-to-wrist distance (px)
            repTimeoutMs = 5000L
        )
    )

    private val smoothAngle = EMA(0.20f)
    private val smoothMovement = EMA(0.25f)
    private val sideSelector = SideSelector()

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

        val leftScore = landmarkConfidence(leftShoulder) + landmarkConfidence(leftElbow) + landmarkConfidence(leftWrist)
        val rightScore = landmarkConfidence(rightShoulder) + landmarkConfidence(rightElbow) + landmarkConfidence(rightWrist)
        val useLeft = sideSelector.chooseLeft(leftScore, rightScore)

        val shoulder = if (useLeft) leftShoulder else rightShoulder
        val elbow = if (useLeft) leftElbow else rightElbow
        val wrist = if (useLeft) leftWrist else rightWrist
        val hip = if (useLeft) leftHip else rightHip
        val knee = if (useLeft) leftKnee else rightKnee

        // Minimalni uslov za brojanje: Rame, lakat i zglob ruke, sa dovoljnom pouzdanošću
        if (shoulder == null || elbow == null || wrist == null) {
            return noArms()
        }
        if (landmarkConfidence(shoulder) < MIN_LANDMARK_CONFIDENCE ||
            landmarkConfidence(elbow) < MIN_LANDMARK_CONFIDENCE ||
            landmarkConfidence(wrist) < MIN_LANDMARK_CONFIDENCE
        ) {
            return noArms()
        }

        // Provera da li se vidi donji deo tela (za preciznu formu)
        val hasLowerBody = hip != null && knee != null
        val lowVisibilityMessage = if (!hasLowerBody) "FULL BODY NOT VISIBLE (FORM MAY BE INACCURATE)" else null

        val rawAngle = calculateAngle(shoulder, elbow, wrist)
        val sAngle = smoothAngle.update(rawAngle)

        val dx = (shoulder.position.x - wrist.position.x).toDouble()
        val dy = (shoulder.position.y - wrist.position.y).toDouble()
        val rawDist = sqrt(dx * dx + dy * dy)
        val sMovement = smoothMovement.update(rawDist).toFloat()

        val update = counter.update(angle = sAngle, movement = sMovement)

        return ExerciseResult(
            count = counter.count,
            isCorrectForm = update.isCorrectForm,
            currentAngle = update.angle,
            isUserInFrame = true,
            visibilityMessage = lowVisibilityMessage ?: update.message,
            areHandsFixed = true // Uvek true za sklekove da ne bi izlazila poruka za stabilizaciju
        )
    }

    private fun noArms() = ExerciseResult(
        count = counter.count,
        isCorrectForm = false,
        currentAngle = 0.0,
        isUserInFrame = false,
        visibilityMessage = "GET IN FRAME (ARMS)",
        areHandsFixed = false
    )

    override fun reset() {
        counter.reset()
        smoothAngle.reset()
        smoothMovement.reset()
        sideSelector.reset()
    }
}
