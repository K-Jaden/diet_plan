package com.example.menu_recipe_app.ai

import android.util.Log
import com.example.menu_recipe_app.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.ai.client.generativeai.type.content
import kotlinx.serialization.json.Json

/**
 * Gemini API 호출 + JSON 파싱 + 에러 처리를 담당하는 핵심 서비스
 */
class GeminiService {

    companion object {
        private const val TAG = "GeminiService"
    }

    // JSON 파서 설정 (유연하게 — 알 수 없는 키 무시, 기본값 허용)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // =============================================
    // 결과 래퍼 클래스
    // =============================================
    sealed class MealPlanResult {
        data class Success(val plan: WeeklyMealPlan) : MealPlanResult()
        data class Error(val message: String, val type: ErrorType) : MealPlanResult()
    }

    enum class ErrorType {
        NETWORK,      // 인터넷 끊김, 타임아웃
        PARSE,        // AI가 예상과 다른 형식으로 응답
        API_KEY,      // 키 만료/잘못된 키
        RATE_LIMIT,   // 무료 tier 호출 제한 초과
        UNKNOWN       // 기타
    }

    // =============================================
    // 메인 함수: 식단 생성 요청
    // =============================================
    suspend fun generateMealPlan(
        agentType: AgentType,
        userCalories: Int?,
        ingredients: List<String>,
        excludedIngredients: List<String>,
        mealsPerDay: Int,
        includeSnack: Boolean,
        mealStyle: String,
        additionalRequest: String = ""
    ): MealPlanResult {

        // 1. 유저 프롬프트 조립
        val userPrompt = buildUserPrompt(
            userCalories = userCalories,
            ingredients = ingredients,
            excludedIngredients = excludedIngredients,
            mealsPerDay = mealsPerDay,
            includeSnack = includeSnack,
            mealStyle = mealStyle,
            additionalRequest = additionalRequest
        )

        Log.d(TAG, "=== 프롬프트 전송 ===")
        Log.d(TAG, "에이전트: ${agentType.name}")
        Log.d(TAG, "시스템: ${agentType.systemPrompt.take(100)}...")
        Log.d(TAG, "유저: ${userPrompt.take(200)}...")

        // 2. Gemini 호출 (에러 시 1회 자동 재시도)
        return try {
            val result = callGemini(agentType.systemPrompt, userPrompt)
            result
        } catch (e: Exception) {
            Log.w(TAG, "1차 시도 실패, 재시도합니다: ${e.message}")
            try {
                val retryResult = callGemini(agentType.systemPrompt, userPrompt)
                retryResult
            } catch (retryException: Exception) {
                Log.e(TAG, "2차 시도도 실패: ${retryException.message}")
                classifyError(retryException)
            }
        }
    }

    // =============================================
    // Gemini API 호출 + JSON 파싱
    // =============================================
    private suspend fun callGemini(systemPrompt: String, userPrompt: String): MealPlanResult {
        val model = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY,
            generationConfig = generationConfig {
                responseMimeType = "application/json"  // ★ JSON 강제 출력
                temperature = 0.7f
            },
            systemInstruction = content { text(systemPrompt) }
        )

        val response = model.generateContent(userPrompt)
        val responseText = response.text ?: throw Exception("AI 응답이 비어있습니다.")

        Log.d(TAG, "=== AI 응답 수신 (${responseText.length}자) ===")
        Log.d(TAG, responseText.take(500))

