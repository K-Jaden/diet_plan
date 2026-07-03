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
    val calories: String? = null  // 열량 (kcal)
)