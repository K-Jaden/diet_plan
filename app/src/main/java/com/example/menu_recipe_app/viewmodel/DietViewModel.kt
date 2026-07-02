package com.example.menu_recipe_app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.menu_recipe_app.db.MealDao
import com.example.menu_recipe_app.db.MealEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class DietViewModel(private val mealDao: MealDao) : ViewModel() {

    // 1. '선택된 날짜의 식단'을 들고 있을 변수
    private val _selectedDateMeals = MutableStateFlow<List<MealEntity>>(emptyList())
    val selectedDateMeals: StateFlow<List<MealEntity>> = _selectedDateMeals.asStateFlow()

    // 2. '현재 선택된 날짜' 상태 (기본값: 오늘)
    private val _currentSelectedDate = MutableStateFlow(LocalDate.now())
    val currentSelectedDate: StateFlow<LocalDate> = _currentSelectedDate.asStateFlow()

    init {
        // 앱이 켜지면 기본으로 오늘 날짜 데이터를 불러옴
        fetchMealsForDate(LocalDate.now())
    }

    // 💡 달력에서 날짜를 클릭할 때마다 이 함수를 호출할 겁니다!
    fun fetchMealsForDate(date: LocalDate) {
        _currentSelectedDate.value = date // 선택된 날짜 업데이트
        viewModelScope.launch {
            // DB에서 해당 날짜의 식단만 쏙 뽑아옵니다.
            mealDao.getMealsByDate(date.toString()).collect { meals ->
                _selectedDateMeals.value = meals
            }
        }
    }

    // 💡 생성된 식단을 DB에 저장하고 화면을 새로고침합니다.
    fun saveGeneratedMeals(meals: List<MealEntity>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            mealDao.insertMeals(meals)
            fetchMealsForDate(_currentSelectedDate.value)
        }
    }
}

class DietViewModelFactory(private val mealDao: MealDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DietViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DietViewModel(mealDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
