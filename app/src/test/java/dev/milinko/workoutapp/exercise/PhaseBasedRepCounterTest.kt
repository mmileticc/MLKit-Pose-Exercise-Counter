package dev.milinko.workoutapp.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for [PhaseBasedRepCounter] using synthetic angle/movement sequences
 * instead of a physical camera + pose detector, so the calibration/state-machine logic
 * that both PushUpAnalyzer and PullUpAnalyzer rely on can be verified without hardware
 * (see REFACTOR_PLAN.md Faza 4).
 */
class PhaseBasedRepCounterTest {

    private fun newCounter(
        angleDropToStart: Double = 25.0,
        angleRecoverMargin: Double = 12.0,
        minMovement: Float = 15f,
        repTimeoutMs: Long = 5000L
    ) = PhaseBasedRepCounter(
        PhaseBasedRepCounter.Config(
            angleDropToStart = angleDropToStart,
            angleRecoverMargin = angleRecoverMargin,
            minMovement = minMovement,
            repTimeoutMs = repTimeoutMs
        )
    )

    /** Feeds constant (angle, movement) frames - simulates holding still, e.g. calibration/rest. */
    private fun feedHold(counter: PhaseBasedRepCounter, angle: Double, movement: Float, frames: Int) {
        repeat(frames) { counter.update(angle, movement) }
    }

    /** Feeds a slow linear ramp from (fromAngle, fromMovement) to (toAngle, toMovement) over [steps]
     *  frames, small enough per-step deltas to stay under the counter's velocity thresholds. */
    private fun feedRamp(
        counter: PhaseBasedRepCounter,
        fromAngle: Double, toAngle: Double,
        fromMovement: Float, toMovement: Float,
        steps: Int
    ) {
        for (i in 1..steps) {
            val t = i.toDouble() / steps
            val angle = fromAngle + (toAngle - fromAngle) * t
            val movement = fromMovement + (toMovement - fromMovement) * t.toFloat()
            counter.update(angle, movement)
        }
    }

    /** Calibrates at [restAngle]/[restMovement] and settles into RESTING, ready for a rep. */
    private fun calibrateAndSettle(counter: PhaseBasedRepCounter, restAngle: Double, restMovement: Float) {
        // calibFramesNeeded (15) frames to establish the baseline...
        feedHold(counter, restAngle, restMovement, 15)
        assertEquals(PhaseBasedRepCounter.Phase.RESTING, counter.phase)
        // ...then restStableFramesNeeded (4) more to be considered "ready for rep".
        feedHold(counter, restAngle, restMovement, 4)
    }

    @Test
    fun `counts a full rep with enough angle drop and movement`() {
        val counter = newCounter()
        val restAngle = 160.0
        val restMovement = 300f
        val workAngle = 90.0
        val workMovement = 260f

        calibrateAndSettle(counter, restAngle, restMovement)

        // Slow descent, slow return - well within every velocity threshold, only the
        // total angle/movement range of motion should decide whether a rep counts.
        feedRamp(counter, restAngle, workAngle, restMovement, workMovement, steps = 30)
        feedRamp(counter, workAngle, restAngle, workMovement, restMovement, steps = 30)

        assertEquals(1, counter.count)
        assertEquals(PhaseBasedRepCounter.Phase.RESTING, counter.phase)
    }

    @Test
    fun `counts two consecutive full reps`() {
        val counter = newCounter()
        val restAngle = 160.0
        val restMovement = 300f
        val workAngle = 90.0
        val workMovement = 260f

        calibrateAndSettle(counter, restAngle, restMovement)

        repeat(2) {
            feedRamp(counter, restAngle, workAngle, restMovement, workMovement, steps = 30)
            feedRamp(counter, workAngle, restAngle, workMovement, restMovement, steps = 30)
            // give it a few more stable resting frames before the next rep, like a real user
            // pausing briefly at the top between reps.
            feedHold(counter, restAngle, restMovement, 4)
        }

        assertEquals(2, counter.count)
    }

    @Test
    fun `does not count a shallow, partial-range rep`() {
        val counter = newCounter(angleDropToStart = 25.0, minMovement = 15f)
        val restAngle = 160.0
        val restMovement = 300f
        // Only a small dip - not a real rep: angle barely crosses the drop threshold and
        // movement barely changes, well under minMovement.
        val shallowAngle = 132.0
        val shallowMovement = 296f

        calibrateAndSettle(counter, restAngle, restMovement)
        feedRamp(counter, restAngle, shallowAngle, restMovement, shallowMovement, steps = 20)
        feedRamp(counter, shallowAngle, restAngle, shallowMovement, restMovement, steps = 20)

        assertEquals(0, counter.count)
    }

    @Test
    fun `extraValidation can reject an otherwise-valid rep`() {
        val counter = newCounter()
        val restAngle = 160.0
        val restMovement = 300f
        val workAngle = 90.0
        val workMovement = 260f
        val rejection = PhaseBasedRepCounter.ValidationResult(valid = false, rejectionMessage = "KEEP HANDS STILL")

        calibrateAndSettle(counter, restAngle, restMovement)
        feedRamp(counter, restAngle, workAngle, restMovement, workMovement, steps = 30)

        // Drive the return phase manually so we can attach extraValidation on every frame,
        // the same way PullUpAnalyzer checks wrist stability. The rep gets rejected partway
        // through this loop and the phase resets to RESTING, so we collect every message
        // instead of only checking the very last frame's (which would just be RESTING's
        // "READY" from the frames fed after the rejection already happened).
        val messages = mutableListOf<String?>()
        for (i in 1..30) {
            val t = i.toDouble() / 30
            val angle = workAngle + (restAngle - workAngle) * t
            val movement = workMovement + (restMovement - workMovement) * t.toFloat()
            val result = counter.update(angle, movement) { rejection }
            messages.add(result.message)
        }

        assertEquals(0, counter.count)
        assertTrue(
            "expected rejection message to appear at least once, got $messages",
            messages.contains(rejection.rejectionMessage)
        )
    }

    @Test
    fun `stuck rep times out back to RESTING instead of staying stuck`() {
        val counter = newCounter(repTimeoutMs = 30L)
        val restAngle = 160.0
        val restMovement = 300f

        calibrateAndSettle(counter, restAngle, restMovement)
        // Drop into WORKING...
        counter.update(120.0, 280f)
        assertEquals(PhaseBasedRepCounter.Phase.WORKING, counter.phase)

        Thread.sleep(50)

        // ...then get stuck (angle stays low, never recovers) past the short timeout.
        val result = counter.update(120.0, 280f)

        assertEquals(PhaseBasedRepCounter.Phase.RESTING, counter.phase)
        assertFalse(result.isCorrectForm)
        assertEquals(0, counter.count)
    }

    @Test
    fun `reset clears count and phase back to CALIBRATING`() {
        val counter = newCounter()
        calibrateAndSettle(counter, 160.0, 300f)
        feedRamp(counter, 160.0, 90.0, 300f, 260f, steps = 30)
        feedRamp(counter, 90.0, 160.0, 260f, 300f, steps = 30)
        assertTrue(counter.count > 0)

        counter.reset()

        assertEquals(0, counter.count)
        assertEquals(PhaseBasedRepCounter.Phase.CALIBRATING, counter.phase)
    }
}
