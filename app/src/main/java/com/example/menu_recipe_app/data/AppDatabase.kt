package com.example.menu_recipe_app.data

import android.content.Context
import androidx.room.*

@Database(entities = [DietPlanEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dietDao(): DietDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "recipe_app_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
class Converters {
    @TypeConverter
    fun fromMeal(meal: Meal?): String? {
        return Gson().toJson(meal)
    }

    @TypeConverter
    fun toMeal(mealString: String?): Meal? {
        return Gson().fromJson(mealString, Meal::class.java)
    }
}
