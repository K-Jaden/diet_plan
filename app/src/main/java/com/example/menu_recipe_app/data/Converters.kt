package com.example.menu_recipe_app.data

import androidx.room.TypeConverter
import com.example.menu_recipe_app.Meal
import com.google.gson.Gson

class Converters {
    private val gson = Gson()

    @TypeConverter fun fromMeal(m: Meal?): String? = gson.toJson(m)
    @TypeConverter fun toMeal(s: String?): Meal? = gson.fromJson(s, Meal::class.java)
    @TypeConverter
    fun fromMealList(list: List<Meal>?): String? = gson.toJson(list)

    @TypeConverter
    fun toMealList(value: String?): List<Meal>? =
        gson.fromJson(value, object : TypeToken<List<Meal>>() {}.type)
}