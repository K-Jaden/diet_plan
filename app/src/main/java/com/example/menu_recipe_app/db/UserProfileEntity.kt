package com.example.menu_recipe_app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile_table")
data class UserProfileEntity(
    // 프로필은 1명(나 자신)의 데이터만 있으면 되므로 기본키를 1로 고정합니다.
    @PrimaryKey
    val id: Int = 1,

    val selectedAgent: String, // 예: "실속관리", "혈당케어"
    val allergies: String,     // 예: "오이, 땅콩" (쉼표로 구분해서 저장)
    val mealsPerDay: Int       // 하루 몇 끼? (예: 2 또는 3)
)