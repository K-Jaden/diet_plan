package com.example.menu_recipe_app.api

import com.example.menu_recipe_app.db.RecipeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 식품안전나라 "조리식품의 레시피 DB" (COOKRCP01) API 클라이언트
 *
 * 요청 형식:
 *   https://openapi.foodsafetykorea.go.kr/api/{키}/COOKRCP01/json/{시작번호}/{끝번호}
 *   - 한 번에 최대 1000건, 전체 약 1,100여 건의 레시피 제공
 *
 * 응답에서 사용하는 필드:
 *   RCP_NM            요리 이름
 *   RCP_PARTS_DTLS    재료 (통짜 텍스트)
 *   MANUAL01~20       조리 순서 (단계별)
 *   ATT_FILE_NO_MAIN  대표 이미지 URL
 *   INFO_ENG          열량 (kcal)
 *
 * 추가 라이브러리 없이 HttpURLConnection + org.json(안드로이드 내장)으로 구현
 * → gradle 수정 불필요
 */
class FoodSafetyApiClient(private val apiKey: String) {

    companion object {
        private const val BASE = "https://openapi.foodsafetykorea.go.kr/api"
        private const val SERVICE = "COOKRCP01"
        private const val TIMEOUT_MS = 15_000
    }

    /**
     * start~end 범위의 레시피 목록을 가져와 RecipeEntity 리스트로 반환
     * 실패 시 Result.failure (앱이 튕기지 않게)
     */
    suspend fun fetchRecipes(start: Int, end: Int): Result<List<RecipeEntity>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("$BASE/$apiKey/$SERVICE/json/$start/$end")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    requestMethod = "GET"
                }
                val body = try {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    conn.disconnect()
                }
                parse(body)
            }
        }

    private fun parse(body: String): List<RecipeEntity> {
        val root = JSONObject(body)

        // 키가 틀리면 { "RESULT": { "CODE": "ERROR-...", ... } } 형태로 옴
        root.optJSONObject("RESULT")?.let { r ->
            throw IllegalStateException("API 오류: ${r.optString("MSG", r.optString("CODE"))}")
        }

        val service = root.optJSONObject(SERVICE)
            ?: throw IllegalStateException("API 응답 형식이 예상과 다릅니다: ${body.take(150)}")

        // 서비스 내부 RESULT 코드 검사 (INFO-000 = 정상)
        service.optJSONObject("RESULT")?.let { r ->
            val code = r.optString("CODE")
            if (code.isNotBlank() && code != "INFO-000") {
                throw IllegalStateException("API 오류: ${r.optString("MSG", code)}")
            }
        }

        val rows = service.optJSONArray("row") ?: return emptyList()

        return (0 until rows.length()).mapNotNull { i ->
            val r = rows.optJSONObject(i) ?: return@mapNotNull null
            val name = r.optString("RCP_NM").trim()
            if (name.isBlank()) return@mapNotNull null

            val parts = r.optString("RCP_PARTS_DTLS").trim()

            // MANUAL01 ~ MANUAL20 중 내용 있는 것만 모아 조리순서 구성
            val steps = (1..20).mapNotNull { n ->
                r.optString("MANUAL%02d".format(n)).trim()
                    .replace("\n", " ")
                    .ifBlank { null }
            }

            // 재료 텍스트에서 "4인분" 같은 표기 추출 (없으면 null)
            val servings = Regex("""(\d+\s*인분)""").find(parts)?.groupValues?.get(1)

            RecipeEntity(
                menuName = name,
                ingredients = parts,
                // API의 MANUAL은 "1. ..." 번호가 이미 붙어있는 경우가 많음 → 중복 방지 처리
                instructions = steps.mapIndexed { idx, s ->
                    val cleaned = s.replace(Regex("""^\d+\.\s*"""), "")
                    "${idx + 1}. $cleaned"
                }.joinToString("\n"),
                imageUrl = r.optString("ATT_FILE_NO_MAIN").ifBlank { null },
                servings = servings,
                calories = r.optString("INFO_ENG").ifBlank { null }
            )
        }
    }
}
