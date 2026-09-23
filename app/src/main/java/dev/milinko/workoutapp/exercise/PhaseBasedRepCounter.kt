package dev.milinko.workoutapp.exercise

import kotlin.math.abs

/**
 * Generic phase-based rep counter, extracted from the original pull-up analyzer so any
 * angle-driven exercise (elbow bends, body part moves away from rest and back) can reuse the
 * same proven detection pipeline instead of re-implementing it:
 *  - calibrates a personal baseline angle instead of using a fixed threshold for everyone,
 *  - runs a RESTING -> WORKING -> RETURNING state machine,
 *  - only confirms a phase change after several consecutive stable frames (rejects single-frame
 *    noise instead of counting it as a rep),
 *  - times out a rep that gets stuck mid-way instead of leaving the state machine stuck.
 *
 * Feed it one frame at a time via [update] with the joint angle you're tracking (e.g. elbow angle)
 * and a secondary [movement] signal that should move away from its resting value during a rep and
 * back during the return (e.g. shoulder Y in pixels for pull-ups, shoulder-to-wrist distance for
 * push-ups) - this confirms real physical movement happened, not just angle noise.
 *
 * Assumes the resting/extended pose has the HIGHER angle and the worked pose has the LOWER angle -
 * true for both push-ups and pull-ups, since the elbow bends during the rep.
 */
class PhaseBasedRepCounter(private val config: Config) {

    data class Config(
        /** How far below the calibrated baseline the angle must drop to start a rep. */
        val angleDropToStart: Double,
        /** How far the angle must recover from its peak bend before the return is recognized. */
        val angleRecoverMargin: Double,
        /** Minimum required change in [movement] for a rep to be counted as valid range of motion. */
        val minMovement: Float,
        val calibFramesNeeded: Int = 15,
        val repTimeoutMs: Long = 5000L,
        val restStableFramesNeeded: Int = 4,
        val restStableVelocity: Float = 8f,
        val returnStableFramesNeeded: Int = 3,
        val returnStableVelocity: Float = 10f,
        val endAngleTolerance: Double = 12.0,
        val maxStartVelocity: Float = 30f
    )

    enum class Phase { CALIBRATING, RESTING, WORKING, RETURNING }

    /** Extra pass/fail check an exercise can plug in at the moment a rep would be confirmed. */
    data class ValidationResult(val valid: Boolean, val rejectionMessage: String)

    data class Result(
        val phase: Phase,
        val count: Int,
        val angle: Double,
        val isCorrectForm: Boolean,
        val message: String?
    )

    var phase = Phase.CALIBRATING
        private set
    var count = 0
        private set

    private val calibFrames = mutableListOf<Double>()
    private var baselineAngle: Double? = null

    private var restStableFrames = 0
    private var returnStableFrames = 0
    private var readyForRep = false

    private var startMovement: Float? = null
    private var peakMovement: Float? = null
    private var peakAngle: Double? = null
    private var workingStartTime = 0L
    private var prevMovement: Float? = null

    fun reset() {
        phase = Phase.CALIBRATING
        count = 0
        calibFrames.clear()
        baselineAngle = null
        restStableFrames = 0
        returnStableFrames = 0
        readyForRep = false
        startMovement = null
        peakMovement = null
        peakAngle = null
        workingStartTime = 0L
        prevMovement = null
    }

    fun update(
        angle: Double,
        movement: Float,
        extraValidation: (() -> ValidationResult)? = null
    ): Result {
        val velocity = prevMovement?.let { abs(movement - it) } ?: 0f
        prevMovement = movement

        return when (phase) {
            Phase.CALIBRATING -> updateCalibrating(angle, velocity)
            Phase.RESTING -> updateResting(angle, movement, velocity)
            Phase.WORKING -> updateWorking(angle, movement)
            Phase.RETURNING -> updateReturning(angle, movement, velocity, extraValidation)
        }
    }

    private fun updateCalibrating(angle: Double, velocity: Float): Result {
        if (velocity < 5f && angle > 130.0) calibFrames.add(angle)
        if (velocity > 10f) calibFrames.clear()

        if (calibFrames.size >= config.calibFramesNeeded) {
            baselineAngle = calibFrames.takeLast(10).average()
            calibFrames.clear()
            phase = Phase.RESTING
            restStableFrames = 0
            returnStableFrames = 0
            readyForRep = false
            return result(angle, true, "READY! START")
        }
        val pct = calibFrames.size * 100 / config.calibFramesNeeded
        return result(angle, false, "HOLD STILL ($pct%)")
    }

    private fun updateResting(angle: Double, movement: Float, velocity: Float): Result {
        val baseline = baselineAngle ?: 160.0
        val dropNeeded = baseline - config.angleDropToStart
        val isStable = angle > baseline - config.endAngleTolerance && velocity < config.restStableVelocity

        restStableFrames = if (isStable) restStableFrames + 1 else 0
        if (restStableFrames >= config.restStableFramesNeeded) readyForRep = true

        if (!readyForRep) return result(angle, true, "SETTLING...")

        return if (angle < dropNeeded && velocity < config.maxStartVelocity) {
            startMovement = movement
            peakMovement = movement
            peakAngle = angle
            workingStartTime = System.currentTimeMillis()
            returnStableFrames = 0
            phase = Phase.WORKING
            result(angle, true, "GO!")
        } else {
            result(angle, true, "READY")
        }
    }

    private fun updateWorking(angle: Double, movement: Float): Result {
        val elapsed = System.currentTimeMillis() - workingStartTime
        if (elapsed > config.repTimeoutMs) {
            phase = Phase.RESTING
            return result(angle, false, "TOO SLOW - TRY AGAIN")
        }

        if (movement < (peakMovement ?: movement)) peakMovement = movement
        if (angle < (peakAngle ?: angle)) peakAngle = angle

        val recoverAngle = (peakAngle ?: angle) + config.angleRecoverMargin
        val rise = (startMovement ?: movement) - movement

        return if (angle > recoverAngle && rise > 5f) {
            phase = Phase.RETURNING
            result(angle, true, "RETURNING")
        } else {
            result(angle, true, "WORKING")
        }
    }

    private fun updateReturning(
        angle: Double,
        movement: Float,
        velocity: Float,
        extraValidation: (() -> ValidationResult)?
    ): Result {
        val baseline = baselineAngle ?: 160.0
        val isStable = angle > baseline - config.endAngleTolerance && velocity < config.returnStableVelocity
        returnStableFrames = if (isStable) returnStableFrames + 1 else 0

        if (returnStableFrames < config.returnStableFramesNeeded) {
            return result(angle, true, "RETURNING")
        }

        val movementDelta = (startMovement ?: movement) - (peakMovement ?: movement)
        val angleDelta = baseline - (peakAngle ?: angle)
        val movementOk = movementDelta >= config.minMovement
        val angleOk = angleDelta >= config.angleDropToStart * 0.75
        val extra = extraValidation?.invoke() ?: ValidationResult(true, "")

        phase = Phase.RESTING
        restStableFrames = 0
        returnStableFrames = 0
        readyForRep = true

        return if (movementOk && angleOk && extra.valid) {
            count++
            result(angle, true, "REP $count!")
        } else {
            val reason = when {
                !extra.valid -> extra.rejectionMessage
                !movementOk -> "GO FURTHER"
                else -> "FULL RANGE NEEDED"
            }
            result(angle, false, reason)
        }
    }

    private fun result(angle: Double, correctForm: Boolean, message: String?) = Result(
        phase = phase,
        count = count,
        angle = angle,
        isCorrectForm = correctForm,
        message = message
    )
}