        // JSON → WeeklyMealPlan 파싱
        return try {
            val mealPlan = json.decodeFromString<WeeklyMealPlan>(responseText)

            // 검증: 7일치가 맞는지 확인
            if (mealPlan.days.size != 7) {
                Log.w(TAG, "경고: ${mealPlan.days.size}일치 데이터 수신 (7일 예상)")
            }

            Log.d(TAG, "✅ 파싱 성공! ${mealPlan.days.size}일치 식단")
            MealPlanResult.Success(mealPlan)
        } catch (parseException: Exception) {
            Log.e(TAG, "JSON 파싱 실패: ${parseException.message}")
            MealPlanResult.Error(
                "식단 데이터를 분석하지 못했습니다. 다시 시도해주세요.",
                ErrorType.PARSE
            )
        }
    }

    // =============================================
    // 유저 프롬프트 조립
    // =============================================
    private fun buildUserPrompt(
        userCalories: Int?,
        ingredients: List<String>,
        excludedIngredients: List<String>,
        mealsPerDay: Int,
        includeSnack: Boolean,
        mealStyle: String,
        additionalRequest: String
    ): String {
        val sb = StringBuilder()

        sb.appendLine("7일치(월요일~일요일) 한국인 식단표를 JSON으로 생성해주세요.")
        sb.appendLine()

        // 칼로리 정보
        if (userCalories != null) {
            sb.appendLine("【칼로리 기준】하루 총 섭취 칼로리: 약 ${userCalories}kcal")
        } else {
            sb.appendLine("【칼로리 기준】성인 평균 하루 권장 칼로리(약 2000kcal) 기준")
        }
        sb.appendLine()

        // 보유 재료
        if (ingredients.isNotEmpty()) {
            sb.appendLine("【보유 재료】${ingredients.joinToString(", ")}")
            sb.appendLine("위 재료를 우선적으로 활용해주세요.")
        } else {
            sb.appendLine("【보유 재료】없음 — 자유롭게 재료를 선정해주세요.")
        }
        sb.appendLine()

        // 제외 재료
        if (excludedIngredients.isNotEmpty()) {
            sb.appendLine("【제외 재료 (알레르기/기피)】${excludedIngredients.joinToString(", ")}")
            sb.appendLine("위 재료는 절대 사용하지 마세요!")
        }
        sb.appendLine()

        // 끼니 설정
        val mealInfo = if (mealsPerDay == 2) "2끼 (점심/저녁)" else "3끼 (아침/점심/저녁)"
        sb.appendLine("【끼니 수】$mealInfo")
        if (includeSnack) {
            sb.appendLine("【간식】가벼운 간식 포함")
        }
        sb.appendLine("【식단 스타일】$mealStyle")
        sb.appendLine()

        // 추가 요청사항
        if (additionalRequest.isNotBlank()) {
            sb.appendLine("【추가 요청사항】$additionalRequest")
            sb.appendLine()
        }

        // JSON 스키마 지정
        sb.appendLine("【출력 형식】아래 JSON 스키마를 정확히 따라주세요:")
        sb.appendLine("""
{
  "days": [
    {
      "dayName": "월요일",
      "breakfast": {
        "menuName": "메뉴 이름",
        "ingredients": ["재료1", "재료2"],
        "calories": 400,
        "recipe": "1. 조리법 첫 단계. 2. 두 번째 단계."
      },
      "lunch": { ... },
      "dinner": { ... },
      "snack": { "menuName": "...", "ingredients": [...], "calories": 150, "recipe": "..." },
      "totalCalories": 1800
    }
  ]
}
        """.trimIndent())
        sb.appendLine()

        if (mealsPerDay == 2) {
            sb.appendLine("※ 2끼 식단이므로 breakfast의 menuName을 \"없음\"으로, ingredients를 빈 배열, calories를 0, recipe를 \"해당없음\"으로 설정하세요.")
        }
        if (!includeSnack) {
            sb.appendLine("※ 간식 미포함이므로 snack은 null로 설정하세요.")
        }

        sb.appendLine("※ days 배열에는 반드시 7개(월~일) 요소가 있어야 합니다.")
        sb.appendLine("※ recipe는 한국어로 간결하게 3~5단계로 작성하세요.")
        sb.appendLine("※ 같은 메뉴가 일주일에 2번 이상 반복되지 않도록 하세요.")

        return sb.toString()
    }

    // =============================================
    // 에러 분류
    // =============================================
    private fun classifyError(exception: Exception): MealPlanResult.Error {
        val message = exception.message ?: "알 수 없는 오류"
        Log.e(TAG, "에러 분류: $message", exception)

        return when {
            message.contains("API key", ignoreCase = true) ||
            message.contains("PERMISSION_DENIED", ignoreCase = true) ||
            message.contains("403") ->
                MealPlanResult.Error(
                    "API 키가 올바르지 않습니다. 설정을 확인해주세요.",
                    ErrorType.API_KEY
                )

            message.contains("429") ||
            message.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
            message.contains("quota", ignoreCase = true) ->
                MealPlanResult.Error(
                    "오늘의 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.",
                    ErrorType.RATE_LIMIT
                )

            message.contains("Unable to resolve host", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true) ||
            message.contains("connect", ignoreCase = true) ||
            exception is java.net.UnknownHostException ||
            exception is java.net.SocketTimeoutException ->
                MealPlanResult.Error(
                    "인터넷 연결을 확인해주세요.",
                    ErrorType.NETWORK
                )

            else ->
                MealPlanResult.Error(
                    "식단 생성에 실패했습니다. 다시 시도해주세요.\n($message)",
                    ErrorType.UNKNOWN
                )
        }
    }
}
