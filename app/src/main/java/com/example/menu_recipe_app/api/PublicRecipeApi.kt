package com.example.menu_recipe_app.api

import retrofit2.http.GET
import retrofit2.http.Path

interface PublicRecipeApi {
    /**
     * 식약처 조리식품 레시피 API (COOKRCP01)
     * 예: http://openapi.foodsafetykorea.go.kr/api/{key}/COOKRCP01/json/{startIdx}/{endIdx}/RCP_PARTS_DTLS={ingredient}
     */
    @GET("api/{key}/COOKRCP01/json/{startIdx}/{endIdx}/RCP_PARTS_DTLS={ingredient}")
    suspend fun searchRecipesByIngredient(
        @Path("key") apiKey: String,
        @Path("startIdx") startIdx: Int,
        @Path("endIdx") endIdx: Int,
        @Path("ingredient") ingredient: String
    ): CookRcpResponse
}
