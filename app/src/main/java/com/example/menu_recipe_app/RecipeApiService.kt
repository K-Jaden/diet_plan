package com.example.menu_recipe_app // 본인 패키지명 확인!

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import com.google.gson.annotations.SerializedName

// ==========================================
// 1. 서버 통신 명세서 & 기지국
// ==========================================
interface RecipeApiService {
    @GET("{keyId}/{serviceId}/{dataType}/{startIdx}/{endIdx}")
    suspend fun getRecipes(
        @Path("keyId") apiKey: String = BuildConfig.RECIPE_API_KEY,
        @Path("serviceId") serviceId: String = "COOKRCP01",
        @Path("dataType") dataType: String = "json",
        @Path("startIdx") startIdx: Int = 1,
        @Path("endIdx") endIdx: Int = 100
    ): RecipeResponse
}

object RetrofitClient {
    private const val BASE_URL = "http://openapi.foodsafetykorea.go.kr/api/"

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: RecipeApiService = retrofit.create(RecipeApiService::class.java)
}

// ==========================================
// 2. 서버에서 받는 데이터 바구니 (여기로 통합!)
// ==========================================
data class RecipeResponse(
    @SerializedName("COOKRCP01") val cookRcp01: CookRcp01?
)

data class CookRcp01(
    @SerializedName("row") val row: List<RecipeItem>?
)

data class RecipeItem(
    @SerializedName("RCP_NM") val rcpNm: String,
    @SerializedName("RCP_PARTS_DTLS") val rcpPartsDtls: String,
    @SerializedName("MANUAL01") val manual01: String?,
    @SerializedName("MANUAL02") val manual02: String?,
    @SerializedName("ATT_FILE_NO_MAIN") val attFileNoMain: String?,
    @SerializedName("INFO_WGT") val infoWgt: String?, // 1인분
    @SerializedName("INFO_ENG") val infoEng: String?  // 열량
)