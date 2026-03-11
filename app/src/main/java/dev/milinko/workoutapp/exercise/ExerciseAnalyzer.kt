package dev.milinko.workoutapp.exercise

import com.google.mlkit.vision.pose.PoseLandmark

interface ExerciseAnalyzer {
    fun analyze(poseLandmarks: Map<Int, PoseLandmark>): ExerciseResult
    fun reset()
}

data class ExerciseResult(
    val count: Int,
    val isCorrectForm: Boolean,
    val currentAngle: Double = 0.0,
    val isUserInFrame: Boolean = true,
    val visibilityMessage: String? = null
)
