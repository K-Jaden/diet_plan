package com.example.menu_recipe_app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.menu_recipe_app.Meal

@Entity(tableName = "diet_plan")
data class DietPlanEntity(
    @PrimaryKey val date: String, // 달력 날짜 "2026-06-25"가 키가 됩니다.
    val dayName: String,
    val breakfast: Meal,
    val lunch: Meal,
    val dinner: Meal,
    val snack: Meal?,
    val totalCalories: Int,
    val totalCarbs: Int,
    val totalProtein: Int,
    val totalFat: Int,
    val dailyFeedback: String
)