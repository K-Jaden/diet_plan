package com.example.menu_recipe_app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {

    // 1. 식단 저장
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: MealEntity)

    // 2. 특정 날짜의 식단만 가져오기 (캘린더에서 날짜 클릭 시 사용)
    // Flow를 사용하여 DB 변경 시 UI가 즉시 반응하게 합니다.
    @Query("SELECT * FROM meal_table WHERE date = :targetDate ORDER BY id ASC")
    fun getMealsByDate(targetDate: String): Flow<List<MealEntity>>

    // 3. 특정 기간의 식단 가져오기 (달력에 점(Indicator) 찍을 때 사용)
    @Query("SELECT * FROM meal_table WHERE date BETWEEN :startDate AND :endDate")
    fun getMealsBetweenDates(startDate: String, endDate: String): Flow<List<MealEntity>>

    // 4. 식단 삭제하기
    @Query("DELETE FROM meal_table WHERE id = :mealId")
    suspend fun deleteMeal(mealId: Int)

    // 식단 여러 개를 한 번에 저장하는 기능
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeals(meals: List<MealEntity>)
}