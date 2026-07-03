package com.example.menu_recipe_app.api

import com.example.menu_recipe_app.BuildConfig

/**
 * API 키는 local.properties의 RECIPE_API_KEY에서 읽어옴 (팀 컨벤션)
 * local.properties는 Git이 자동으로 무시하므로 깃헙에 올라가지 않음.
 * 각 팀원은 자기 local.properties에 키를 직접 추가해야 함.
 */
object ApiKeys {
    const val FOOD_SAFETY_API_KEY = BuildConfig.RECIPE_API_KEY
}