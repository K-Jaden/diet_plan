package com.example.menu_recipe_app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipe_table")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val menuName: String,
    val ingredients: String,
    val instructions: String,
    val imageUrl: String?,
    val servings: String? = null, // 몇인분 기준 (예: "4인분")
    val calories: String? = null,  // 열량 (kcal)
    val category: String? = null,      // 요리 종류 (밥, 국&찌개, 반찬, 일품, 후식, 기타)
    val isFavorite: Boolean = false,
    val embedding: String? = null // AI RAG용 임베딩 JSON 문자열
)