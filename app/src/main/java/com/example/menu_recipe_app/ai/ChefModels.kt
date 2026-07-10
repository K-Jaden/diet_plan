package com.example.menu_recipe_app.ai

import kotlinx.serialization.Serializable

/**
 * ============================================================
 * 역할 D — 요리사 에이전트 (Chef Agent) Data Models
 * ============================================================
 * 이 파일은 Gemini 요리사 에이전트의 응답을 파싱할 때 사용하는
 * 경량 데이터 클래스를 정의합니다.
 *
 * 출력 결과는 역할 E(식단 검증 및 파싱)로 그대로 전달됩니다.
 * ============================================================
 */

// ─────────────────────────────────────────────────────────────
// ① 요리 후보 1건
// ─────────────────────────────────────────────────────────────
@Serializable
data class DishCandidate(
    /** 요리 이름 (예: "계란볶음밥") */
    val dishName: String,

    /** 이 요리의 핵심 재료 (유저 보유 재료 위주) */
    val keyIngredients: List<String>,

    /** 추가 구매가 필요한 재료 (없으면 빈 리스트) */
    val additionalIngredients: List<String> = emptyList(),

    /** 요리 특징 한 줄 요약 (예: "담백하고 빠른 단품 한식") */
    val description: String,

    /** 예상 조리 시간(분) */
    val estimatedMinutes: Int,

    /** 1인분 예상 칼로리 */
    val estimatedCalories: Int,

    /** 해당 끼니 분류 (BREAKFAST / LUNCH / DINNER / SNACK / ANY) */
    val mealType: String = "ANY"
)

// ─────────────────────────────────────────────────────────────
// ② 요리 후보군 전체 응답 래퍼
// ─────────────────────────────────────────────────────────────
@Serializable
data class ChefAgentResponse(
    /** 요리 후보 리스트 (최소 5개, 최대 10개) */
    val candidates: List<DishCandidate>,

    /** 입력 재료가 부족하거나 이상할 때 모델이 남기는 안내 메시지 (정상이면 null) */
    val warningMessage: String? = null,

    /** 입력 재료가 부족해 추가 구매를 권장할 때 추천 재료 리스트 */
    val suggestedIngredientsToBuy: List<String> = emptyList()
)

// ─────────────────────────────────────────────────────────────
// ③ ChefAgentService 반환 타입 (Sealed)
// ─────────────────────────────────────────────────────────────
sealed class ChefResult {
    data class Success(val response: ChefAgentResponse) : ChefResult()
    data class Error(val message: String, val type: ChefErrorType) : ChefResult()
}

enum class ChefErrorType {
    NETWORK,      // 인터넷 연결 문제
    PARSE,        // AI 응답 JSON 파싱 실패
    API_KEY,      // API 키 오류
    RATE_LIMIT,   // 호출 한도 초과
    UNKNOWN       // 기타
}
