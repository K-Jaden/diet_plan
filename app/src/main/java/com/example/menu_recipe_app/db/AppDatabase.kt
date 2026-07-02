package com.example.menu_recipe_app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// 💡 entities 배열에 RecipeEntity와 MealEntity를 모두 등록합니다!
@Database(entities = [RecipeEntity::class,
    MealEntity::class,
    IngredientEntity::class,
    UserProfileEntity::class],
    version = 2,
    exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // DAO들을 연결해줍니다.
    abstract fun recipeDao(): RecipeDao
    abstract fun mealDao(): MealDao
    abstract fun ingredientDao(): IngredientDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "menu_recipe_database" // DB 파일 이름 하나로 통합
                )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}