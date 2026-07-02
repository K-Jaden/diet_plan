package com.example.menu_recipe_app.ai

import android.util.Log
import com.example.menu_recipe_app.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.ai.client.generativeai.type.content
import kotlinx.serialization.json.Json
import kotlinx.coroutines.delay

/**
 * ============================================================
 * 역할 D — 요리사 에이전트 (Chef Agent) LLM 서비스
 * ============================================================
 * Task 2: 디바이스에서 직접 Gemini API를 호출하여
 *         요리 후보군(ChefAgentResponse)을 반환하는 싱글톤 서비스.
 *
 * 사용 예시:
 *   val result = ChefAgentService.generateDishCandidates(
 *       ingredients = listOf("계란", "양파", "김치"),
 *       excludedIngredients = listOf("땅콩"),
 *       preferenceNote = "매운 음식은 피해주세요"
 *   )
 *   when (result) {
 *       is ChefResult.Success -> result.response.candidates
 *       is ChefResult.Error   -> showError(result.message)
 *   }
 * ============================================================
 */
object ChefAgentService {

    private const val TAG = "ChefAgentService"

    // ─────────────────────────────────────────────────────────
    // JSON 파서 — 유연한 파싱 허용
    // ─────────────────────────────────────────────────────────
    private val json = Json {
        ignoreUnknownKeys = true   // 미래에 새 필드가 추가돼도 앱이 깨지지 않음
        isLenient = true           // 약간의 JSON 형식 오류 허용
        coerceInputValues = true   // 타입 불일치 시 기본값으로 대체
    }

    // ─────────────────────────────────────────────────────────
    // 메인 공개 함수 — 요리 후보군 생성 요청
    // ─────────────────────────────────────────────────────────
    /**
     * 유저 보유 재료를 Gemini에 전달해 요리 후보군을 받아옵니다.
     * 실패 시 1회 자동 재시도합니다.
     *
     * @param ingredients       보유 재료 리스트
     * @param excludedIngredients 알레르기/기피 재료 (기본값: 빈 리스트)
     * @param preferenceNote    추가 요청사항 문자열 (기본값: 빈 문자열)
     * @return ChefResult.Success 또는 ChefResult.Error
     */
    suspend fun generateDishCandidates(
        ingredients: List<String>,
        excludedIngredients: List<String> = emptyList(),
        preferenceNote: String = ""
    ): ChefResult {

        val userPrompt = ChefAgentPrompts.buildUserPrompt(
            ingredients = ingredients,
            excludedIngredients = excludedIngredients,
            preferenceNote = preferenceNote
        )

        Log.d(TAG, "=== [요리사 에이전트] 프롬프트 전송 ===")
        Log.d(TAG, "보유 재료: $ingredients")
        Log.d(TAG, "제외 재료: $excludedIngredients")

        // 1차 시도
        return try {
            callGeminiChef(userPrompt)
        } catch (e: Exception) {
            val errorMsg = e.message ?: ""
            // Rate Limit(429)은 재시도해도 또 실패 → 즉시 에러 반환
            if (errorMsg.contains("429") ||
                errorMsg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                errorMsg.contains("quota", ignoreCase = true)) {
                Log.w(TAG, "Rate Limit 감지 — 재시도 없이 에러 반환")
                return classifyError(e)
            }
            Log.w(TAG, "1차 시도 실패 — 2초 후 재시도합니다: $errorMsg")
            // 2차 재시도 (2초 대기 후)
            try {
                delay(2000L)  // ★ RPM 낭비 방지
                callGeminiChef(userPrompt)
            } catch (retryEx: Exception) {
                Log.e(TAG, "2차 시도도 실패: ${retryEx.message}")
                classifyError(retryEx)
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Gemini API 호출 + JSON 파싱 (내부 함수)
    // ─────────────────────────────────────────────────────────
    private suspend fun callGeminiChef(userPrompt: String): ChefResult {
        val model = GenerativeModel(
            modelName = "gemini-flash-lite-latest",
            apiKey = BuildConfig.GEMINI_API_KEY,
            generationConfig = generationConfig {
                responseMimeType = "application/json"   // ★ JSON 강제 출력
                temperature = 0.8f                      // 창의적인 요리 제안을 위해 약간 높게 설정
                maxOutputTokens = 2048
            },
            systemInstruction = content {
                text(ChefAgentPrompts.SYSTEM_PROMPT)
            }
        )

        val response = model.generateContent(userPrompt)
        val responseText = response.text
            ?: throw Exception("AI 응답이 비어있습니다.")

        Log.d(TAG, "=== [요리사 에이전트] 응답 수신 (${responseText.length}자) ===")
        Log.d(TAG, responseText.take(600))

        // JSON → ChefAgentResponse 파싱
        return try {
            val chefResponse = json.decodeFromString<ChefAgentResponse>(responseText)

            // 검증: 후보가 최소 1개 이상 있는지 확인
            if (chefResponse.candidates.isEmpty()) {
                Log.w(TAG, "경고: 요리 후보가 0개 반환됨")
                return ChefResult.Error(
                    "요리 후보를 생성하지 못했습니다. 재료를 다시 확인해주세요.",
                    ChefErrorType.PARSE
                )
            }

            Log.d(TAG, "✅ 파싱 성공! ${chefResponse.candidates.size}개 요리 후보 수신")
            chefResponse.warningMessage?.let {
                Log.w(TAG, "⚠️ 에이전트 경고: $it")
            }

            ChefResult.Success(chefResponse)

        } catch (parseEx: Exception) {
            Log.e(TAG, "JSON 파싱 실패: ${parseEx.message}")
            Log.e(TAG, "원본 응답: $responseText")
            ChefResult.Error(
                "요리 데이터를 분석하지 못했습니다. 다시 시도해주세요.",
                ChefErrorType.PARSE
            )
        }
    }

    // ─────────────────────────────────────────────────────────
    // 에러 분류 (내부 함수)
    // ─────────────────────────────────────────────────────────
    private fun classifyError(exception: Exception): ChefResult.Error {
        val message = exception.message ?: "알 수 없는 오류"
        Log.e(TAG, "에러 분류: $message", exception)

        return when {
            message.contains("API key", ignoreCase = true) ||
            message.contains("PERMISSION_DENIED", ignoreCase = true) ||
            message.contains("403") ->
                ChefResult.Error(
                    "API 키가 올바르지 않습니다. 설정을 확인해주세요.",
                    ChefErrorType.API_KEY
                )

            message.contains("429") ||
            message.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
            message.contains("quota", ignoreCase = true) ->
                ChefResult.Error(
                    "오늘의 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.",
                    ChefErrorType.RATE_LIMIT
                )

            message.contains("Unable to resolve host", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true) ||
            message.contains("connect", ignoreCase = true) ||
            exception is java.net.UnknownHostException ||
            exception is java.net.SocketTimeoutException ->
                ChefResult.Error(
                    "인터넷 연결을 확인해주세요.",
                    ChefErrorType.NETWORK
                )

            else ->
                ChefResult.Error(
                    "요리 후보 생성에 실패했습니다. 다시 시도해주세요.\n($message)",
                    ChefErrorType.UNKNOWN
                )
        }
    }
}
