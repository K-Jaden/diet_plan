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

    companion object {
        /** 필터 칩에 표시할 카테고리 (식약처 RCP_PAT2 공식 분류) */
        val CATEGORIES = listOf("밥", "국&찌개", "반찬", "일품", "후식", "기타")

        fun factory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val dao = AppDatabase.getDatabase(context.applicationContext).recipeDao()
                @Suppress("UNCHECKED_CAST")
                return RecipeViewModel(RecipeRepository(dao)) as T
            }
        }
    }

    // ── API 프리로드 ──
    private val _preloadState = MutableStateFlow<PreloadUiState>(PreloadUiState.Idle)
    val preloadState: StateFlow<PreloadUiState> = _preloadState.asStateFlow()

    init {
        viewModelScope.launch { if (repository.needsPreload()) runPreload() }
    }

    fun retryPreload() {
        viewModelScope.launch { runPreload() }
    }

    private suspend fun runPreload() {
        _preloadState.value = PreloadUiState.Loading(0, 0)
        repository.preloadFromApi { loaded, total ->
            _preloadState.value = PreloadUiState.Loading(loaded, total)
        }.onSuccess { _preloadState.value = PreloadUiState.Done(it) }
            .onFailure { _preloadState.value = PreloadUiState.Error(it.message ?: "레시피 데이터를 받아오지 못했어요") }
    }

    // ── 검색 + 카테고리 + 즐겨찾기 필터 ──
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** null = 전체 */
    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }

    fun onCategorySelected(category: String?) {
        _selectedCategory.value = if (_selectedCategory.value == category) null else category
    }

    fun toggleFavoritesFilter() { _favoritesOnly.value = !_favoritesOnly.value }

    /** 검색 결과(DB) 위에 카테고리/즐겨찾기 필터를 메모리에서 적용 */
    private val baseRecipes: Flow<List<RecipeEntity>> = _searchQuery
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) repository.observeAllRecipes()
            else repository.searchRecipes(query)
        }

    val recipes: StateFlow<List<RecipeEntity>> =
        combine(baseRecipes, _selectedCategory, _favoritesOnly) { list, category, favOnly ->
            list.filter { r ->
                (category == null || (r.category ?: "기타") == category) &&
                        (!favOnly || r.isFavorite)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── 레시피 상세 + 즐겨찾기 토글 ──
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

    /** 상세 화면 북마크 버튼 → 즐겨찾기 토글 */
    fun toggleFavorite() {
        val current = (_detailState.value as? RecipeDetailUiState.Success)?.recipe ?: return
        viewModelScope.launch {
            val newValue = !current.isFavorite
            repository.setFavorite(current.id, newValue)
            _detailState.value = RecipeDetailUiState.Success(
                current.copy(isFavorite = newValue), true
            )
        }
    }

    fun resetDetailState() { _detailState.value = RecipeDetailUiState.Idle }
}