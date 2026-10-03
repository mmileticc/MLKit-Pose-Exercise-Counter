package dev.milinko.workoutapp.exercise

import android.graphics.PointF

/**
 * One pose landmark's position + confidence, after EMA smoothing/outlier rejection in
 * ExerciseViewModel.
 *
 * This replaces passing around ML Kit's own `PoseLandmark` downstream of smoothing. ML Kit's
 * PoseLandmark has no public constructor/setters for arbitrary values (it's only meant to be
 * built internally by the detector), so the smoothed coordinates used to be force-written into
 * an existing PoseLandmark via reflection (getDeclaredField + setAccessible) on every landmark,
 * every frame - a real per-frame cost, and doubly so on every frame where the guessed field name
 * didn't match ML Kit's actual (minified) field, since that meant throwing and catching a
 * NoSuchFieldException (with a full stack trace) per landmark. A plain data class instead needs
 * no reflection at all.
 */
data class TrackedLandmark(
    val position: PointF,
    val inFrameLikelihood: Float
)
