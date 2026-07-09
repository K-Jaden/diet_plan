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
    val imageUrl: String?,    // 사진 URL (사진이 없을 수도 있으니 '?'를 붙여 null 허용)
    val servings: String? = null, // 레시피가 몇인분 기준으로 작성됐는지 저장
    val calories: Int? = null, // 칼로리 (일일 총 칼로리 합산 등 연산에 쓰이므로 Int 유지)
    val embedding: String? = null // 임베딩 벡터 (FloatArray의 JSON 문자열) - 하이브리드 RAG 유사도 검색용
)