package dev.milinko.workoutapp.exercise

import android.util.Log
import com.google.mlkit.vision.pose.PoseLandmark
import dev.milinko.workoutapp.filters.EMA
import kotlin.math.abs

class PullUpAnalyzer : ExerciseAnalyzer {

    companion object {
        const val TAG = "PullUpAnalyzer"
        const val WRIST_STABILITY_THRESHOLD = 15f // Max dozvoljeno pomeranje zgloba tokom rep-a
        const val MIN_STABLE_WRIST_FRAMES = 5
    }

    private val counter = PhaseBasedRepCounter(
        PhaseBasedRepCounter.Config(
            angleDropToStart = 25.0,
            angleRecoverMargin = 12.0,
            minMovement = 15f, // MIN_SHOULDER_RISE_PX
            repTimeoutMs = 5000L
        )
    )

    // Smooth vrijednosti
    private val smoothAngle = EMA(0.20f)
    private val smoothShoulderY = EMA(0.25f)
    private val sideSelector = SideSelector()

    private var prevWristY: Float? = null
    private var wristStableFrames = 0
    private var frameCount = 0

    override fun analyze(poseLandmarks: Map<Int, PoseLandmark>): ExerciseResult {
        frameCount++
        val shouldLog = frameCount % 10 == 0

        if (poseLandmarks.isEmpty()) return noFrame("NO POSE DETECTED")

        val lm = chooseBestSide(poseLandmarks)
        if (lm == null) {
            if (shouldLog) Log.w(TAG, "Low confidence landmarks")
            return noFrame("STEP BACK — CAN'T SEE ARMS")
        }

        val rawAngle = calculateAngle(lm.shoulder, lm.elbow, lm.wrist)
        val sAngle = smoothAngle.update(rawAngle)
        val sShoulderY = smoothShoulderY.update(lm.shoulder.position.y).toFloat()
        val wristY = lm.wrist.position.y

        // Provera da li su šake iznad glave (glava je otprilike u nivou ramena ili malo iznad)
        // PoseLandmark.y raste nadole, tako da manje y znači višu poziciju.
        val handsAboveHead = wristY < lm.shoulder.position.y - 10f

        val wristVelocity = prevWristY?.let { abs(wristY - it) } ?: 0f
        prevWristY = wristY

        if (shouldLog) {
            Log.d(TAG, "[${counter.phase}] angle=${sAngle.toInt()}° shoulderY=${sShoulderY.toInt()} handsUp=$handsAboveHead")
        }

        if (!handsAboveHead && counter.phase != PhaseBasedRepCounter.Phase.CALIBRATING) {
            return result(sAngle, false, "HANDS MUST BE ABOVE HEAD")
        }

        // Only track wrist stability *during* the pull (WORKING) - this is what stops someone
        // from swinging their body/arms to fling themselves up and still getting a rep counted
        // (see "Fixed a pullup counting with fixed wrists position..." in git history). Reset
        // between reps (RESTING); freeze through RETURNING so the tally reached during WORKING
        // survives to be read by extraValidation below at rep-confirm time. Without this the
        // counter kept accumulating stable frames while just hanging still before the rep even
        // started, making the check pass almost regardless of what happened during the pull.
        // Placed after the handsAboveHead gate (same as the original pre-refactor code) so a
        // frame where hands read as below head doesn't touch this tally either way.
        when (counter.phase) {
            PhaseBasedRepCounter.Phase.WORKING ->
                wristStableFrames = if (wristVelocity > WRIST_STABILITY_THRESHOLD) 0 else wristStableFrames + 1
            PhaseBasedRepCounter.Phase.RESTING -> wristStableFrames = 0
            else -> Unit
        }

        val update = counter.update(
            angle = sAngle,
            movement = sShoulderY,
            extraValidation = {
                PhaseBasedRepCounter.ValidationResult(
                    valid = wristStableFrames > MIN_STABLE_WRIST_FRAMES,
                    rejectionMessage = "KEEP HANDS STILL ON BAR"
                )
            }
        )

        if (update.message == "REP ${update.count}!") {
            Log.i(TAG, "✓✓✓ ${update.message}")
        }

        return result(update.angle, update.isCorrectForm, update.message)
    }

    private fun result(angle: Double, correctForm: Boolean, msg: String?) = ExerciseResult(
        count = counter.count,
        isCorrectForm = correctForm,
        currentAngle = angle,
        isUserInFrame = true,
        visibilityMessage = msg,
        areHandsFixed = counter.phase != PhaseBasedRepCounter.Phase.CALIBRATING
    )

    private fun noFrame(msg: String) = ExerciseResult(
        count = counter.count,
        isCorrectForm = false,
        currentAngle = 0.0,
        isUserInFrame = false,
        visibilityMessage = msg,
        areHandsFixed = false
    )

    private data class SideLandmarks(
        val shoulder: PoseLandmark,
        val elbow: PoseLandmark,
        val wrist: PoseLandmark
    )

    private fun chooseBestSide(lm: Map<Int, PoseLandmark>): SideLandmarks? {
        val leftShoulder = lm[PoseLandmark.LEFT_SHOULDER]
        val leftElbow = lm[PoseLandmark.LEFT_ELBOW]
        val leftWrist = lm[PoseLandmark.LEFT_WRIST]
        val rightShoulder = lm[PoseLandmark.RIGHT_SHOULDER]
        val rightElbow = lm[PoseLandmark.RIGHT_ELBOW]
        val rightWrist = lm[PoseLandmark.RIGHT_WRIST]

        val lScore = landmarkConfidence(leftShoulder) + landmarkConfidence(leftElbow) + landmarkConfidence(leftWrist)
        val rScore = landmarkConfidence(rightShoulder) + landmarkConfidence(rightElbow) + landmarkConfidence(rightWrist)
        val useLeft = sideSelector.chooseLeft(lScore, rScore)

        val shoulder = if (useLeft) leftShoulder else rightShoulder
        val elbow = if (useLeft) leftElbow else rightElbow
        val wrist = if (useLeft) leftWrist else rightWrist

        if (shoulder == null || elbow == null || wrist == null) return null
        if (landmarkConfidence(shoulder) < MIN_LANDMARK_CONFIDENCE ||
            landmarkConfidence(elbow) < MIN_LANDMARK_CONFIDENCE ||
            landmarkConfidence(wrist) < MIN_LANDMARK_CONFIDENCE
        ) return null

        return SideLandmarks(shoulder, elbow, wrist)
    }

    override fun reset() {
        Log.d(TAG, "reset()")
        counter.reset()
        smoothAngle.reset()
        smoothShoulderY.reset()
        sideSelector.reset()
        prevWristY = null
        wristStableFrames = 0
        frameCount = 0
    }
}
