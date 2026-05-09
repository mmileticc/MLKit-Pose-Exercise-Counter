package dev.milinko.workoutapp.exercise

import android.util.Log
import com.google.mlkit.vision.pose.PoseLandmark
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

    // Timeout za PULLING_UP fazu — ako zaglavi predugo, reset
    private var pullingUpStartTime: Long = 0L
    private val PULLING_UP_TIMEOUT_MS = 5000L   // Max 5 sekundi za jedan rep

    private var prevShoulderY: Float? = null
    private var chosenSide = -1
    private var frameCount = 0

    companion object {
        const val TAG = "PullUpAnalyzer"

        // FIX 1: Baseline je FIKSAN nakon kalibracije.
        // Koristimo PULL_ANGLE_DROP % od baseline-a umjesto apsolutnih stepeni
        // Npr. baseline=177°, drop=40° → pull triggeruje na 137°
        const val PULL_ANGLE_DROP = 40.0

        // FIX 2: Recover threshold — koliko ugao mora porasti od PEAK-a
        // Koristimo manji broj jer EMA gladi oscilacije
        const val LOWER_ANGLE_RECOVER = 25.0

        // FIX 3: Smanjen min rise jer shoulderY varira malo na nekim uređajima
        const val MIN_SHOULDER_RISE_PX = 12f

        const val MAX_VELOCITY_PX = 25f
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

        val velocity = prevShoulderY?.let { abs(sShoulderY - it) } ?: 0f
        prevShoulderY = sShoulderY

        if (shouldLog) {
            Log.d(TAG, "[$phase] angle=${sAngle.toInt()}° shoulderY=${sShoulderY.toInt()} v=${velocity.toInt()} baseline=${baselineAngle?.toInt()}")
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

                // FIX 1: Baseline se NE AŽURIRA ovdje više!
                // Ovo je bio izvor problema — svaki put kad bi se opružio više,
                // threshold bi porastao i pull-up nikad ne bi bio detektovan

                if (sAngle < dropNeeded && velocity < MAX_VELOCITY_PX) {
                    repStartShoulderY = sShoulderY
                    repPeakShoulderY = sShoulderY
                    peakAngle = sAngle
                    pullingUpStartTime = System.currentTimeMillis()
                    phase = Phase.PULLING_UP
                    Log.i(TAG, "↑ PULL START angle=${sAngle.toInt()}° shoulderY=${sShoulderY.toInt()}")
                    result(sAngle, true, "PULLING!")
                } else {
                    result(sAngle, true, "READY | angle=${sAngle.toInt()}° need<${dropNeeded.toInt()}°")
                }
            }

            // ── IDE GORE ──────────────────────────────────────────────────────
            Phase.PULLING_UP -> {

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

                if (sAngle > baseline - 20) {
                    val shoulderRise = (repStartShoulderY ?: sShoulderY) - (repPeakShoulderY ?: sShoulderY)
                    val angleChange = baseline - (peakAngle ?: sAngle)

                    val riseOk = shoulderRise >= MIN_SHOULDER_RISE_PX
                    val angleOk = angleChange >= PULL_ANGLE_DROP * 0.75

                    Log.i(TAG, "REP CHECK rise=${shoulderRise.toInt()}px(need ${MIN_SHOULDER_RISE_PX.toInt()}) Δangle=${angleChange.toInt()}°(need ${(PULL_ANGLE_DROP*0.75).toInt()}) riseOk=$riseOk angleOk=$angleOk")

                    phase = Phase.HANGING

                    return if (riseOk && angleOk) {
                        count++
                        Log.i(TAG, "✓✓✓ REP $count!")
                        result(sAngle, true, "✓ REP $count!")
                    } else {
                        val reason = when {
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
        val ls = lm[PoseLandmark.LEFT_SHOULDER]
        val le = lm[PoseLandmark.LEFT_ELBOW]
        val lw = lm[PoseLandmark.LEFT_WRIST]
        val rs = lm[PoseLandmark.RIGHT_SHOULDER]
        val re = lm[PoseLandmark.RIGHT_ELBOW]
        val rw = lm[PoseLandmark.RIGHT_WRIST]

        val lScore = conf(ls) + conf(le) + conf(lw)
        val rScore = conf(rs) + conf(re) + conf(rw)

        val useLeft = when (chosenSide) {
            0 -> lScore >= rScore - 0.5f
            1 -> lScore > rScore + 0.5f
            else -> lScore >= rScore
        }
        chosenSide = if (useLeft) 0 else 1

        val s = if (useLeft) ls else rs
        val e = if (useLeft) le else re
        val w = if (useLeft) lw else rw

        if (s == null || e == null || w == null) return null
        if (s.inFrameLikelihood < 0.25f || e.inFrameLikelihood < 0.25f || w.inFrameLikelihood < 0.25f) return null

        val hip = if (useLeft) lm[PoseLandmark.LEFT_HIP] else lm[PoseLandmark.RIGHT_HIP]
        return SideLandmarks(s, e, w, hip?.takeIf { it.inFrameLikelihood > 0.25f })
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
        pullingUpStartTime = 0L
        chosenSide = -1
        frameCount = 0
    }
}

class EMA(private val alpha: Float) {
    private var value: Double? = null
    fun update(v: Float): Double {
        value = if (value == null) v.toDouble() else alpha * v + (1 - alpha) * value!!
        return value!!
    }
    fun update(v: Double): Double {
        value = if (value == null) v else alpha * v + (1 - alpha) * value!!
        return value!!
    }
    fun reset() { value = null }
    fun get() = value
}