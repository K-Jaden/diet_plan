package com.example.menu_recipe_app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.menu_recipe_app.db.MealDao
import com.example.menu_recipe_app.db.MealEntity
import com.example.menu_recipe_app.db.RecipeDao
import com.example.menu_recipe_app.db.RecipeEntity
import com.example.menu_recipe_app.db.UserProfileDao
import com.example.menu_recipe_app.db.UserProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class DietViewModel(
    private val mealDao: MealDao,
    private val userProfileDao: UserProfileDao,
    private val recipeDao: RecipeDao
) : ViewModel() {

    private val _selectedDateMeals = MutableStateFlow<List<MealEntity>>(emptyList())
    val selectedDateMeals: StateFlow<List<MealEntity>> = _selectedDateMeals.asStateFlow()

    private val _currentSelectedDate = MutableStateFlow(LocalDate.now())
    val currentSelectedDate: StateFlow<LocalDate> = _currentSelectedDate.asStateFlow()

    private val _currentMonthMeals = MutableStateFlow<List<MealEntity>>(emptyList())
    val currentMonthMeals: StateFlow<List<MealEntity>> = _currentMonthMeals.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfileEntity?>(null)
    val userProfile: StateFlow<UserProfileEntity?> = _userProfile.asStateFlow()

    private val _selectedRecipe = MutableStateFlow<RecipeEntity?>(null)
    val selectedRecipe: StateFlow<RecipeEntity?> = _selectedRecipe.asStateFlow()

    var tempAllergies: String = ""

    init {
        fetchMealsForDate(LocalDate.now())
        fetchMealsForMonth(YearMonth.now())
        loadUserProfile()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            userProfileDao.getUserProfile().collect { profile ->
                _userProfile.value = profile
            }
        }
    }

    fun saveUserProfile(agent: String, meals: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = UserProfileEntity(
                id = 1,
                selectedAgent = agent,
                allergies = tempAllergies,
                mealsPerDay = meals
            )
            userProfileDao.saveUserProfile(profile)
        }
    }

    fun fetchRecipeByName(name: String) {
        viewModelScope.launch {
            _selectedRecipe.value = recipeDao.getRecipeByName(name)
        }
    }

    fun fetchMealsForDate(date: LocalDate) {
        _currentSelectedDate.value = date
        viewModelScope.launch {
            mealDao.getMealsByDate(date.toString()).collect { meals ->
                _selectedDateMeals.value = meals
            }
        }
    }

    fun fetchMealsForMonth(yearMonth: YearMonth) {
        val startDate = yearMonth.atDay(1).toString()
        val endDate = yearMonth.atEndOfMonth().toString()

        viewModelScope.launch {
            mealDao.getMealsBetweenDates(startDate, endDate).collect { meals ->
                _currentMonthMeals.value = meals
            }
        }
    }

    fun saveGeneratedMeals(meals: List<MealEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            mealDao.insertMeals(meals)
            fetchMealsForDate(_currentSelectedDate.value)
            fetchMealsForMonth(YearMonth.from(_currentSelectedDate.value))
        }
    }
}

class DietViewModelFactory(
    private val mealDao: MealDao,
    private val userProfileDao: UserProfileDao,
    private val recipeDao: RecipeDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DietViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DietViewModel(mealDao, userProfileDao, recipeDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
