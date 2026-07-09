package com.example.menu_recipe_app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * ★ 통합판 DAO (B의 기존 버전 + 팀원 feat/recipeDB의 추가분 + API 프리로드용 신규)
 */
@Dao
interface RecipeDao {

    // ===== 기본 CRUD =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: RecipeEntity)

    /** [신규] API 프리로드용 대량 저장 (100개씩 청크 저장) */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipes(recipes: List<RecipeEntity>)

    @Query("SELECT * FROM recipe_table WHERE menuName = :name LIMIT 1")
    suspend fun getRecipeByName(name: String): RecipeEntity?

    /** [팀원 추가분 반영] 특정 ID의 레시피 1개 */
    @Query("SELECT * FROM recipe_table WHERE id = :id")
    suspend fun getRecipeById(id: Int): RecipeEntity?

    @Query("SELECT * FROM recipe_table ORDER BY id DESC")
    suspend fun getAllRecipes(): List<RecipeEntity>

    @Query("DELETE FROM recipe_table WHERE id = :recipeId")
    suspend fun deleteRecipe(recipeId: Int)

    /** [신규] 프리로드 필요 여부 판단용 (0개면 최초 실행) */
    @Query("SELECT COUNT(*) FROM recipe_table")
    suspend fun getRecipeCount(): Int

    // ===== 레시피 탭용 Flow 쿼리 (DB 변경 시 UI 자동 갱신) =====

    @Query("SELECT * FROM recipe_table ORDER BY id DESC")
    fun observeAllRecipes(): Flow<List<RecipeEntity>>

    /** 실시간 검색: 이름 OR 재료 (팀원 의도 반영) */
    @Query(
        "SELECT * FROM recipe_table " +
                "WHERE menuName LIKE '%' || :query || '%' OR ingredients LIKE '%' || :query || '%' " +
                "ORDER BY menuName"
    )
    fun searchRecipes(query: String): Flow<List<RecipeEntity>>
}