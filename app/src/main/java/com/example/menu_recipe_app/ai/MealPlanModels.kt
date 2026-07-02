package com.example.menu_recipe_app.ai

import kotlinx.serialization.Serializable

/**
 * AI 응답을 매핑할 데이터 클래스들
 * Gemini가 반환하는 JSON을 이 구조로 변환합니다.
 */

// 7일치 식단 전체
@Serializable
data class WeeklyMealPlan(
    val days: List<DailyMealPlan>
)

// 하루 식단
@Serializable
data class DailyMealPlan(
    val dayName: String,         // "월요일", "화요일" ...
    val breakfast: Meal,
    val lunch: Meal,
    val dinner: Meal,
    val snack: Meal? = null,     // 선택적 간식 (null 허용)
    val totalCalories: Int
)

// 한 끼니
@Serializable
data class Meal(
    val menuName: String,                // "김치찌개"
    val ingredients: List<String>,       // ["김치", "돼지고기", "두부"]
    val calories: Int,                   // 350
    val recipe: String                   // "1. 김치를 볶는다. 2. 물을 넣고 끓인다..."
)
