package com.example.menu_recipe_app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        RecipeEntity::class,
        MealPlanEntity::class,
        IngredientEntity::class,
        UserProfileEntity::class,
        UserEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao
    abstract fun ingredientDao(): IngredientDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun userDao(): UserDao

    // AI 식단 DAO
    abstract fun mealPlanDao(): MealPlanDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "menu_recipe_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(AppDatabaseCallback(context)) // ★ 콜백 연결 (초기 데이터 셋업)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }

    // ==========================================
    // ★ DB 최초 생성 시 초기 데이터(Dummy Data)를 삽입하는 콜백
    // ==========================================
    private class AppDatabaseCallback(
        private val context: Context
    ) : RoomDatabase.Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)

            // DB 작업은 백그라운드 스레드(IO)에서 실행해야 합니다.
            CoroutineScope(Dispatchers.IO).launch {
                val database = getDatabase(context)
                val recipeDao = database.recipeDao()
                val ingredientDao = database.ingredientDao()

                // 더미 레시피 삭제 (API에서 1000개를 자동으로 받아오므로 불필요)

                // 2. 내 냉장고 기본 재료 세팅
                val initialIngredients = listOf(
                    IngredientEntity(name = "양파"),
                    IngredientEntity(name = "대파"),
                    IngredientEntity(name = "계란"),
                    IngredientEntity(name = "마늘")
                )
                ingredientDao.insertIngredients(initialIngredients)
            }
        }
    }
}