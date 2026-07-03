package com.example.menu_recipe_app.repository

import android.content.Context
import com.example.menu_recipe_app.db.AppDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 프리페치 진행 상태 (어느 화면에서든 관찰 가능) */
sealed interface PrefetchState {
    data object Idle : PrefetchState
    data class Running(val done: Int, val total: Int, val current: String) : PrefetchState
    data class Finished(val success: Int, val failed: List<String>) : PrefetchState
}

/**
 * [B 담당] 식단표 레시피 프리페처
 *
 * 에이전트가 식단표를 생성/저장하면 이걸 호출:
 *   RecipePrefetcher.prefetch(context, listOf("김치찌개", "계란말이", ...))
 *
 * 동작:
 *  - DB에 이미 있는 메뉴는 스킵 (API 프리로드 데이터 포함)
 *  - 없는 메뉴만 만개의레시피 크롤링 → DB 저장
 *  - 화면 ViewModel이 아닌 자체 코루틴 스코프에서 실행
 *    → 사용자가 화면을 이동해도 백그라운드에서 계속 진행됨
 *  - 크롤링 사이 600ms 간격 (사이트 부담 최소화)
 *
 * [D/E 연동 포인트] 에이전트 응답(JSON 식단표)을 파싱한 뒤
 * 모든 메뉴 이름 리스트를 이 함수에 넘기면 끝.
 * "밥, 된장국, 계란말이" 같은 콤마 묶음 문자열을 넘겨도 알아서 쪼갬.
 */
object RecipePrefetcher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _state = MutableStateFlow<PrefetchState>(PrefetchState.Idle)
    val state: StateFlow<PrefetchState> = _state.asStateFlow()

    fun prefetch(context: Context, menuNames: List<String>) {
        // "밥, 된장국" 같은 묶음도 개별 메뉴로 분리 + 중복 제거
        val names = menuNames
            .flatMap { it.split(",", "/", "+") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (names.isEmpty()) return

        val repository = RecipeRepository(
            AppDatabase.getDatabase(context.applicationContext).recipeDao()
        )

        job?.cancel() // 이전 프리페치가 돌고 있으면 취소하고 새로 시작
        job = scope.launch {
            val failed = mutableListOf<String>()
            var success = 0

            names.forEachIndexed { index, name ->
                _state.value = PrefetchState.Running(index, names.size, name)

                if (repository.hasRecipe(name)) {
                    success++ // 이미 캐시됨 → 크롤링 스킵, 딜레이도 스킵
                    return@forEachIndexed
                }

                repository.getRecipe(name)
                    .onSuccess { success++ }
                    .onFailure { failed.add(name) } // 검색 실패해도 전체는 계속 진행

                delay(600) // 크롤링 간격 (사이트 예의)
            }

            _state.value = PrefetchState.Finished(success, failed)
        }
    }
}