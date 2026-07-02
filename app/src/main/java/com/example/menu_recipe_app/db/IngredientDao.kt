package com.example.menu_recipe_app.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {

    // 1. 재료 하나 추가
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredient(ingredient: IngredientEntity)

    // 2. 재료 여러 개 한 번에 추가 (초기 세팅 시 유용)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(ingredients: List<IngredientEntity>)

    // 3. 내 냉장고에 있는 모든 재료 가져오기 (UI 실시간 반영을 위해 Flow 사용)
    @Query("SELECT * FROM ingredient_table")
    fun getAllIngredients(): Flow<List<IngredientEntity>>

    // 4. 특정 재료 삭제
    @Delete
    suspend fun deleteIngredient(ingredient: IngredientEntity)

    // 5. 재료 전체 초기화 (필요할 때를 대비)
    @Query("DELETE FROM ingredient_table")
    suspend fun deleteAllIngredients()
}