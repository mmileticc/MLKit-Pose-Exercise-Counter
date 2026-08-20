package dev.milinko.workoutapp.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.milinko.workoutapp.db.daos.ExerciseDao
import dev.milinko.workoutapp.db.entitys.Exercise


@TypeConverters(DateConverter::class)
@Database(entities = [Exercise::class], version = 1, exportSchema = true)
abstract class ExerciseDatabase : RoomDatabase(){

//  registering dao-s for example -> abstract fun workoutDao(): WorkoutDao
abstract fun exerciseDao(): ExerciseDao
    companion object {
        private var INSTANCE: ExerciseDatabase? = null
        fun getDatabase(context: Context): ExerciseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExerciseDatabase::class.java,
                    "running_database" // leftover name from an earlier template; internal file name only, not worth a migration to rename
                ).build()
                INSTANCE = instance
                // return instance
                return@synchronized instance
            }
        }
    }
}