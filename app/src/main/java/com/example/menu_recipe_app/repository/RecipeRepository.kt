package com.example.menu_recipe_app.repository

import com.example.menu_recipe_app.api.ApiKeys
import com.example.menu_recipe_app.api.FoodSafetyApiClient
import com.example.menu_recipe_app.crawler.TenThousandRecipeCrawler
import com.example.menu_recipe_app.db.RecipeDao
import com.example.menu_recipe_app.db.RecipeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RecipeRepository(
    private val dao: RecipeDao,
    private val crawler: TenThousandRecipeCrawler = TenThousandRecipeCrawler(),
    private val api: FoodSafetyApiClient = FoodSafetyApiClient(ApiKeys.FOOD_SAFETY_API_KEY)
) {
    companion object {
        private const val PRELOAD_TOTAL = 1000
        private const val PAGE_SIZE = 100
    }
    suspend fun setFavorite(id: Int, fav: Boolean) = dao.setFavorite(id, fav)
    private val mutex = Mutex()
    private val inFlight = mutableSetOf<String>()

    /** DB가 비어있는지 = 프리로드가 필요한지 */
    suspend fun needsPreload(): Boolean = dao.getRecipeCount() == 0
    suspend fun hasRecipe(menuName: String): Boolean =
        dao.getRecipeByName(menuName.trim()) != null

    /** 식약처 API에서 100개씩 받아 DB 저장. onProgress로 진행률 콜백 */
    suspend fun preloadFromApi(
        onProgress: (loaded: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<Int> {
        if (!needsPreload()) return Result.success(0)

        if (ApiKeys.FOOD_SAFETY_API_KEY.isBlank()) {
            return Result.failure(IllegalStateException("API 키가 설정되지 않았어요 (local.properties 확인)"))
        }

        var loaded = 0
        var start = 1
        while (start <= PRELOAD_TOTAL) {
            val end = minOf(start + PAGE_SIZE - 1, PRELOAD_TOTAL)
            val chunk = api.fetchRecipes(start, end).getOrElse { e ->
                return if (loaded > 0) Result.success(loaded) else Result.failure(e)
            }
            if (chunk.isEmpty()) break

            dao.insertRecipes(chunk)
            loaded += chunk.size
            onProgress(loaded, PRELOAD_TOTAL)
            start = end + 1
        }
        return Result.success(loaded)
    }

    /** DB 우선 → 없으면 만개의레시피 크롤링 폴백 */
    suspend fun getRecipe(menuName: String): Result<RecipeEntity> {
        val key = menuName.trim()
        dao.getRecipeByName(key)?.let { return Result.success(it) }

        mutex.withLock { inFlight.add(key) }
        return try {
            dao.getRecipeByName(key)?.let { return Result.success(it) }
            val result = crawler.crawlRecipe(key)
            result.onSuccess { dao.insertRecipe(it) }
            result
        } finally {
            mutex.withLock { inFlight.remove(key) }
        }
    }

    /** [D/E 연동용] AI 생성 레시피 저장 */
    suspend fun saveRecipeFromAi(recipe: RecipeEntity) {
        if (dao.getRecipeByName(recipe.menuName) == null) dao.insertRecipe(recipe)
    }

    fun observeAllRecipes(): Flow<List<RecipeEntity>> = dao.observeAllRecipes()
    fun searchRecipes(query: String): Flow<List<RecipeEntity>> = dao.searchRecipes(query)
}