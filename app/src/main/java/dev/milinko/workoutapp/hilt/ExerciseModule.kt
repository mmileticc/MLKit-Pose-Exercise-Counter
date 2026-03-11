package dev.milinko.workoutapp.hilt

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.milinko.workoutapp.exercise.ExerciseAnalyzer
import dev.milinko.workoutapp.exercise.PullUpAnalyzer
import dev.milinko.workoutapp.exercise.PushUpAnalyzer
import dev.milinko.workoutapp.pose.PoseDetectorProcessor

@Module
@InstallIn(SingletonComponent::class)
object ExerciseModule {

    @Provides
    fun providePushUpAnalyzer(): PushUpAnalyzer = PushUpAnalyzer()

    @Provides
    fun providePullUpAnalyzer(): PullUpAnalyzer = PullUpAnalyzer()

    @Provides
    fun providePoseDetectorProcessor(): PoseDetectorProcessor =
        PoseDetectorProcessor()
}