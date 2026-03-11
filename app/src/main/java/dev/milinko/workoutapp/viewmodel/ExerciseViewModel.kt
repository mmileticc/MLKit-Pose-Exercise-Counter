package dev.milinko.workoutapp.viewmodel

import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.milinko.workoutapp.exercise.ExerciseAnalyzer
import dev.milinko.workoutapp.exercise.ExerciseResult
import dev.milinko.workoutapp.pose.PoseDetectorProcessor
import com.google.mlkit.vision.pose.PoseLandmark
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.workoutapp.db.daos.ExerciseDao
import dev.milinko.workoutapp.db.entitys.Exercise
import dev.milinko.workoutapp.exercise.PullUpAnalyzer
import dev.milinko.workoutapp.exercise.PushUpAnalyzer
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date

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

    fun setExerciseType(type: String) {
        _currentExerciseType.value = type
        analyzer().reset()
        _uiState.value = ExerciseResult(0, true)
    }

    private fun analyzer(): ExerciseAnalyzer = if (_currentExerciseType.value == "Push Ups") pushUpAnalyzer else pullUpAnalyzer

    fun startSession() {
        analyzer().reset()
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
            _landmarks.value = landmarks
            _uiState.value = analyzer().analyze(landmarks)
        }
    }
}