package com.example.menu_recipe_app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ★ develop의 식단/재료/프로필 엔티티 + feat/agents의 MealPlanEntity를 합치며 버전 4로 업그레이드
@Database(
    entities = [
        RecipeEntity::class,
        MealPlanEntity::class,
        MealEntity::class,
        IngredientEntity::class,
        UserProfileEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao
    abstract fun mealDao(): MealDao
    abstract fun ingredientDao(): IngredientDao
    abstract fun userProfileDao(): UserProfileDao

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
                    "menu_recipe_database"
                )
                    // DB 구조(version)가 바뀌었을 때 이전 데이터를 날리고 새로 만들지 설정
                    .fallbackToDestructiveMigration(true)
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

                // 1. 레시피 기본 데이터 세팅
                val initialRecipes = listOf(
                    RecipeEntity(
                        menuName = "된장찌개",
                        ingredients = "차돌박이 150g, 애호박 1/2개, 두부 반 모, 시판 된장 2큰술",
                        instructions = "1. 애호박과 두부를 먹기 좋은 크기로 깍둑썰기 해줍니다.\n2. 냄비에 차돌박이를 넣고 중불에서 겉면이 익을 때까지 볶아줍니다.\n3. 고기가 익으면 물 500ml를 넣고 된장을 풀어줍니다.\n4. 물이 끓어오르면 썰어둔 야채와 두부를 넣고 5분간 끓여 완성합니다.",
                        imageUrl = "https://loremflickr.com/300/300/korean,stew",
                        servings = "2인분",
                        calories = 450
                    ),
                    RecipeEntity(
                        menuName = "김치볶음밥",
                        ingredients = "신김치 1컵, 밥 1공기, 참치 1캔, 참기름 1스푼",
                        instructions = "1. 김치를 잘게 썰어 준비합니다.\n2. 팬에 식용유를 두르고 김치와 참치를 볶아줍니다.\n3. 김치가 익으면 밥을 넣고 뭉치지 않게 잘 볶아줍니다.\n4. 불을 끄고 참기름을 둘러 풍미를 더해줍니다.",
                        imageUrl = "https://loremflickr.com/300/300/friedrice",
                        servings = "1인분",
                        calories = 600
                    ),
                    RecipeEntity(
                        menuName = "계란말이",
                        ingredients = "계란 3개, 대파 1/4대, 소금 약간",
                        instructions = "1. 계란을 볼에 풀고 다진 대파와 소금을 섞어줍니다.\n2. 약불로 달군 팬에 식용유를 두르고 계란물을 얇게 폅니다.\n3. 계란이 반쯤 익으면 끝에서부터 돌돌 말아줍니다.\n4. 한 김 식힌 후 먹기 좋은 크기로 썰어냅니다.",
                        imageUrl = "https://loremflickr.com/300/300/omelet",
                        servings = "2인분",
                        calories = 300
                    ),
                    RecipeEntity(
                        menuName = "제육볶음",
                        ingredients = "돼지고기 앞다리살 300g, 양파 1/2개, 대파 1대, 고추장 2큰술, 간장 1큰술",
                        instructions = "1. 돼지고기를 먹기 좋은 크기로 썰고 양념장에 버무려 10분간 재웁니다.\n2. 팬에 기름을 약간 두르고 고기를 볶습니다.\n3. 고기가 겉면이 익으면 채썬 양파와 대파를 넣고 함께 볶습니다.\n4. 고기가 완전히 익을 때까지 센 불에서 빠르게 볶아냅니다.",
                        imageUrl = "https://loremflickr.com/300/300/spicypork",
                        servings = "2인분",
                        calories = 700
                    )
                )
                initialRecipes.forEach { recipeDao.insertRecipe(it) }

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