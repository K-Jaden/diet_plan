package com.example.menu_recipe_app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipe_table")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val menuName: String,     // 요리 이름 (예: 김치찌개)
    val ingredients: String,  // 재료
    val instructions: String, // 조리 순서
    val imageUrl: String?,    // 사진 URL (사진이 없을 수도 있으니 '?'를 붙여 null 허용)
    val servings: String? = null, // 레시피가 몇인분 기준으로 작성됐는지 저장 (예: "4인분")
    val calories: String? = null, // 열량 (kcal) - FoodSafetyApiClient가 원문 문자열로 제공하므로 String 유지
    val embedding: String? = null // 임베딩 벡터 (FloatArray의 JSON 문자열) - 하이브리드 RAG 유사도 검색용
)