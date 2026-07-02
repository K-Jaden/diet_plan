package com.example.menu_recipe_app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 식단 데이터 CRUD를 담당하는 DAO
 */
@Dao
interface MealPlanDao {

    // 식단 저장 (같은 ID가 있으면 덮어쓰기)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealPlan(plan: MealPlanEntity)

    // 여러 건 한꺼번에 저장
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plans: List<MealPlanEntity>)

    // 특정 날짜의 식단 가져오기
    @Query("SELECT * FROM meal_plan_table WHERE date = :date ORDER BY id")
    suspend fun getMealsByDate(date: String): List<MealPlanEntity>

    // 날짜 범위로 식단 가져오기 (7일치 조회용)
    @Query("SELECT * FROM meal_plan_table WHERE date BETWEEN :startDate AND :endDate ORDER BY date, id")
    suspend fun getMealsByDateRange(startDate: String, endDate: String): List<MealPlanEntity>

    // 날짜 범위의 식단 삭제 (재생성 시 기존 데이터 교체용)
    @Query("DELETE FROM meal_plan_table WHERE date BETWEEN :startDate AND :endDate")
    suspend fun deleteMealsByDateRange(startDate: String, endDate: String)

    // 저장된 모든 식단 가져오기
    @Query("SELECT * FROM meal_plan_table ORDER BY date, id")
    suspend fun getAllMealPlans(): List<MealPlanEntity>

    // 식단이 등록된 모든 고유 날짜(String) 가져오기 (캘린더 점 찍기용)
    @Query("SELECT DISTINCT date FROM meal_plan_table")
    suspend fun getAllMealPlanDates(): List<String>

    // 특정 날짜에 식단이 1개라도 존재하는지 확인 (중복 생성 방지용)
    @Query("SELECT EXISTS(SELECT 1 FROM meal_plan_table WHERE date = :date)")
    suspend fun hasMealPlanForDate(date: String): Boolean
}
