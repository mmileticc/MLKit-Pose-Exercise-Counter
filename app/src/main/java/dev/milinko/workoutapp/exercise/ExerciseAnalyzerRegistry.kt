package dev.milinko.workoutapp.exercise

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps a camera-supported [ExerciseType] to its [ExerciseAnalyzer].
 * Adding a new camera exercise: implement a new ExerciseAnalyzer, add a
 * @Provides for it in ExerciseModule, add an ExerciseType entry, and add
 * it to the map below.
 */
@Singleton
class ExerciseAnalyzerRegistry @Inject constructor(
    private val pushUpAnalyzer: PushUpAnalyzer,
    private val pullUpAnalyzer: PullUpAnalyzer
) {
    private val analyzers: Map<ExerciseType, ExerciseAnalyzer> = mapOf(
        ExerciseType.PUSH_UPS to pushUpAnalyzer,
        ExerciseType.PULL_UPS to pullUpAnalyzer
    )

    fun get(type: ExerciseType): ExerciseAnalyzer =
        analyzers.getValue(type)
}
