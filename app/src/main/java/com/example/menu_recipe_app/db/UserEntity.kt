package com.example.menu_recipe_app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_table")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: String,        // 로그인용 아이디
    val password: String,      // 비밀번호 (평문)
    val name: String,          // 이름 또는 닉네임
    val gender: String = "",   // 성별 (예: "남성", "여성")
    val age: Int = 0,          // 나이
    val height: Float = 0f,    // 키 (cm)
    val weight: Float = 0f,    // 몸무게 (kg)
    val activityLevel: String = "", // 평소 활동량
    val dietGoal: String = "",      // 식단 목표
    val recommendedCalories: Int = 0 // 권장 칼로리
)
