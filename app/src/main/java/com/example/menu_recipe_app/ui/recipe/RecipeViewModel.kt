package com.example.menu_recipe_app.ui.recipe

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.menu_recipe_app.db.AppDatabase
import com.example.menu_recipe_app.db.RecipeEntity
import com.example.menu_recipe_app.repository.RecipeRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface RecipeDetailUiState {
    data object Idle : RecipeDetailUiState
    data object Loading : RecipeDetailUiState
    data class Success(val recipe: RecipeEntity, val fromCache: Boolean) : RecipeDetailUiState
    data class Error(val message: String) : RecipeDetailUiState
}

/** [v2] API 프리로드 진행 상태 */
sealed interface PreloadUiState {
    data object Idle : PreloadUiState
    data class Loading(val loaded: Int, val total: Int) : PreloadUiState
    data class Done(val count: Int) : PreloadUiState
    data class Error(val message: String) : PreloadUiState
}

@OptIn(FlowPreview::class)
class RecipeViewModel(
    private val repository: RecipeRepository
) : ViewModel() {

    // ── [v2] 앱 최초 실행 시 API 프리로드 ──
    private val _preloadState = MutableStateFlow<PreloadUiState>(PreloadUiState.Idle)
    val preloadState: StateFlow<PreloadUiState> = _preloadState.asStateFlow()

    init {
        viewModelScope.launch {
            if (repository.needsPreload()) {
                runPreload()
            }
        }
    }

    fun retryPreload() {
        viewModelScope.launch { runPreload() }
    }

    private suspend fun runPreload() {
        _preloadState.value = PreloadUiState.Loading(0, 0)
        repository.preloadFromApi { loaded, total ->
            _preloadState.value = PreloadUiState.Loading(loaded, total)
        }.onSuccess { count ->
            _preloadState.value = PreloadUiState.Done(count)
        }.onFailure { e ->
            _preloadState.value = PreloadUiState.Error(e.message ?: "레시피 데이터를 받아오지 못했어요")
        }
    }

    // ── 실시간 검색 ──
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    val recipes: StateFlow<List<RecipeEntity>> = _searchQuery
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) repository.observeAllRecipes()
            else repository.searchRecipes(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── 레시피 상세 ──
    private val _detailState = MutableStateFlow<RecipeDetailUiState>(RecipeDetailUiState.Idle)
    val detailState: StateFlow<RecipeDetailUiState> = _detailState.asStateFlow()

    fun loadRecipe(menuName: String) {
        viewModelScope.launch {
            _detailState.value = RecipeDetailUiState.Loading
            repository.getRecipe(menuName)
                .onSuccess { _detailState.value = RecipeDetailUiState.Success(it, false) }
                .onFailure { e ->
                    _detailState.value = RecipeDetailUiState.Error(
                        e.message ?: "레시피를 불러오지 못했어요. 네트워크를 확인해주세요."
                    )
                }
        }
    }

    fun resetDetailState() {
        _detailState.value = RecipeDetailUiState.Idle
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val dao = AppDatabase.getDatabase(context.applicationContext).recipeDao()
                @Suppress("UNCHECKED_CAST")
                return RecipeViewModel(RecipeRepository(dao)) as T
            }
        }
    }
}