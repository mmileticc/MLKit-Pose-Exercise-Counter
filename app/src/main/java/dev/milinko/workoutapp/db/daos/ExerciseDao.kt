package dev.milinko.workoutapp.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Update
import dev.milinko.workoutapp.db.entitys.Exercise

@Dao
interface ExerciseDao {

    @Insert
    suspend fun insert( e : Exercise): Long

    @Update
    suspend fun update (e: Exercise)

    @Delete
    suspend fun delete(e: Exercise)

    @androidx.room.Query("SELECT * FROM Exercise ORDER BY date DESC")
    fun getAllExercises(): kotlinx.coroutines.flow.Flow<List<Exercise>>

    @androidx.room.Query("SELECT DISTINCT name FROM Exercise ORDER BY name")
    fun getDistinctExerciseNames(): kotlinx.coroutines.flow.Flow<List<String>>
}