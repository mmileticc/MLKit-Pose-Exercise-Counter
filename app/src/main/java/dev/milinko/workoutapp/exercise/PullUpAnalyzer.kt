package dev.milinko.workoutapp.exercise

import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.sqrt

class PullUpAnalyzer : ExerciseAnalyzer {

    private enum class Phase {
        CALIBRATING,   // Čeka da korisnik visi mirno i kalibrišemo baseline
        HANGING,       // Visi sa opruženim rukama — spreman
        PULLING_UP,    // Ide gore
        LOWERING       // Spušta se nazad
    }

    private var phase = Phase.CALIBRATING
    private var count = 0

    // Kalibracija
    private val calibFrames = mutableListOf<Double>()
    private var baselineAngle: Double? = null  // ugao opruženih ruku (individualan)
    private val CALIB_NEEDED = 15              // ~1.5 sec

    // Smooth vrijednosti
    private val smoothAngle = EMA(0.25f)
    private val smoothShoulderY = EMA(0.25f)
    private val smoothHipY = EMA(0.30f)

    // Rep tracking
    private var repStartShoulderY: Float? = null
    private var repPeakShoulderY: Float? = null
    private var repStartAngle: Double? = null
    private var peakAngle: Double? = null

    // Prethodna vrijednost za velocity
    private var prevShoulderY: Float? = null

    // Odabrana strana (0=lijevo, 1=desno) — stabilna
    private var chosenSide = -1

    // DEBUG flag — postavi na false za produkciju
    private val DEBUG = true

    companion object {
        // Koliko stepeni ugao mora da se promijeni od baseline da detektujemo pull-up
        // Npr. baseline=165°, drop=45 → pull detektovan kad ugao < 120°
        const val PULL_ANGLE_DROP = 45.0

        // Koliko stepeni ugao mora da se vrati da detektujemo spuštanje
        const val LOWER_ANGLE_RECOVER = 30.0

        // Minimalni pomak ramena prema gore (u pikselima ekrana)
        // Y raste prema DOLE na ekranu kamere, dakle rise = startY - currentY > 0
        const val MIN_SHOULDER_RISE_PX = 25f

        // Max brzina po frejmu — zaštita od naglog pokreta koji nije zgib
        const val MAX_VELOCITY_PX = 22f
    }

