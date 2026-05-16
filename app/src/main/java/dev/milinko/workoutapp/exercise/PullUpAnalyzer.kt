package dev.milinko.workoutapp.exercise

import android.util.Log
import com.google.mlkit.vision.pose.PoseLandmark
import dev.milinko.workoutapp.filters.EMA
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.sqrt

class PullUpAnalyzer : ExerciseAnalyzer {

    private enum class Phase {
        CALIBRATING,
        HANGING,
        PULLING_UP,
        LOWERING
    }

    private var phase = Phase.CALIBRATING
    private var count = 0

    // Kalibracija
    private val calibFrames = mutableListOf<Double>()
    private var baselineAngle: Double? = null  // FIKSAN nakon kalibracije — ne mjenja se više!
    private val CALIB_NEEDED = 15

    // Smooth vrijednosti
    private val smoothAngle = EMA(0.20f)       // Sporiji EMA za stabilniji ugao
    private val smoothShoulderY = EMA(0.25f)

    // Rep tracking
    private var repStartShoulderY: Float? = null
    private var repPeakShoulderY: Float? = null  // Najmanji Y (fizički najviše)
    private var peakAngle: Double? = null
    private var hangStableFrames = 0
    private var endStableFrames = 0
    private var readyForRep = false

    // Timeout za PULLING_UP fazu — ako zaglavi predugo, reset
    private var pullingUpStartTime: Long = 0L
    private val PULLING_UP_TIMEOUT_MS = 5000L   // Max 5 sekundi za jedan rep

    private var prevShoulderY: Float? = null
    private var prevWristY: Float? = null
    private var wristStableFrames = 0
    private var chosenSide = -1
    private var frameCount = 0

    companion object {
        const val TAG = "PullUpAnalyzer"

        // Thresholds
        const val PULL_ANGLE_DROP = 25.0
        const val LOWER_ANGLE_RECOVER = 12.0
        const val MIN_SHOULDER_RISE_PX = 15f   // Povećano sa 5f za bolju sigurnost
        const val WRIST_STABILITY_THRESHOLD = 15f // Max dozvoljeno pomeranje zgloba tokom rep-a

        const val MAX_VELOCITY_PX = 30f
        const val HANG_STABLE_FRAMES = 4
        const val HANG_STABLE_VELOCITY = 8f
        const val END_STABLE_FRAMES = 3
        const val END_STABLE_VELOCITY = 10f
        const val END_ANGLE_TOLERANCE = 12.0
    }

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

        val velocity = prevShoulderY?.let { abs(sShoulderY - it) } ?: 0f
        prevShoulderY = sShoulderY

        val wristVelocity = prevWristY?.let { abs(wristY - it) } ?: 0f
        prevWristY = wristY

        if (shouldLog) {
            Log.d(TAG, "[$phase] angle=${sAngle.toInt()}° shoulderY=${sShoulderY.toInt()} v=${velocity.toInt()} handsUp=$handsAboveHead")
        }

        if (!handsAboveHead && phase != Phase.CALIBRATING) {
            return result(sAngle, false, "HANDS MUST BE ABOVE HEAD")
        }

