package com.example.menu_recipe_app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DietDao {
    // 캘린더에서 특정 날짜를 누르면 즉시 식단을 뱉어주는 쿼리
    @Query("SELECT * FROM diet_plan WHERE date = :date")
    fun getDietByDate(date: String): Flow<DietPlanEntity?>

    // 식단 저장하기
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDietPlan(dietPlan: DietPlanEntity)

    @Query("SELECT * FROM diet_plan")
    fun getAllDietPlans(): Flow<List<DietPlanEntity>>

    @Delete
    suspend fun deleteDietPlan(dietPlan: DietPlanEntity)
}