    override fun analyze(poseLandmarks: Map<Int, PoseLandmark>): ExerciseResult {

        val lm = chooseBestSide(poseLandmarks)
            ?: return ExerciseResult(
                count = count,
                isCorrectForm = false,
                currentAngle = 0.0,
                isUserInFrame = false,
                visibilityMessage = "GET IN FRAME",
                areHandsFixed = false
            )

        val shoulder = lm.shoulder
        val elbow = lm.elbow
        val wrist = lm.wrist
        val hip = lm.hip

        // Izračunaj vrijednosti
        val rawAngle = calculateAngle(shoulder, elbow, wrist)
        val sAngle = smoothAngle.update(rawAngle)
        val sShoulderY = smoothShoulderY.update(shoulder.position.y).toFloat()
        val sHipY = hip?.let { smoothHipY.update(it.position.y).toFloat() }

        // Velocity (za anti-cheat)
        val velocity = prevShoulderY?.let { abs(sShoulderY - it) } ?: 0f
        prevShoulderY = sShoulderY

        val debugPrefix = if (DEBUG) "[${phase.name}|a=${sAngle.toInt()}°|v=${velocity.toInt()}] " else ""

        when (phase) {

            // ── FAZA 1: Kalibracija ───────────────────────────────────────────
            Phase.CALIBRATING -> {
                // Prihvatamo frame samo ako je osoba relativno mirna i ruke opružene
                if (velocity < 6f && sAngle > 130.0) {
                    calibFrames.add(sAngle)
                }
                // Ako se naglo pomjeri, resetujemo kalibraciju
                if (velocity > 10f) calibFrames.clear()

                if (calibFrames.size >= CALIB_NEEDED) {
                    baselineAngle = calibFrames.takeLast(10).average()
                    calibFrames.clear()
                    phase = Phase.HANGING
                    return result(sAngle, true, "${debugPrefix}CALIBRATED! BASELINE=${baselineAngle!!.toInt()}°")
                }

                val pct = (calibFrames.size * 100 / CALIB_NEEDED).coerceAtMost(100)
                return result(sAngle, false, "${debugPrefix}HANG STILL ($pct%)")
            }

            // ── FAZA 2: Visanje — čekamo početak zgiba ───────────────────────
            Phase.HANGING -> {
                val baseline = baselineAngle ?: 160.0
                val dropNeeded = baseline - PULL_ANGLE_DROP

                // Detektuj početak pull-up-a
                if (sAngle < dropNeeded && velocity < MAX_VELOCITY_PX) {
                    repStartShoulderY = sShoulderY
                    repStartAngle = sAngle
                    repPeakShoulderY = sShoulderY
                    peakAngle = sAngle
                    phase = Phase.PULLING_UP
                    return result(sAngle, true, "${debugPrefix}PULLING UP DETECTED")
                }

                // Dinamički ažuriraj baseline ako se opruži još više
                if (sAngle > (baselineAngle ?: 0.0) + 3) {
                    baselineAngle = sAngle
                }

                return result(sAngle, true, "${debugPrefix}READY | need angle < ${dropNeeded.toInt()}°")
            }

            // ── FAZA 3: Ide gore ──────────────────────────────────────────────
            Phase.PULLING_UP -> {
                // Ažuriraj peak (najmanji Y = fizički najviše)
                if (sShoulderY < (repPeakShoulderY ?: sShoulderY)) {
                    repPeakShoulderY = sShoulderY
                    peakAngle = sAngle
                }

                val recoverAngle = (peakAngle ?: sAngle) + LOWER_ANGLE_RECOVER

                // Detektuj spuštanje (ugao raste nazad prema baseline)
                if (sAngle > recoverAngle) {
                    phase = Phase.LOWERING
                    return result(sAngle, true, "${debugPrefix}LOWERING")
                }

                val rise = (repStartShoulderY ?: sShoulderY) - sShoulderY
                return result(sAngle, true, "${debugPrefix}UP | rise=${rise.toInt()}px angle=${sAngle.toInt()}°")
            }

            // ── FAZA 4: Spušta se — validacija repa ──────────────────────────
            Phase.LOWERING -> {
                val baseline = baselineAngle ?: 160.0

                // Čekamo da se vrati blizu baseline ugla
                if (sAngle > baseline - 20) {
                    val shoulderRise = (repStartShoulderY ?: sShoulderY) - (repPeakShoulderY ?: sShoulderY)
                    val angleChange = baseline - (peakAngle ?: sAngle)

                    val riseOk = shoulderRise >= MIN_SHOULDER_RISE_PX
                    val angleOk = angleChange >= PULL_ANGLE_DROP * 0.7  // 70% threshold

                    return if (riseOk && angleOk) {
                        count++
                        phase = Phase.HANGING
                        result(sAngle, true, "${debugPrefix}✓ REP ${count}! rise=${shoulderRise.toInt()}px Δangle=${angleChange.toInt()}°")
                    } else {
                        phase = Phase.HANGING
                        val reason = when {
                            !riseOk -> "TOO LOW (rise=${shoulderRise.toInt()}px, need ${MIN_SHOULDER_RISE_PX.toInt()})"
                            !angleOk -> "ANGLE TOO SMALL (${angleChange.toInt()}°, need ${(PULL_ANGLE_DROP * 0.7).toInt()}°)"
                            else -> "INCOMPLETE REP"
                        }
                        result(sAngle, false, "${debugPrefix}$reason")
                    }
                }

                val rise = (repStartShoulderY ?: sShoulderY) - (repPeakShoulderY ?: sShoulderY)
                return result(sAngle, true, "${debugPrefix}LOWERING | peak_rise=${rise.toInt()}px")
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

        // Histereza: ne mijenjamo stranu osim ako je razlika > 0.5
        val useLeft = when (chosenSide) {
            0 -> lScore >= rScore - 0.5f
            1 -> lScore > rScore + 0.5f
            else -> lScore >= rScore
        }
        chosenSide = if (useLeft) 0 else 1

        val s = if (useLeft) ls else rs
        val e = if (useLeft) le else re
        val w = if (useLeft) lw else rw

        // Minimalna vidljivost
        if (s == null || e == null || w == null) return null
        if (s.inFrameLikelihood < 0.4f || e.inFrameLikelihood < 0.4f || w.inFrameLikelihood < 0.4f) return null

        val hip = if (useLeft) lm[PoseLandmark.LEFT_HIP] else lm[PoseLandmark.RIGHT_HIP]
        return SideLandmarks(s, e, w, hip?.takeIf { it.inFrameLikelihood > 0.3f })
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
        phase = Phase.CALIBRATING
        count = 0
        calibFrames.clear()
        baselineAngle = null
        smoothAngle.reset()
        smoothShoulderY.reset()
        smoothHipY.reset()
        repStartShoulderY = null
        repPeakShoulderY = null
        repStartAngle = null
        peakAngle = null
        prevShoulderY = null
        chosenSide = -1
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