package com.example.menu_recipe_app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AI가 생성한 식단을 날짜별/끼니별로 저장하는 Entity
 * 하루 식단은 여러 행(아침/점심/저녁/간식)으로 나뉘어 저장됩니다.
 */
@Entity(tableName = "meal_plan_table")
data class MealPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val date: String,             // "2026-07-02" (ISO 형식)
    val dayName: String,          // "수요일"
    val agentType: String,        // "Budget" / "Family" / "BloodSugar"
    val mealType: String,         // "breakfast" / "lunch" / "dinner" / "snack"
    val menuName: String,         // "김치찌개"
    val ingredients: String,      // JSON 문자열: "[\"김치\",\"돼지고기\",\"두부\"]"
    val calories: Int,            // 350
    val recipe: String,           // "1. 김치를 볶는다. 2. 물을 넣고 끓인다..."
    val totalDayCalories: Int     // 하루 총 칼로리
)
