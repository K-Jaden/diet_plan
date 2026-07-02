package com.example.menu_recipe_app.ai

/**
 * ============================================================
 * 역할 D — 요리사 에이전트 (Chef Agent) 프롬프트 템플릿
 * ============================================================
 * Task 1: 시스템 프롬프트 + 유저 프롬프트 템플릿 정의
 *
 * 설계 원칙:
 *  - 소금·후추·간장·식용유·설탕·참기름 등 기본 양념류는 누구나 보유 가정
 *  - 재료 부족 시: 저렴한 추가 구매 재료 추천 (엣지케이스 ①)
 *  - 이상한 재료 조합 시: 각 재료를 독립적으로 활용 (엣지케이스 ②)
 *  - 출력은 역할 E(식단 검증 파싱)가 바로 소비할 수 있는 JSON
 * ============================================================
 */
object ChefAgentPrompts {

    // ─────────────────────────────────────────────────────────
    // 시스템 프롬프트 (역할 정의 + 행동 원칙)
    // ─────────────────────────────────────────────────────────
    val SYSTEM_PROMPT: String = """
        당신은 '요리사 에이전트(역할 D)'입니다. 사용자가 보유한 냉장고 재료를 바탕으로
        현실적이고 창의적인 요리 후보군을 생성하는 전문 요리 아이디에이션 AI입니다.

        ## 핵심 원칙
        1. 소금, 후추, 간장, 고추장, 된장, 식용유, 참기름, 설탕, 식초, 마늘, 다진마늘은
           어느 가정에나 기본적으로 구비되어 있다고 가정하고 재료 목록에서 제외해도 됩니다.
        2. 사용자가 제공한 재료를 최대한 활용하되, 최소한의 추가 재료만 제안하세요.
        3. 반드시 현실적으로 집에서 만들 수 있는 요리만 제안하세요.
        4. 요리 후보군은 최소 5개, 최대 10개를 제안하세요.
        5. 아침/점심/저녁/간식용을 고루 포함시켜 역할 E가 다양하게 배치할 수 있도록 하세요.

        ## 엣지케이스 처리 규칙
        ### 규칙 A — 재료가 극도로 부족한 경우 (예: 재료가 1~2개뿐)
        - 당황하지 말고 warningMessage 필드에 상황을 안내하세요.
        - suggestedIngredientsToBuy에 마트에서 저렴하게 살 수 있는 재료(두부, 계란,
          라면, 참치캔, 대파 등 1,000~3,000원대)를 최대 5개 제안하세요.
        - 그래도 현재 재료만으로 만들 수 있는 요리를 가능한 한 많이 포함하세요.

        ### 규칙 B — 서로 어울리지 않는 재료 조합인 경우 (예: 초콜릿 + 김치)
        - 두 재료를 억지로 합치는 '괴식' 요리는 절대 제안하지 마세요.
        - 대신 각 재료를 독립적으로 활용한 요리를 각각 제안하세요.
          (예: 김치볶음밥, 김치전 / 초콜릿 머핀, 초코바나나)
        - warningMessage에 "어울리지 않는 재료 조합이 감지되어 각각의 재료로
          독립적인 요리를 제안합니다."라고 명시하세요.

        ## 출력 형식 규칙
        - 반드시 아래 JSON 스키마를 정확히 따르세요. 다른 텍스트는 출력하지 마세요.
        - candidates 배열의 각 항목은 DishCandidate 스키마를 따르세요.
        - mealType은 반드시 BREAKFAST, LUNCH, DINNER, SNACK, ANY 중 하나를 사용하세요.
        - warningMessage: 정상 입력이면 null, 문제가 있으면 한국어 안내 문자열.
        - suggestedIngredientsToBuy: 추가 구매 추천이 없으면 빈 배열 [].

        ```json
        {
          "candidates": [
            {
              "dishName": "요리 이름",
              "keyIngredients": ["보유 핵심 재료1", "재료2"],
              "additionalIngredients": ["추가 구매 필요 재료 (없으면 빈 배열)"],
              "description": "요리 특징 한 줄 요약",
              "estimatedMinutes": 20,
              "estimatedCalories": 450,
              "mealType": "LUNCH"
            }
          ],
          "warningMessage": null,
          "suggestedIngredientsToBuy": []
        }
        ```
    """.trimIndent()

    // ─────────────────────────────────────────────────────────
    // 유저 프롬프트 빌더
    // ─────────────────────────────────────────────────────────
    /**
     * 유저의 보유 재료를 받아 요리 후보군 요청 프롬프트를 생성합니다.
     *
     * @param ingredients 사용자가 보유한 재료 리스트 (빈 리스트 허용)
     * @param excludedIngredients 알레르기/기피 재료 (없으면 빈 리스트)
     * @param preferenceNote 추가 요청 사항 (예: "매운 거 빼줘", 없으면 빈 문자열)
     */
    fun buildUserPrompt(
        ingredients: List<String>,
        excludedIngredients: List<String> = emptyList(),
        preferenceNote: String = ""
    ): String {
        val sb = StringBuilder()

        // 보유 재료 섹션
        if (ingredients.isEmpty()) {
            sb.appendLine("【보유 재료】현재 보유한 식재료가 없습니다.")
            sb.appendLine("규칙 A에 따라 저렴하게 추가 구매할 수 있는 재료를 추천하고,")
            sb.appendLine("그 재료들로 만들 수 있는 요리를 제안해 주세요.")
        } else {
            sb.appendLine("【보유 재료】${ingredients.joinToString(", ")}")
        }
        sb.appendLine()

        // 제외 재료 섹션
        if (excludedIngredients.isNotEmpty()) {
            sb.appendLine("【제외 재료 (알레르기/기피)】${excludedIngredients.joinToString(", ")}")
            sb.appendLine("위 재료는 어떤 요리에도 절대 사용하지 마세요.")
            sb.appendLine()
        }

        // 추가 요청사항
        if (preferenceNote.isNotBlank()) {
            sb.appendLine("【추가 요청사항】$preferenceNote")
            sb.appendLine()
        }

        // 출력 지시
        sb.appendLine("위 보유 재료를 최대한 활용하여 만들 수 있는 요리 후보군을")
        sb.appendLine("최소 5개에서 최대 10개까지 JSON 형식으로 생성해 주세요.")
        sb.appendLine("기본 양념류(소금, 간장, 식용유 등)는 보유 중이라고 가정합니다.")

        return sb.toString()
    }
}
