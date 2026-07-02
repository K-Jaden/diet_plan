package com.example.menu_recipe_app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

// 캘린더와 연동될 '식단 기록' 테이블입니다.
@Entity(tableName = "meal_table")
data class MealEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val date: String,          // 식단 날짜 (예: "2026-07-02")
    val mealType: String,      // 식사 종류 (예: "아침", "점심", "저녁", "간식")
    val menuName: String,      // 메뉴 이름 (예: "차돌박이 된장찌개, 밥")
    val calories: Int,         // 칼로리 (예: 450)
    val isEaten: Boolean = false // 실제 섭취 여부 (UI에서 체크박스 용도)
)