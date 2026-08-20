package dev.milinko.workoutapp.exercise

/**
 * Closed set of exercises that have a camera-based [ExerciseAnalyzer].
 * [id] matches the value already stored in [dev.milinko.workoutapp.db.entitys.Exercise.name]
 * for these two exercises, so no data migration is needed.
 *
 * Manual logging is NOT limited to this enum - it accepts any free-text
 * exercise name (see ExerciseViewModel.logManualExercise).
 */
enum class ExerciseType(val id: String, val displayName: String, val isTimeBased: Boolean) {
    PUSH_UPS(id = "Push Ups", displayName = "Push Ups", isTimeBased = false),
    PULL_UPS(id = "Pull Ups", displayName = "Pull Ups", isTimeBased = false);

    companion object {
        fun fromId(id: String): ExerciseType? = entries.find { it.id == id }
    }
}
