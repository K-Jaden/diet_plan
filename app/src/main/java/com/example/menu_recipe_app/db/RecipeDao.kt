package com.example.menu_recipe_app.dbimport

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.menu_recipe_app.db.RecipeEntity

@Dao
interface RecipeDao {

    // 1. 레시피 저장 (이미 같은 ID가 있다면 덮어쓰기)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: RecipeEntity)

    // 2. 이름으로 특정 레시피 하나만 찾기
    @Query("SELECT * FROM recipe_table WHERE menuName = :name LIMIT 1")
    suspend fun getRecipeByName(name: String): RecipeEntity?

    // 3. 저장된 모든 레시피 다 가져오기
    @Query("SELECT * FROM recipe_table ORDER BY id DESC")
    suspend fun getAllRecipes(): List<RecipeEntity>

    // 4. 레시피 삭제하기
    @Query("DELETE FROM recipe_table WHERE id = :recipeId")
    suspend fun deleteRecipe(recipeId: Int)
    // RecipeDao.kt 파일 내부

    @Query("SELECT * FROM recipe_table WHERE id = :id")
    suspend fun getRecipeById(id: Int): RecipeEntity // 특정 ID의 레시피 1개만 가져오기
}