        return when (phase) {

            // ── KALIBRACIJA ───────────────────────────────────────────────────
            Phase.CALIBRATING -> {
                if (velocity < 5f && sAngle > 130.0) {
                    calibFrames.add(sAngle)
                }
                if (velocity > 10f) calibFrames.clear()

                if (calibFrames.size >= CALIB_NEEDED) {
                    // FIX 1: Baseline se NIKAD više ne ažurira nakon ovoga
                    baselineAngle = calibFrames.takeLast(10).average()
                    calibFrames.clear()
                    phase = Phase.HANGING
                    hangStableFrames = 0
                    endStableFrames = 0
                    readyForRep = false
                    Log.i(TAG, "✓ CALIBRATED baseline=${baselineAngle!!.toInt()}°")
                    result(sAngle, true, "READY! START PULLING")
                } else {
                    val pct = calibFrames.size * 100 / CALIB_NEEDED
                    result(sAngle, false, "HANG STILL ($pct%)")
                }
            }

            // ── VISANJE ───────────────────────────────────────────────────────
            Phase.HANGING -> {
                val baseline = baselineAngle ?: 160.0
                val dropNeeded = baseline - PULL_ANGLE_DROP
                val isStableHang = sAngle > baseline - END_ANGLE_TOLERANCE && velocity < HANG_STABLE_VELOCITY

                if (isStableHang) {
                    hangStableFrames++
                } else {
                    hangStableFrames = 0
                }

                if (hangStableFrames >= HANG_STABLE_FRAMES) {
                    readyForRep = true
                }

                // FIX 1: Baseline se NE AŽURIRA ovdje više!
                // Ovo je bio izvor problema — svaki put kad bi se opružio više,
                // threshold bi porastao i pull-up nikad ne bi bio detektovan

                if (!readyForRep) {
                    result(sAngle, true, "SETTLING...")
                } else if (sAngle < dropNeeded && velocity < MAX_VELOCITY_PX) {
                    repStartShoulderY = sShoulderY
                    repPeakShoulderY = sShoulderY
                    peakAngle = sAngle
                    pullingUpStartTime = System.currentTimeMillis()
                    endStableFrames = 0
                    phase = Phase.PULLING_UP
                    Log.i(TAG, "↑ PULL START angle=${sAngle.toInt()}° shoulderY=${sShoulderY.toInt()}")
                    result(sAngle, true, "PULLING!")
                } else {
                    result(sAngle, true, "READY | angle=${sAngle.toInt()}° need<${dropNeeded.toInt()}°")
                }
            }

            // ── IDE GORE ──────────────────────────────────────────────────────
            Phase.PULLING_UP -> {

                // Provera stabilnosti šaka
                if (wristVelocity > WRIST_STABILITY_THRESHOLD) {
                    wristStableFrames = 0
                } else {
                    wristStableFrames++
                }

                // FIX 2: Timeout — ako smo u PULLING_UP predugo, vraćamo se u HANGING
                // Ovo rješava problem "zaglavljenog" stanja iz loga (frejm 400-550)
                val elapsed = System.currentTimeMillis() - pullingUpStartTime
                if (elapsed > PULLING_UP_TIMEOUT_MS) {
                    Log.w(TAG, "PULLING_UP TIMEOUT after ${elapsed}ms — back to HANGING")
                    phase = Phase.HANGING
                    return result(sAngle, false, "TOO SLOW — TRY AGAIN")
                }

                // FIX 3: Peak tracking — čuvamo NAJMANJI Y (fizički najviše)
                // i NAJMANJI ugao (najviše savijen lakat)
                if (sShoulderY < (repPeakShoulderY ?: sShoulderY)) {
                    repPeakShoulderY = sShoulderY
                }
                // Peak ugao = minimum smooth ugla (bez outliera)
                if (sAngle < (peakAngle ?: sAngle)) {
                    peakAngle = sAngle
                }

                val recoverAngle = (peakAngle ?: sAngle) + LOWER_ANGLE_RECOVER
                val rise = (repStartShoulderY ?: sShoulderY) - sShoulderY

                // Detektuj spuštanje — ugao mora porasti od peak-a za LOWER_ANGLE_RECOVER
                // I rame mora biti ispod startne pozicije (znači bilo je gore)
                if (sAngle > recoverAngle && rise > 5f) {
                    Log.i(TAG, "↓ LOWERING rise=${rise.toInt()}px peakAngle=${peakAngle?.toInt()}°")
                    phase = Phase.LOWERING
                    result(sAngle, true, "LOWERING ↓")
                } else {
                    result(sAngle, true, "UP ↑ rise=${rise.toInt()}px angle=${sAngle.toInt()}°")
                }
            }

            // ── SPUŠTA SE — validacija ────────────────────────────────────────
            Phase.LOWERING -> {
                val baseline = baselineAngle ?: 160.0
                val isStableFinish = sAngle > baseline - END_ANGLE_TOLERANCE && velocity < END_STABLE_VELOCITY

                if (isStableFinish) {
                    endStableFrames++
                } else {
                    endStableFrames = 0
                }

                if (endStableFrames >= END_STABLE_FRAMES) {
                    val shoulderRise = (repStartShoulderY ?: sShoulderY) - (repPeakShoulderY ?: sShoulderY)
                    val angleChange = baseline - (peakAngle ?: sAngle)

                    val riseOk = shoulderRise >= MIN_SHOULDER_RISE_PX
                    val angleOk = angleChange >= PULL_ANGLE_DROP * 0.75
                    val wristsWereStable = wristStableFrames > 5 // Barem 5 frejmova stabilnih šaka tokom pull-a

                    Log.i(TAG, "REP CHECK rise=${shoulderRise.toInt()}px(need ${MIN_SHOULDER_RISE_PX.toInt()}) Δangle=${angleChange.toInt()}°(need ${(PULL_ANGLE_DROP*0.75).toInt()}) riseOk=$riseOk angleOk=$angleOk wristOk=$wristsWereStable")

                    phase = Phase.HANGING
                    hangStableFrames = 0
                    endStableFrames = 0
                    wristStableFrames = 0
                    readyForRep = true

                    return if (riseOk && angleOk && wristsWereStable) {
                        count++
                        Log.i(TAG, "✓✓✓ REP $count!")
                        result(sAngle, true, "✓ REP $count!")
                    } else {
                        val reason = when {
                            !wristsWereStable -> "KEEP HANDS STILL ON BAR"
                            !riseOk -> "GO HIGHER (${shoulderRise.toInt()}px < ${MIN_SHOULDER_RISE_PX.toInt()}px)"
                            else -> "FULL RANGE NEEDED"
                        }
                        Log.w(TAG, "✗ REJECTED: $reason")
                        result(sAngle, false, reason)
                    }
                }

                val rise = (repStartShoulderY ?: sShoulderY) - (repPeakShoulderY ?: sShoulderY)
                result(sAngle, true, "LOWERING ↓ rise=${rise.toInt()}px")
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun result(angle: Double, correctForm: Boolean, msg: String?) = ExerciseResult(
        count = count,
        isCorrectForm = correctForm,
        currentAngle = angle,
        isUserInFrame = true,
        visibilityMessage = msg,
        areHandsFixed = phase != Phase.CALIBRATING
    )

    private fun noFrame(msg: String) = ExerciseResult(
        count = count,
        isCorrectForm = false,
        currentAngle = 0.0,
        isUserInFrame = false,
        visibilityMessage = msg,
        areHandsFixed = false
    )

    data class SideLandmarks(
        val shoulder: PoseLandmark,
        val elbow: PoseLandmark,
        val wrist: PoseLandmark,
        val hip: PoseLandmark?
    )

    private fun chooseBestSide(lm: Map<Int, PoseLandmark>): SideLandmarks? {
        val leftShoulder = lm[PoseLandmark.LEFT_SHOULDER]
        val leftElbow = lm[PoseLandmark.LEFT_ELBOW]
        val leftWrist = lm[PoseLandmark.LEFT_WRIST]
        val rightShoulder = lm[PoseLandmark.RIGHT_SHOULDER]
        val rightElbow = lm[PoseLandmark.RIGHT_ELBOW]
        val rightWrist = lm[PoseLandmark.RIGHT_WRIST]

        val lScore = conf(leftShoulder) + conf(leftElbow) + conf(leftWrist)
        val rScore = conf(rightShoulder) + conf(rightElbow) + conf(rightWrist)

        val useLeft = when (chosenSide) {
            0 -> lScore >= rScore - 0.5f
            1 -> lScore > rScore + 0.5f
            else -> lScore >= rScore
        }
        chosenSide = if (useLeft) 0 else 1

        val shoulder = if (useLeft) leftShoulder else rightShoulder
        val elbow = if (useLeft) leftElbow else rightElbow
        val wrist = if (useLeft) leftWrist else rightWrist

        if (shoulder == null || elbow == null || wrist == null) return null
        if (shoulder.inFrameLikelihood < 0.25f || elbow.inFrameLikelihood < 0.25f || wrist.inFrameLikelihood < 0.25f) return null

        val hip = if (useLeft) lm[PoseLandmark.LEFT_HIP] else lm[PoseLandmark.RIGHT_HIP]
        return SideLandmarks(shoulder, elbow, wrist, hip?.takeIf { it.inFrameLikelihood > 0.25f })
    }

    private fun conf(lm: PoseLandmark?) = lm?.inFrameLikelihood ?: 0f

    private fun calculateAngle(a: PoseLandmark, b: PoseLandmark, c: PoseLandmark): Double {
        val abX = a.position.x - b.position.x
        val abY = a.position.y - b.position.y
        val cbX = c.position.x - b.position.x
        val cbY = c.position.y - b.position.y
        val dot = abX * cbX + abY * cbY
        val mag = sqrt((abX * abX + abY * abY).toDouble()) * sqrt((cbX * cbX + cbY * cbY).toDouble())
        return if (mag < 1e-6) 180.0 else Math.toDegrees(acos((dot / mag).coerceIn(-1.0, 1.0)))
    }

    override fun reset() {
        Log.d(TAG, "reset()")
        phase = Phase.CALIBRATING
        count = 0
        calibFrames.clear()
        baselineAngle = null
        smoothAngle.reset()
        smoothShoulderY.reset()
        repStartShoulderY = null
        repPeakShoulderY = null
        peakAngle = null
        prevShoulderY = null
        prevWristY = null
        pullingUpStartTime = 0L
        chosenSide = -1
        frameCount = 0
        hangStableFrames = 0
        endStableFrames = 0
        wristStableFrames = 0
        readyForRep = false
    }
}

