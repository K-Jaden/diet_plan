package com.example.menu_recipe_app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// ★ MealPlanEntity 추가, 버전 2로 업그레이드
@Database(
    entities = [RecipeEntity::class, MealPlanEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    // 기존 DAO
    abstract fun recipeDao(): RecipeDao

    // ★ 새로운 식단 DAO
    abstract fun mealPlanDao(): MealPlanDao

    // DB 객체는 앱 전체에서 딱 1개만 만들어져야 하므로 Singleton 패턴을 사용합니다.
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "recipe_database" // 스마트폰 내부에 저장될 실제 파일 이름
                )
                    // DB 구조(version)가 바뀌었을 때 이전 데이터를 날리고 새로 만들지 설정
                    .fallbackToDestructiveMigration(true)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}