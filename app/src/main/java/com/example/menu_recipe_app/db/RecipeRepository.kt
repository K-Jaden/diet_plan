package com.example.menu_recipe_app.db

import android.content.Context
import android.util.Log
import com.example.menu_recipe_app.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStreamReader

@Serializable
data class RecipeJson(
    val menuName: String,
    val ingredients: String,
    val instructions: String,
    val calories: Int,
    val embedding: List<Float>? = null
)

class RecipeRepository(
    private val context: Context,
    private val recipeDao: RecipeDao
) {
    private val TAG = "RecipeRepository"
    
    // 임베딩 모델 인스턴스 (Gemini의 text-embedding-004 사용)
    private val embeddingModel = GenerativeModel(
        modelName = "text-embedding-004",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    /**
     * 최초 앱 실행 시 (또는 DB가 비어있을 때) 로컬 JSON을 읽어 DB에 넣고, 임베딩을 생성합니다.
     */
    suspend fun seedDatabaseIfNeeded() {
        withContext(Dispatchers.IO) {
            val count = recipeDao.getAllRecipes().size
            if (count == 0) {
                Log.d(TAG, "DB가 비어있습니다. 초기 레시피 데이터를 로드합니다.")
                loadRecipesFromJson()
            } else {
                Log.d(TAG, "이미 DB에 ${count}개의 레시피가 있습니다.")
            }
        }
    }

    /**
     * HTTP를 통해 임베딩을 가져오는 함수
     */
    private suspend fun fetchEmbedding(text: String): List<Float> {
        return withContext(Dispatchers.IO) {
            val url = java.net.URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-2:embedContent?key=${BuildConfig.GEMINI_API_KEY}")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val requestBody = """
                {
                  "model": "models/gemini-embedding-2",
                  "content": {
                    "parts": [{
                      "text": "${text.replace("\"", "\\\"").replace("\n", " ")}"
                    }]
                  }
                }
            """.trimIndent()

            connection.outputStream.use { os ->
                val input = requestBody.toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            if (connection.responseCode == 200) {
                val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                // JSON 파싱 (간단히 정규식이나 Json 파서 활용)
                // 응답 구조: { "embedding": { "values": [0.1, 0.2, ...] } }
                val parser = Json { ignoreUnknownKeys = true }
                val parsed = parser.decodeFromString<kotlinx.serialization.json.JsonObject>(responseString)
                val embeddingObj = parsed["embedding"] as? kotlinx.serialization.json.JsonObject
                val valuesArray = embeddingObj?.get("values") as? kotlinx.serialization.json.JsonArray
                valuesArray?.map { it.toString().toFloat() } ?: emptyList()
            } else {
                throw Exception("HTTP Error ${connection.responseCode}: ${connection.errorStream.bufferedReader().use { it.readText() }}")
            }
        }
    }

    private suspend fun loadRecipesFromJson() {
        try {
            // 1. assets 폴더에서 recipes.json 읽기
            val inputStream = context.assets.open("recipes.json")
            val jsonString = InputStreamReader(inputStream).readText()
            
            // 2. 파싱
            val jsonParser = Json { ignoreUnknownKeys = true }
            val recipes = jsonParser.decodeFromString<List<RecipeJson>>(jsonString)
            
            // 3. 임베딩 생성 및 DB 저장
            for (recipe in recipes) {
                // 재료 텍스트를 기준으로 임베딩을 생성합니다.
                val embeddingText = "요리명: ${recipe.menuName}, 재료: ${recipe.ingredients}"
                
                Log.d(TAG, "'${recipe.menuName}' 초기 적재 중...")
                var embeddingJson: String? = null

                if (recipe.embedding != null) {
                    embeddingJson = recipe.embedding.joinToString(separator = ",", prefix = "[", postfix = "]")
                } else {
                    try {
                        // API 호출 (HTTP) - 파일에 임베딩이 없는 경우만 (하위 호환)
                        Log.d(TAG, "'${recipe.menuName}' 임베딩 생성 중...")
                        val vectorList = fetchEmbedding(embeddingText)
                        embeddingJson = vectorList.joinToString(separator = ",", prefix = "[", postfix = "]")
                    } catch (e: Exception) {
                        Log.e(TAG, "임베딩 생성 실패: ${recipe.menuName} - ${e.message}")
                    }
                }

                // Entity로 변환하여 DB에 저장
                val entity = RecipeEntity(
                    menuName = recipe.menuName,
                    ingredients = recipe.ingredients,
                    instructions = recipe.instructions,
                    imageUrl = null,
                    calories = recipe.calories,
                    embedding = embeddingJson
                )
                recipeDao.insertRecipe(entity)
            }
            Log.d(TAG, "총 ${recipes.size}개 레시피 초기 적재 및 임베딩 완료!")
        } catch (e: Exception) {
            Log.e(TAG, "초기 데이터 적재 실패", e)
        }
    }

    /**
     * 사용자 입력 재료를 벡터로 변환한 뒤, DB의 레시피들과 코사인 유사도를 계산하여 반환합니다.
     */
    suspend fun searchRecipesByIngredients(userIngredients: List<String>, limit: Int = 10): List<RecipeEntity> {
        return withContext(Dispatchers.IO) {
            if (userIngredients.isEmpty()) {
                return@withContext recipeDao.getAllRecipes().take(limit)
            }

            // 1. 사용자 입력 텍스트를 임베딩
            val queryText = "주재료: ${userIngredients.joinToString(", ")}"
            val queryVector: List<Float>
            try {
                queryVector = fetchEmbedding(queryText)
            } catch (e: Exception) {
                Log.e(TAG, "쿼리 임베딩 실패", e)
                // 실패 시 그냥 전체 목록 반환
                return@withContext recipeDao.getAllRecipes().take(limit)
            }

            // 2. DB에서 모든 레시피를 가져와서 코사인 유사도 계산
            val allRecipes = recipeDao.getAllRecipes()
            val scoredRecipes = allRecipes.mapNotNull { recipe ->
                recipe.embedding?.let { embeddingStr ->
                    try {
                        // "[0.1, 0.2, ...]" 형태의 문자열을 List<Float>로 변환
                        val vector = embeddingStr
                            .removeSurrounding("[", "]")
                            .split(",")
                            .map { it.trim().toFloat() }
                            
                        val score = cosineSimilarity(queryVector, vector)
                        Pair(recipe, score)
                    } catch (e: Exception) { null }
                } ?: Pair(recipe, 0f)
            }

            // 유사도 0.4 이상인 레시피만 필터링
            var topRecipes = scoredRecipes
                .filter { it.second > 0.4f }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }

            // 레이지 로딩 로직: 검색 결과가 5개 미만이면 공공데이터 API 호출
            if (topRecipes.size < 5) {
                Log.d(TAG, "로컬 검색 결과 부족(${topRecipes.size}개). 공공데이터 API에서 추가 레시피를 가져옵니다...")
                val success = fetchAndSaveFromPublicApi(userIngredients)
                if (success) {
                    // 새로 DB에 추가되었으므로 다시 검색
                    val newAllRecipes = recipeDao.getAllRecipes()
                    val newScoredRecipes = newAllRecipes.mapNotNull { recipe ->
                        recipe.embedding?.let { embeddingStr ->
                            try {
                                val vector = embeddingStr.removeSurrounding("[", "]").split(",").map { it.trim().toFloat() }
                                val score = cosineSimilarity(queryVector, vector)
                                Pair(recipe, score)
                            } catch (e: Exception) { null }
                        } ?: Pair(recipe, 0f)
                    }
                    topRecipes = newScoredRecipes
                        .filter { it.second > 0.4f }
                        .sortedByDescending { it.second }
                        .take(limit)
                        .map { it.first }
                }
            }

            // 여전히 부족하거나 유사도 필터링을 통과한 게 없다면, 가장 점수가 높은(또는 임의의) 데이터라도 반환
            if (topRecipes.isEmpty()) {
                return@withContext scoredRecipes.sortedByDescending { it.second }.take(limit).map { it.first }
            }

            return@withContext topRecipes
        }
    }

    /**
     * 공공데이터 API에서 유저의 첫 번째 재료를 기반으로 레시피를 검색하고 DB에 저장 (레이지 로딩)
     */
    private suspend fun fetchAndSaveFromPublicApi(userIngredients: List<String>): Boolean {
        val apiKey = BuildConfig.PUBLIC_DATA_API_KEY
        if (apiKey.isBlank() || apiKey == "YOUR_PUBLIC_DATA_API_KEY_HERE") {
            Log.w(TAG, "PUBLIC_DATA_API_KEY가 설정되지 않아 외부 API를 호출할 수 없습니다.")
            return false
        }

        // 가장 중요한 첫 번째 재료를 검색어로 사용
        val mainIngredient = userIngredients.firstOrNull() ?: return false

        return try {
            // 외부 API 호출 (예: 1번부터 10번까지 최대 10개)
            val response = com.example.menu_recipe_app.api.PublicRecipeClient.api.searchRecipesByIngredient(
                apiKey = apiKey,
                startIdx = 1,
                endIdx = 10,
                ingredient = mainIngredient
            )

            val rows = response.cookRcp01?.row
            if (rows.isNullOrEmpty()) {
                Log.d(TAG, "공공데이터 API에 '$mainIngredient' 관련 레시피가 없습니다.")
                return false
            }

            Log.d(TAG, "공공데이터 API에서 ${rows.size}개의 레시피를 가져왔습니다. 임베딩 및 저장 시작...")
            
            for (row in rows) {
                // 이미 DB에 같은 이름의 레시피가 있는지 확인 (간단하게)
                if (recipeDao.getAllRecipes().any { it.menuName == row.rcpNm }) continue

                val menuName = row.rcpNm ?: "이름 없음"
                val ingredients = row.rcpPartsDtls ?: "재료 정보 없음"
                val instructions = row.getInstructions()
                val caloriesStr = row.infoEng ?: "0"
                val calories = caloriesStr.replace(Regex("[^0-9]"), "").toIntOrNull()

                // 임베딩 텍스트 생성
                val textToEmbed = "요리 이름: ${menuName}\n재료: $ingredients\n조리법: $instructions"
                val embeddingJson: String? = try {
                    val vector = fetchEmbedding(textToEmbed)
                    "[" + vector.joinToString(", ") + "]"
                } catch (e: Exception) {
                    Log.e(TAG, "새 레시피 임베딩 생성 실패: $menuName", e)
                    null
                }

                val entity = RecipeEntity(
                    menuName = menuName,
                    ingredients = ingredients,
                    instructions = instructions,
                    imageUrl = row.attFileNoMain,
                    calories = calories,
                    embedding = embeddingJson
                )
                recipeDao.insertRecipe(entity)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "공공데이터 API 연동 실패", e)
            false
        }
    }

    // 코사인 유사도 계산 함수
    private fun cosineSimilarity(v1: List<Float>, v2: List<Float>): Float {
        if (v1.size != v2.size) return 0f
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            norm1 += v1[i] * v1[i]
            norm2 += v2[i] * v2[i]
        }
        return if (norm1 == 0f || norm2 == 0f) 0f else (dotProduct / (Math.sqrt(norm1.toDouble()) * Math.sqrt(norm2.toDouble()))).toFloat()
    }
}
