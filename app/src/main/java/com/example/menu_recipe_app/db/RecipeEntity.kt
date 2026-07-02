package com.example.menu_recipe_app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

// tableName으로 실제 DB에 저장될 표 이름을 정합니다.
@Entity(tableName = "recipe_table")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) // 데이터가 추가될 때마다 ID를 1, 2, 3... 자동으로 부여합니다.
    val id: Int = 0,

    val menuName: String,     // 요리 이름 (예: 김치찌개)
    val ingredients: String,  // 재료
    val instructions: String, // 조리 순서
    val imageUrl: String?,    // 사진 URL
    val calories: Int? = null, // 칼로리
    val embedding: String? = null // 임베딩 벡터 (FloatArray의 JSON 문자열)
)