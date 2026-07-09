package com.example.menu_recipe_app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ingredient_table")
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String // 재료 이름 (예: "양파")
)