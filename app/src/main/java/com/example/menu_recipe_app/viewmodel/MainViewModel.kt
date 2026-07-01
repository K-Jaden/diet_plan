package com.example.menu_recipe_app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.menu_recipe_app.data.DietDao
import com.example.menu_recipe_app.data.DietPlanEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate

class MainViewModel(private val dao: DietDao) : ViewModel() {

    // 사용자가 캘린더에서 선택한 날짜 (초기값: 오늘)
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    // 선택된 날짜에 따라 DB에서 자동으로 식단을 가져오는 반응형 로직
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedDiet: StateFlow<DietPlanEntity?> = _selectedDate
        .flatMapLatest { date -> dao.getDietByDate(date.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun changeDate(newDate: LocalDate) {
        _selectedDate.value = newDate
    }

    fun saveDietPlan(dietPlan: DietPlanEntity) {
        viewModelScope.launch {
            dao.insertDietPlan(dietPlan)
        }
    }
}