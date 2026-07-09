package com.example.menu_recipe_app.ai

import android.util.Log
import com.example.menu_recipe_app.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import com.example.menu_recipe_app.db.RecipeEntity

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
        additionalRequest: String = "",
        allowedRecipes: List<RecipeEntity> = emptyList(),
        mealReuseCount: Int = 1
    ): MealPlanResult {

        // 1. 유저 프롬프트 조립
        val userPrompt = buildUserPrompt(
            userCalories = userCalories,
            ingredients = ingredients,
            excludedIngredients = excludedIngredients,
            mealsPerDay = mealsPerDay,
            includeSnack = includeSnack,
            mealStyle = mealStyle,
            additionalRequest = additionalRequest,
            allowedRecipes = allowedRecipes,
            mealReuseCount = mealReuseCount
        )

        Log.d(TAG, "=== 프롬프트 전송 ===")
        Log.d(TAG, "에이전트: ${agentType.name}")
        Log.d(TAG, "시스템: ${agentType.systemPrompt.take(100)}...")
        Log.d(TAG, "유저: ${userPrompt.take(200)}...")

        // 2. Gemini 호출 (에러 시 1회 자동 재시도)
        return try {
            callGemini(agentType.systemPrompt, userPrompt)
        } catch (e: Exception) {
            val errorMsg = e.message ?: ""
            // Rate Limit(429)은 바로 재시도해도 또 실패 → 즉시 에러 반환
            if (errorMsg.contains("429") ||
                errorMsg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                errorMsg.contains("quota", ignoreCase = true)) {
                Log.w(TAG, "Rate Limit 감지 — 재시도 없이 에러 반환")
                return classifyError(e)
            }
            Log.w(TAG, "1차 시도 실패, 2초 후 재시도합니다: $errorMsg")
            try {
                delay(2000L)  // ★ 2초 대기 후 재시도 (RPM 낭비 방지)
                callGemini(agentType.systemPrompt, userPrompt)
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
            modelName = "gemini-flash-lite-latest",
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

        // AI가 마크다운(```json)을 붙이거나 끝에 여분의 }를 붙이는 오류를 방어하기 위한 정제 로직
        var cleanText = responseText.trim()
        val startIndex = cleanText.indexOf('{')
        if (startIndex != -1) {
            var depth = 0
            var validEndIndex = -1
            for (i in startIndex until cleanText.length) {
                if (cleanText[i] == '{') depth++
                else if (cleanText[i] == '}') {
                    depth--
                    if (depth == 0) {
                        validEndIndex = i
                        break
                    }
                }
            }
            if (validEndIndex != -1) {
                cleanText = cleanText.substring(startIndex, validEndIndex + 1)
            }
        }

        // JSON → WeeklyMealPlan 파싱
        return try {
            val mealPlan = json.decodeFromString<WeeklyMealPlan>(cleanText)

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
        additionalRequest: String,
        allowedRecipes: List<RecipeEntity>,
        mealReuseCount: Int
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
            sb.appendLine("★ 매우 중요: 메인 식재료(소고기, 돼지고기, 해산물, 생선, 특별한 채소 등)는 반드시 위 '보유 재료'에 명시된 것만 사용해야 합니다!")
            sb.appendLine("★ 보유 재료에 없는 메인 식재료를 임의로 지어내서 메뉴에 넣지 마세요 (예: 보유 재료에 소고기가 없는데 스테이크를 추천하면 절대 안 됨).")
            sb.appendLine("★ 단, 집에 흔히 있는 기본 양념류(소금, 간장, 설탕, 고춧가루, 식용유, 참기름 등)와 쌀(밥), 그리고 필수 향신채(마늘, 파, 양파 등)는 보유 재료에 없어도 알아서 사용 가능합니다.")
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
        
        // RAG: 허용된 레시피 (가장 중요)
        if (allowedRecipes.isNotEmpty()) {
            sb.appendLine("【반드시 사용할 레시피 카탈로그】")
            sb.appendLine("아래 제공된 레시피 목록 안에서만 메뉴를 선택하여 식단표를 구성해야 합니다. 이 목록에 없는 메뉴(예: 스테이크, 해물볶음 등)를 임의로 지어내면 절대 안 됩니다.")
            allowedRecipes.forEachIndexed { index, recipe ->
                sb.appendLine("${index + 1}. ${recipe.menuName} (칼로리: ${recipe.calories ?: "미상"}kcal)")
                sb.appendLine("   - 재료: ${recipe.ingredients}")
                sb.appendLine("   - 조리법: ${recipe.instructions}")
            }
        } else {
            sb.appendLine("【참고】사용 가능한 지정 레시피가 없습니다. 위의 보유 재료 한도 내에서 기본 레시피를 생성해주세요.")
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

        // 메인 요리 추천 개수 설정
        when (mealReuseCount) {
            1 -> {
                sb.appendLine("【현실성 반영 규칙 (하루 1개 메인 요리)】")
                sb.appendLine("사용자의 설정에 따라, 하루 종일(당일 모든 끼니) 동일한 메인 요리(국, 찌개, 메인 반찬)를 배치하여 조리 수고를 최소화하세요.")
                sb.appendLine()
            }
            2 -> {
                sb.appendLine("【현실성 반영 규칙 (하루 2개 메인 요리)】")
                sb.appendLine("사용자의 설정에 따라, 하루에 딱 2개의 메인 요리만 추천하여 최소 한 번은 요리를 재사용(재배치)하도록 하세요.")
                sb.appendLine()
            }
            else -> {
                sb.appendLine("【메뉴 다양성 (매 끼니 다름)】")
                sb.appendLine("사용자의 설정에 따라, 당일 내에서도 식사가 겹치지 않도록 매 끼니마다 항상 다른 새로운 요리를 추천해주세요.")
                sb.appendLine()
            }
        }

        // 추가 요청사항
        if (additionalRequest.isNotBlank()) {
            sb.appendLine("【추가 요청사항】$additionalRequest")
            sb.appendLine("★ 최우선 규칙: 위 추가 요청사항을 1순위로 반영해야 합니다.")
            sb.appendLine("★ 만약 추가 요청사항(예: 면 요리 추가)이 기존 '보유 재료'나 '제공된 레시피 카탈로그'의 제약과 충돌하더라도, 융통성을 발휘하여(임의로 레시피를 창작해서라도) 무조건 추가 요청사항을 만족시키는 식단을 짜주세요.")
            sb.appendLine("★ 단, 어떠한 딜레마 상황이 오더라도 절대로 사과문이나 일반 텍스트를 출력하지 마시고, 반드시 아래 지정된 JSON 스키마 형식만을 완벽하게 준수해서 출력하세요.")
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
