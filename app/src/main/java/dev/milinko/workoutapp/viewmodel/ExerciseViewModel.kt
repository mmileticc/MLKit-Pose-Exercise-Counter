package dev.milinko.workoutapp.viewmodel

import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.milinko.workoutapp.exercise.ExerciseAnalyzer
import dev.milinko.workoutapp.exercise.ExerciseResult
import dev.milinko.workoutapp.pose.PoseDetectorProcessor
import com.google.mlkit.vision.pose.PoseLandmark
import android.graphics.PointF
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.workoutapp.db.daos.ExerciseDao
import dev.milinko.workoutapp.db.entitys.Exercise
import dev.milinko.workoutapp.exercise.PullUpAnalyzer
import dev.milinko.workoutapp.exercise.PushUpAnalyzer
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.abs

@HiltViewModel
class ExerciseViewModel @Inject constructor(
    private val pushUpAnalyzer: PushUpAnalyzer,
    private val pullUpAnalyzer: PullUpAnalyzer,
    private val processor: PoseDetectorProcessor,
    private val dao: ExerciseDao
) : ViewModel() {

    private val _currentExerciseType = MutableStateFlow("Push Ups")
    val currentExerciseType = _currentExerciseType.asStateFlow()

    private val _uiState = MutableStateFlow(ExerciseResult(0, true))
    val uiState: StateFlow<ExerciseResult> = _uiState.asStateFlow()

    private val _landmarks = MutableStateFlow<Map<Int, PoseLandmark>>(emptyMap())
    val landmarks: StateFlow<Map<Int, PoseLandmark>> = _landmarks.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive = _isSessionActive.asStateFlow()

    private val _showSummary = MutableStateFlow(false)
    val showSummary = _showSummary.asStateFlow()

    val history: StateFlow<List<Exercise>> = dao.getAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // EMA filtri za temporal smoothing svake X i Y koordinate landmarka
    private val landmarkEmaFilters = mutableMapOf<Int, Pair<EMA, EMA>>() // landmarkId -> (xFilter, yFilter)
    
    // Čuvamo prethodne pozicije za outlier rejection
    private val previousPositions = mutableMapOf<Int, Pair<Float, Float>>() // landmarkId -> (prevX, prevY)
    
    // Čuvamo brzinu promene za adaptive smoothing
    private val landmarkVelocities = mutableMapOf<Int, Float>() // landmarkId -> velocity
    
    private companion object {
        const val EMA_ALPHA = 0.08f  // Još manje = PRVO glađenje, manje drhtanja
        const val OUTLIER_THRESHOLD_PX = 200f  // DRASTIČNO povećano za rotaciju
        const val VELOCITY_THRESHOLD = 25f  // Niže da se brže triggeruje adaptive mode
        const val MIN_CONFIDENCE_FOR_LANDMARK = 0.3f  // Minimum confidence da se landmark koristi
    }

    fun setExerciseType(type: String) {
        _currentExerciseType.value = type
        analyzer().reset()
        // Resetuj EMA filtere pri promeni vežbe
        landmarkEmaFilters.clear()
        previousPositions.clear()
        landmarkVelocities.clear()
        _uiState.value = ExerciseResult(0, true)
    }

    private fun analyzer(): ExerciseAnalyzer = if (_currentExerciseType.value == "Push Ups") pushUpAnalyzer else pullUpAnalyzer

    fun startSession() {
        analyzer().reset()
        // Resetuj EMA filtere pri početku nove sesije
        landmarkEmaFilters.clear()
        previousPositions.clear()
        landmarkVelocities.clear()
        _uiState.value = ExerciseResult(0, true)
        _isSessionActive.value = true
        _showSummary.value = false
    }

    fun stopSession() {
        _isSessionActive.value = false
        _showSummary.value = true
    }

    fun saveSession() {
        val result = _uiState.value
        if (result.count > 0) {
            viewModelScope.launch {
                dao.insert(
                    Exercise(
                        name = _currentExerciseType.value,
                        type = true,
                        numOf = result.count,
                        date = Date()
                    )
                )
            }
        }
        _showSummary.value = false
        _isSessionActive.value = false
        _uiState.value = ExerciseResult(0, true)
    }

    fun discardSession() {
        _showSummary.value = false
        _uiState.value = ExerciseResult(0, true)
    }

    fun logManualExercise(reps: Int, type: String = _currentExerciseType.value) {
        viewModelScope.launch {
            dao.insert(
                Exercise(
                    name = if (type.startsWith("Manual")) type else "Manual $type",
                    type = true,
                    numOf = reps,
                    date = Date()
                )
            )
        }
    }

    fun onFrame(image: ImageProxy) {
        if (!_isSessionActive.value) {
            image.close()
            return
        }
        processor.processImage(image) { landmarks ->
            // Primeni EMA filtere i outlier rejection na sve landmark koordinate
            val smoothedLandmarks = applyEMAAndOutlierRejection(landmarks)
            _landmarks.value = smoothedLandmarks
            _uiState.value = analyzer().analyze(smoothedLandmarks)
        }
    }

    /**
     * Primeni EMA filtere na X i Y koordinate svakog landmarka i detektuj outliere
     * Inteligentna flipping detekcija - ne odbija skokove ako je confidence niska
     * (to je legit rotacija, ne flipping)
     */
    private fun applyEMAAndOutlierRejection(
        landmarks: Map<Int, PoseLandmark>
    ): Map<Int, PoseLandmark> {
        val result = mutableMapOf<Int, PoseLandmark>()

        landmarks.forEach { (landmarkId, landmark) ->
            // Inicijalizuj EMA filtere za ovaj landmark ako ne postoje
            if (!landmarkEmaFilters.containsKey(landmarkId)) {
                landmarkEmaFilters[landmarkId] = Pair(EMA(EMA_ALPHA), EMA(EMA_ALPHA))
            }

            val (xFilter, yFilter) = landmarkEmaFilters[landmarkId]!!
            val prevPos = previousPositions[landmarkId]
            val prevVelocity = landmarkVelocities[landmarkId] ?: 0f

            // Detektuj outliere - ako je skok veći od praga, zadrži prethodnu poziciju
            var finalX = landmark.position.x
            var finalY = landmark.position.y
            var velocity = 0f

            if (prevPos != null) {
                val deltaX = abs(landmark.position.x - prevPos.first)
                val deltaY = abs(landmark.position.y - prevPos.second)
                val totalDelta = kotlin.math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()
                velocity = totalDelta

                // INTELIGENTNA DETEKCIJA:
                // Ako je confidence niska (<0.5), to je LEGIT rotacija/flipping, ne odbijaj!
                // Samo odbij ako je confidence DOBRA (>0.5) a skok je velik = greška
                val shouldCheckOutlier = landmark.inFrameLikelihood > 0.5f

                if (shouldCheckOutlier) {
                    // Adapter threshold na osnovu brzine i confidence
                    val adaptiveThreshold = if (prevVelocity > VELOCITY_THRESHOLD) {
                        OUTLIER_THRESHOLD_PX * 1.5f  // 200 * 1.5 = 300px pri brzim pokretima
                    } else {
                        OUTLIER_THRESHOLD_PX  // 200px za normalne pokrete
                    }

                    // Ako je skok veći od praga I confidence je dobra, tretiraj kao outlier
                    if (totalDelta > adaptiveThreshold) {
                        finalX = prevPos.first
                        finalY = prevPos.second
                    }
                } else {
                    // Confidence je niska - koristi raw podatke (to je LEGIT rotacija)
                    finalX = landmark.position.x
                    finalY = landmark.position.y
                }
            }

            // Primeni EMA filtriranje na odsluživane (ili zadržane) pozicije
            val smoothedX = xFilter.update(finalX.toDouble()).toFloat()
            val smoothedY = yFilter.update(finalY.toDouble()).toFloat()

            // Čuva sadašnje pozicije i brzinu za sledeću iteraciju
            previousPositions[landmarkId] = Pair(smoothedX, smoothedY)
            landmarkVelocities[landmarkId] = velocity

            // Kreiraj novi PoseLandmark sa filtriranim koordinatama
            result[landmarkId] = createSmoothedLandmark(landmark, smoothedX, smoothedY)
        }

        return result
    }

    /**
     * Kreiraj novi PoseLandmark sa filtriranim koordinatama
     * Koristi reflection da modifikuj ili wrapper koji čuva smooth pozicije
     */
    private fun createSmoothedLandmark(
        original: PoseLandmark,
        smoothedX: Float,
        smoothedY: Float
    ): PoseLandmark {
        // Najjednostavniji pristup: koristi reflection da pristupis position field-u
        return try {
            val positionField = PoseLandmark::class.java.getDeclaredField("mPosition")
            positionField.isAccessible = true
            val newPosition = PointF(smoothedX, smoothedY)
            positionField.set(original, newPosition)
            original
        } catch (e: NoSuchFieldException) {
            // Ako je ime drugo, pokušaj "position"
            try {
                val positionField = PoseLandmark::class.java.getDeclaredField("position")
                positionField.isAccessible = true
                val newPosition = PointF(smoothedX, smoothedY)
                positionField.set(original, newPosition)
                original
            } catch (e2: Exception) {
                // Ako nema pristupa, vrati original sa raw koordinatama
                // (filter će biti primenjen minimalno, ali neće biti katastrofalno)
                original
            }
        }
    }
}

/**
 * Exponential Moving Average filter za temporal smoothing koordinata
 * Manja vrednost alpha-e daje više smoothing-a (sporija adaptacija)
 * Veća vrednost alpha-e daje manje smoothing-a (brža adaptacija)
 */
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
    
    fun reset() { 
        value = null 
    }
    
    fun get() = value
}
