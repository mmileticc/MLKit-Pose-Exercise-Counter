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
    private val analyzer: ExerciseAnalyzer,
    private val processor: PoseDetectorProcessor,
    private val dao: ExerciseDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExerciseResult(0, true))
    val uiState: StateFlow<ExerciseResult> = _uiState.asStateFlow()

    private val _landmarks = MutableStateFlow<Map<Int, PoseLandmark>>(emptyMap())
    val landmarks: StateFlow<Map<Int, PoseLandmark>> = _landmarks.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive = _isSessionActive.asStateFlow()

    val history: StateFlow<List<Exercise>> = dao.getAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startSession() {
        analyzer.reset()
        _uiState.value = ExerciseResult(0, true)
        _isSessionActive.value = true
    }

    fun stopSession() {
        val result = _uiState.value
        if (result.count > 0) {
            viewModelScope.launch {
                dao.insert(
                    Exercise(
                        name = "Push Ups",
                        type = true,
                        numOf = result.count,
                        date = Date()
                    )
                )
            }
        }
        _isSessionActive.value = false
    }

    fun onFrame(image: ImageProxy) {
        if (!_isSessionActive.value) {
            image.close()
            return
        }
        processor.processImage(image) { landmarks ->
            _landmarks.value = landmarks
            _uiState.value = analyzer.analyze(landmarks)
        }
    }
}