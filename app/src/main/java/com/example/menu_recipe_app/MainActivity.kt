package com.example.menu_recipe_app // ★ 본인 패키지명으로 꼭 확인하세요!

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.time.LocalDate
import java.time.YearMonth
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.menu_recipe_app.db.AppDatabase
import com.example.menu_recipe_app.db.RecipeEntity
import com.example.menu_recipe_app.db.MealPlanEntity
import com.example.menu_recipe_app.ai.AgentType
import com.example.menu_recipe_app.ai.GeminiService
import com.example.menu_recipe_app.ai.WeeklyMealPlan
import com.example.menu_recipe_app.ai.DailyMealPlan
import com.example.menu_recipe_app.ai.Meal
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.example.menu_recipe_app.db.RecipeRepository
import com.example.menu_recipe_app.db.MealEntity
import com.example.menu_recipe_app.viewmodel.DietViewModel
import com.example.menu_recipe_app.viewmodel.DietViewModelFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // ==========================================
        // [DB 레시피 시드 및 RAG 준비]
        // NOTE: develop 브랜치는 공공 API로 시드하는 별도 로직을 갖고 있었으나,
        // 레시피 파이프라인은 feat/recipe-caching 머지에서 크롤러 기반으로 교체될
        // 예정이라 여기서는 feat/agents의 RAG 시드 로직만 유지함.
        // ==========================================
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val repository = RecipeRepository(applicationContext, db.recipeDao())
            repository.seedDatabaseIfNeeded() // 앱 최초 실행 시 recipes.json 로드 및 임베딩 생성
            Log.d("RAG_SYSTEM", "초기 데이터 시드 검사 완료")
        }
        // ==========================================
        setContent {
            var isDarkMode by remember { mutableStateOf(false) }
            val colorScheme = if (isDarkMode) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()
            MaterialTheme(colorScheme = colorScheme) {
                // 1. DB와 Dao 가져오기
                val db = AppDatabase.getDatabase(applicationContext)
                val mealDao = db.mealDao()
                val userProfileDao = db.userProfileDao()
                val recipeDao = db.recipeDao() // ★ 추가

                // 2. ViewModel 생성하기
                val dietViewModel: DietViewModel = viewModel(
                    factory = DietViewModelFactory(mealDao, userProfileDao, recipeDao) // ★ 레시피 DAO 넣기
                )

                // 3. Navigation에 ViewModel 넘겨주기
                AppNavigation(dietViewModel = dietViewModel, isDarkMode = isDarkMode, onDarkModeChange = { isDarkMode = it })
            }
        }
    }

// ==========================================
// 1. 네비게이션 라우터 (로그인 및 칼로리 상태 연동)
// ==========================================
@Composable
fun AppNavigation(dietViewModel: DietViewModel, isDarkMode: Boolean = false, onDarkModeChange: (Boolean) -> Unit = {}) {
    val navController = androidx.navigation.compose.rememberNavController()

    // ★ 기존에 잘 만들어두신 전역 상태 관리 그대로 유지!
    var ticketCount by remember { mutableIntStateOf(5) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var userCalories by remember { mutableStateOf<Int?>(null) }

    // ★ AI 생성 식단 공유 상태 (Step2 → Step3 데이터 전달용)
    var generatedMealPlan by remember { mutableStateOf<WeeklyMealPlan?>(null) }
    var selectedAgentName by remember { mutableStateOf("") }

    // ★ Step1에서 입력한 재료/제외재료 공유 상태
    var userIngredients by remember { mutableStateOf(listOf<String>()) }
    var userExcludedIngredients by remember { mutableStateOf(listOf<String>()) }

    androidx.navigation.compose.NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                navController = navController,
                isLoggedIn = isLoggedIn,
                dietViewModel = dietViewModel, // ★ 2. MainScreen으로 ViewModel 전달
                ticketCount = ticketCount,
                onTicketAdd = { added -> ticketCount += added },
                onNavigateToGenerate = { navController.navigate("generate_step1") },
                onNavigateToCalendar = { navController.navigate("calendar") }

            )
        }
        // AppNavigation 내부 수정
        composable("generate_step1") {
            GenerateStep1Screen(
                dietViewModel = dietViewModel, // ★ 추가됨
                onBackClick = { navController.popBackStack() },
                onNextClick = { hasIngredients -> navController.navigate("generate_step2/$hasIngredients") }
            )
        }
        composable(
            route = "generate_step2/{hasIngredients}",
            arguments = listOf(androidx.navigation.navArgument("hasIngredients") { type = androidx.navigation.NavType.BoolType })
        ) { backStackEntry ->
            val hasIngredients = backStackEntry.arguments?.getBoolean("hasIngredients") ?: false
            GenerateStep2Screen(
                dietViewModel = dietViewModel, // ★ 추가됨
                hasIngredients = hasIngredients,
                ticketCount = ticketCount,
                userCalories = userCalories, // ★ 2단계로 칼로리 정보 전달
                userIngredients = userIngredients,
                userExcludedIngredients = userExcludedIngredients,
                onBackClick = { navController.popBackStack() },
                onMealPlanGenerated = { plan, agentName ->
                    generatedMealPlan = plan
                    selectedAgentName = agentName
                    if (ticketCount >= 3) {
                        ticketCount -= 3
                    }
                    navController.navigate("generate_step3")
                }
            )
        }
        composable("generate_step3") {
            GenerateStep3Screen(
                ticketCount = ticketCount,
                mealPlan = generatedMealPlan,
                agentName = selectedAgentName,
                userCalories = userCalories,
                userIngredients = userIngredients,
                userExcludedIngredients = userExcludedIngredients,
                onDeductTicket = { amount -> ticketCount -= amount },
                onMealPlanRegenerated = { newPlan -> generatedMealPlan = newPlan },
                onBackClick = { navController.popBackStack() },
                // ★ 실제 DB 저장은 GenerateStep3Screen 내부(mealPlanDao)에서 수행됨
                onSaveClick = { navController.navigate("generate_step4") },
                onChangeAgentClick = { navController.popBackStack() }
            )
        }
        composable("generate_step4") {
            GenerateStep4Screen(onBackClick = { navController.popBackStack() }, onGoMainClick = { navController.navigate("main") { popUpTo("main") { inclusive = false } } }, onEditClick = { navController.popBackStack() })
        }
        composable("recipe") {
            RecipeScreen(
                navController = navController,
                // ★ 여기서 받은 menuName을 상세 화면 경로로 넘겨줍니다.
                onNavigateToDetail = { menuName -> navController.navigate("recipe_detail/$menuName") }
            )
        }
        composable(
            route = "recipe_detail/{menuName}", // 파라미터 경로 등록
            arguments = listOf(navArgument("menuName") { type = NavType.StringType })
        ) { backStackEntry ->
            val menuName = backStackEntry.arguments?.getString("menuName") ?: "알 수 없는 요리"
            RecipeDetailScreen(
                menuName = menuName,
                viewModel = dietViewModel, // ★ 뷰모델 파라미터 전달!
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(
            route = "meal_detail/{mealId}",
            arguments = listOf(androidx.navigation.navArgument("mealId") { type = androidx.navigation.NavType.IntType })
        ) { backStackEntry ->
            val mealId = backStackEntry.arguments?.getInt("mealId") ?: 0
            MealDetailScreen(
                mealId = mealId,
                onBackClick = { navController.popBackStack() }
            )
        }
        // AppNavigation 내부
        composable("calendar") {
            CalendarScreen(
                navController = navController,
                dietViewModel = dietViewModel // ★ 여기서 넘겨줌!
            )
        }
        composable("my") {
            MyPageScreen(
                navController = navController,
                isLoggedIn = isLoggedIn,
                ticketCount = ticketCount,
                userCalories = userCalories, // ★ 마이페이지로 칼로리 전달
                isDarkMode = isDarkMode,
                onDarkModeChange = onDarkModeChange,
                onLoginClick = { isLoggedIn = true },
                onLogoutClick = {
                    isLoggedIn = false
                    userCalories = null // 로그아웃 시 칼로리 정보 초기화
                },
                onCaloriesCalculated = { calculated -> userCalories = calculated } // ★ 계산 완료 시 상태 업데이트
            )
        }
    }
}

// ==========================================
// 공통 컴포넌트: 스마트한 진행 단계 표시기
// ==========================================
@Composable
fun StepIndicator(currentStep: Int) {
    val primaryGreen = Color(0xFF5A8754)
    val grayColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant
    val textGray = Color.Gray

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        val steps = listOf("재료 선택", "식단 유형", "식단 확인", "완료")
        steps.forEachIndexed { index, title ->
            val stepNumber = index + 1
            val isCompleted = stepNumber < currentStep
            val isActive = stepNumber == currentStep

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(28.dp).background(if (isActive || isCompleted) primaryGreen else grayColor, CircleShape), contentAlignment = Alignment.Center) {
                    if (isCompleted) Icon(Icons.Default.Check, contentDescription = "완료", tint = Color.White, modifier = Modifier.size(18.dp))
                    else Text(stepNumber.toString(), color = if (isActive) Color.White else textGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(title, fontSize = 10.sp, color = if (isActive || isCompleted) primaryGreen else textGray, modifier = Modifier.padding(top = 4.dp))
            }
            if (index < steps.size - 1) {
                HorizontalDivider(modifier = Modifier.width(40.dp).padding(horizontal = 4.dp).offset(y = (-8).dp), color = if (isCompleted) primaryGreen else grayColor, thickness = 1.dp)
            }
        }
    }
}

// ==========================================
// 1. 상단 헤더 (티켓 개수 표시 및 클릭 기능 포함)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopHeaderSection(primaryColor: Color, ticketCount: Int, onTicketClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Eco, contentDescription = "로고", tint = primaryColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "식단관리", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Text(text = "건강한 하루, 균형 잡힌 식단", fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // ★ 클릭이 100% 작동하는 Surface
            Surface(
                onClick = onTicketClick,
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFFF8E1)
            ) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🎫", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "$ticketCount", fontWeight = FontWeight.Bold, color = Color(0xFFF57F17))
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { }) { Icon(Icons.Default.Notifications, contentDescription = "알림") }
        }
    }
}

// ==========================================
// 2. 메인 화면 (티켓 팝업 로직 포함)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: androidx.navigation.NavController,
    isLoggedIn: Boolean,           // ★ 추가된 로그인 상태 파라미터
    dietViewModel: DietViewModel,  // ViewModel 주입됨
    ticketCount: Int,              // ★ 지갑 잔액 받아옴
    onTicketAdd: (Int) -> Unit,    // ★ 충전 기능 받아옴
    onNavigateToGenerate: () -> Unit,
    onNavigateToCalendar: () -> Unit

) {
    val backgroundColor = androidx.compose.material3.MaterialTheme.colorScheme.background
    val primaryGreen = Color(0xFF5A8754)
    val context = androidx.compose.ui.platform.LocalContext.current

    var showTicketSheet by remember { mutableStateOf(false) }

    val selectedDate by dietViewModel.currentSelectedDate.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = backgroundColor,
        bottomBar = { BottomNavigationBar(navController, "main") }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            TopHeaderSection(
                primaryColor = primaryGreen,
                ticketCount = ticketCount,
                onTicketClick = { showTicketSheet = true }
            )
            Spacer(modifier = Modifier.height(24.dp))
            val db = remember { com.example.menu_recipe_app.db.AppDatabase.getDatabase(context) }
            val mealPlanDao = db.mealPlanDao()
            var planDates by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }

            LaunchedEffect(Unit) {
                val datesStr = mealPlanDao.getAllMealPlanDates()
                planDates = datesStr.mapNotNull {
                    try { LocalDate.parse(it) } catch (e: Exception) { null }
                }.toSet()
            }

            WeeklyGenerateCard(primaryGreen, onNavigate = {
                if (!isLoggedIn) {
                    android.widget.Toast.makeText(context, "로그인을 먼저 해주세요.", android.widget.Toast.LENGTH_SHORT).show()
                } else if (planDates.contains(LocalDate.now())) {
                    android.widget.Toast.makeText(context, "이미 오늘의 식단이 생성되어 있습니다. 식단 수정은 캘린더에서 가능합니다.", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    onNavigateToGenerate()
                }
            })

            Spacer(modifier = Modifier.height(24.dp))

            // ★ selectedDate/plannedDates는 CalendarCard가 dietViewModel로 자체 조회
            CalendarCard(
                onNavigateToCalendar = onNavigateToCalendar,
                selectedDate = selectedDate,
                onDateSelected = { clickedDate -> dietViewModel.fetchMealsForDate(clickedDate) },
                dietViewModel = dietViewModel
            )

            Spacer(modifier = Modifier.height(24.dp))
            IngredientsCard()
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // 티켓 바텀 시트 (기존 코드 유지)
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showTicketSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTicketSheet = false },
            sheetState = sheetState,
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
        ) {
            TicketShopSheetContent(
                primaryColor = primaryGreen,
                onBuySuccess = { addedTickets ->
                    onTicketAdd(addedTickets)
                    showTicketSheet = false
                }
            )
        }
    }
}

@Composable
fun WeeklyGenerateCard(primaryColor: Color, onNavigate: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "이번주 식단표", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(24.dp))
            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "이번주 식단을 생성해보세요!", fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onNavigate, colors = ButtonDefaults.buttonColors(containerColor = primaryColor), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text("생성하러 가기", modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

    // ==========================================
// 캘린더 카드 컴포넌트 (DB 연동 버전)
// ==========================================
// 캘린더 카드 컴포넌트 (더보기 버튼 추가, dietViewModel로 월별 계획일 자동 조회)
// ==========================================
@Composable
fun CalendarCard(
    onNavigateToCalendar: () -> Unit,
    selectedDate: LocalDate = LocalDate.now(),
    onDateSelected: (LocalDate) -> Unit = {},
    dietViewModel: DietViewModel
) {
    var currentMonth by remember(selectedDate) { mutableStateOf(YearMonth.from(selectedDate)) }
    val primaryGreen = Color(0xFF5A8754)

        LaunchedEffect(currentMonth) {
            dietViewModel.fetchMealsForMonth(currentMonth)
        }

        val monthMeals by dietViewModel.currentMonthMeals.collectAsState()
        val plannedDates = remember(monthMeals) {
            monthMeals.map { LocalDate.parse(it.date) }.toSet()
        }

        Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = primaryGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("캘린더", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("더보기 >", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { onNavigateToCalendar() }.padding(4.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.ChevronLeft, contentDescription = "이전 달") }
                        Text(text = "${currentMonth.year}년 ${currentMonth.monthValue}월", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                        IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.ChevronRight, contentDescription = "다음 달") }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(primaryGreen))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("식단 계획", fontSize = 10.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF8D6E63)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("기록", fontSize = 10.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(16.dp))

                val daysOfWeek = listOf("일", "월", "화", "수", "목", "금", "토")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    daysOfWeek.forEachIndexed { index, day ->
                        val color = when (index) { 0 -> Color.Red; 6 -> Color(0xFF1976D2); else -> Color.DarkGray }
                        Text(text = day, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value % 7
                val daysInMonth = currentMonth.lengthOfMonth()
                val totalCells = ((firstDayOfWeek + daysInMonth - 1) / 7 + 1) * 7

                LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.heightIn(min = 250.dp, max = 320.dp), userScrollEnabled = false) {
                    items(totalCells) { index ->
                        val dayOffset = index - firstDayOfWeek + 1
                        if (dayOffset in 1..daysInMonth) {
                            val date = currentMonth.atDay(dayOffset)
                            CalendarDayItem(
                                date = date,
                                isSelected = date == selectedDate,
                                hasPlan = plannedDates.contains(date),
                                onClick = { onDateSelected(date) },
                                primaryGreen = primaryGreen
                            )
                        } else {
                            Box(modifier = Modifier.size(48.dp))
                        }
                    }
                }
            }
        }
    }

@Composable
fun CalendarDayItem(date: LocalDate, isSelected: Boolean, hasPlan: Boolean = false, onClick: () -> Unit, primaryGreen: Color) {
    val hasRecord = false // 섭취 완료 표시는 나중을 위해 false 처리 (가짜 날짜%3 로직 제거)

        Column(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(if (isSelected) primaryGreen.copy(alpha = 0.1f) else Color.Transparent).clickable { onClick() }.padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(24.dp).background(if (isSelected) primaryGreen else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                Text(text = date.dayOfMonth.toString(), fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) Color.White else Color.Black)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                if (hasPlan) Icon(Icons.Default.Restaurant, contentDescription = "계획", tint = primaryGreen, modifier = Modifier.size(10.dp))
                if (hasPlan && hasRecord) Spacer(modifier = Modifier.width(2.dp))
                if (hasRecord) Icon(Icons.Default.EmojiFoodBeverage, contentDescription = "기록", tint = Color(0xFF8D6E63), modifier = Modifier.size(10.dp))
                if (!hasPlan && !hasRecord) Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.LightGray))
            }
        }
    }
@Composable
fun IngredientsCard() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color(0xFF5A8754))
            Spacer(modifier = Modifier.width(8.dp))
            Text("이번주 음식 재료", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IngredientItem("채소", "9가지", Color(0xFFE8F5E9))
            IngredientItem("단백질", "6가지", Color(0xFFFFF3E0))
            IngredientItem("곡류", "5가지", Color(0xFFEFEBE9))
            IngredientItem("기타", "7가지", Color(0xFFF3E5F5))
        }
    }
}

@Composable
fun RowScope.IngredientItem(title: String, count: String, bgColor: Color) {
    Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp).clip(RoundedCornerShape(12.dp)).background(bgColor).padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(count, fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BottomNavigationBar(navController: androidx.navigation.NavController, currentRoute: String) {
    val primaryGreen = Color(0xFF5A8754)
    NavigationBar(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "홈") },
            label = { Text("홈") },
            selected = currentRoute == "main",
            onClick = { if (currentRoute != "main") navController.navigate("main") { popUpTo("main") { saveState = true } } },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = primaryGreen, selectedTextColor = primaryGreen, indicatorColor = Color(0xFFE8F5E9))
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "캘린더") },
            label = { Text("캘린더") },
            selected = currentRoute == "calendar",
            onClick = { if (currentRoute != "calendar") navController.navigate("calendar") { popUpTo("main") { saveState = true } } },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = primaryGreen, selectedTextColor = primaryGreen, indicatorColor = Color(0xFFE8F5E9))
        )
        NavigationBarItem(
            icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "레시피") },
            label = { Text("레시피") },
            selected = currentRoute == "recipe",
            onClick = { if (currentRoute != "recipe") navController.navigate("recipe") { popUpTo("main") { saveState = true } } },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = primaryGreen, selectedTextColor = primaryGreen, indicatorColor = Color(0xFFE8F5E9))
        )
        // ★ 수정된 부분: MY 탭에도 이동 경로와 색상 효과를 넣어주었습니다!
        NavigationBarItem(
            icon = { Icon(Icons.Default.Person, contentDescription = "MY") },
            label = { Text("MY") },
            selected = currentRoute == "my",
            onClick = { if (currentRoute != "my") navController.navigate("my") { popUpTo("main") { saveState = true } } },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = primaryGreen, selectedTextColor = primaryGreen, indicatorColor = Color(0xFFE8F5E9))
        )
    }
}

/// ==========================================
// 1단계: 재료 선택 (UI 배치 및 알레르기 분리 완벽 적용)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateStep1Screen(dietViewModel: DietViewModel, onBackClick: () -> Unit, onNextClick: (Boolean) -> Unit) {
    val backgroundColor = androidx.compose.material3.MaterialTheme.colorScheme.background
    val primaryGreen = Color(0xFF5A8754)

    // 상태 변수들
    var selectedOption by remember { mutableStateOf<String?>(null) }
    var textInput by remember { mutableStateOf("") }
    val myIngredients = remember { mutableStateListOf<String>() }
    val recommendedIngredients = listOf("계란", "양파", "대파", "마늘", "두부", "닭가슴살", "돼지고기", "감자")

    // 알레르기/기피 음식 관련 상태 변수들
    var dislikedInput by remember { mutableStateOf("") }
    val dislikedIngredients = remember { mutableStateListOf<String>() }
    val commonDisliked = listOf("오이", "가지", "고수", "버섯", "피망", "견과류", "갑각류", "복숭아", "우유", "밀가루")

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("식단표 생성", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBackIosNew, contentDescription = "뒤로가기") } },
                actions = { Spacer(modifier = Modifier.width(48.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    // ★ 1단계: 기피 재료를 콤마로 연결해서 ViewModel에 임시 저장!
                    dietViewModel.tempAllergies = dislikedIngredients.joinToString(",")
                    onNextClick(selectedOption == "있음")
                },
                enabled = selectedOption == "없음" || (selectedOption == "있음" && myIngredients.isNotEmpty()),
                colors = ButtonDefaults.buttonColors(containerColor = primaryGreen, disabledContainerColor = Color(0xFFE0E0E0)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(20.dp).height(56.dp)
            ) {
                Text("다음", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if(selectedOption == "없음" || (selectedOption == "있음" && myIngredients.isNotEmpty())) Color.White else Color.Gray)
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(16.dp))
            StepIndicator(currentStep = 1)
            Spacer(modifier = Modifier.height(32.dp))

            Box(modifier = Modifier.size(56.dp).background(Color(0xFFE8F5E9), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Eco, contentDescription = null, tint = primaryGreen, modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("현재 사용할 수 있는\n재료가 있나요?", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 34.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text("보유한 재료에 맞춰 맛있고 건강한 식단을 추천해드려요.", fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(32.dp))

            // [재료 있음 / 없음 선택 카드]
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SelectionCard(modifier = Modifier.weight(1f), title = "재료 없음", description = "보유한 재료 없이\n식단을 추천받을래요.", isSelected = selectedOption == "없음", onClick = { selectedOption = "없음" }, primaryColor = primaryGreen)
                SelectionCard(modifier = Modifier.weight(1f), title = "재료 있음", description = "가지고 있는 재료로\n식단을 추천받을래요.", isSelected = selectedOption == "있음", onClick = { selectedOption = "있음" }, primaryColor = primaryGreen)
            }

            // ==========================================
            // [영역 1] 재료 있음을 선택했을 때만 나오는 보유 재료 입력칸
            // ==========================================
            if (selectedOption == "있음") {
                Spacer(modifier = Modifier.height(32.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.Start) {
                    Text("어떤 재료가 있나요?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("재료 직접 입력 (예: 브로콜리)", color = Color.LightGray, fontSize = 14.sp) },
                        trailingIcon = {
                            IconButton(onClick = {
                                if (textInput.isNotBlank() && !myIngredients.contains(textInput.trim())) {
                                    myIngredients.add(textInput.trim())
                                    textInput = ""
                                }
                            }) { Icon(Icons.Default.AddCircle, contentDescription = "추가", tint = primaryGreen) }
                        },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryGreen, unfocusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, focusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface, unfocusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // 선택된 보유 재료
                    if (myIngredients.isNotEmpty()) {
                        Text("선택된 재료", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Column {
                            myIngredients.chunked(4).forEach { rowItems ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                    rowItems.forEach { ingredient -> InputTagChip(name = ingredient, onDelete = { myIngredients.remove(ingredient) }, primaryColor = primaryGreen) }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 추천 보유 재료 태그
                    Text("자주 쓰는 추천 재료", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column {
                        recommendedIngredients.chunked(4).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                rowItems.forEach { ingredient ->
                                    val isAlreadyAdded = myIngredients.contains(ingredient)
                                    Surface(
                                        shape = RoundedCornerShape(20.dp), color = if (isAlreadyAdded) Color(0xFFF0F0F0) else Color.White, border = BorderStroke(1.dp, if (isAlreadyAdded) Color.Transparent else androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.clickable { if (!isAlreadyAdded) myIngredients.add(ingredient) }
                                    ) { Text(text = if (isAlreadyAdded) "$ingredient ✓" else "+ $ingredient", fontSize = 13.sp, color = if (isAlreadyAdded) Color.LightGray else Color.DarkGray, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // [영역 2] 항상 띄워주는 기피 음식 및 알레르기 입력칸 (if문 바깥으로 꺼냄)
            // ==========================================
            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, thickness = 8.dp) // 시각적 분리를 위한 두꺼운 선
            Spacer(modifier = Modifier.height(32.dp))

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.Start) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("못 먹는 음식이나 알레르기가 있나요?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("식단 추천 시 해당 재료는 무조건 제외해 드려요.", fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))

                // 1. 직접 입력창
                OutlinedTextField(
                    value = dislikedInput,
                    onValueChange = { dislikedInput = it },
                    placeholder = { Text("제외할 재료 입력 (예: 오이, 땅콩)", color = Color.LightGray, fontSize = 14.sp) },
                    trailingIcon = {
                        IconButton(onClick = {
                            if (dislikedInput.isNotBlank() && !dislikedIngredients.contains(dislikedInput.trim())) {
                                dislikedIngredients.add(dislikedInput.trim())
                                dislikedInput = ""
                            }
                        }) { Icon(Icons.Default.AddCircle, contentDescription = "추가", tint = Color(0xFFE53935)) }
                    },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFE53935), unfocusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, focusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface, unfocusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2. 자주 제외하는 재료 태그
                Text("자주 제외하는 재료", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Column {
                    commonDisliked.chunked(4).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                            rowItems.forEach { ingredient ->
                                val isAlreadyExcluded = dislikedIngredients.contains(ingredient)
                                val dangerRed = Color(0xFFE53935)

                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isAlreadyExcluded) Color(0xFFFFEBEE) else Color.White,
                                    border = BorderStroke(1.dp, if (isAlreadyExcluded) Color.Transparent else androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.clickable {
                                        if (!isAlreadyExcluded) dislikedIngredients.add(ingredient)
                                    }
                                ) {
                                    Text(
                                        text = if (isAlreadyExcluded) "$ingredient ✓" else "+ $ingredient",
                                        fontSize = 13.sp,
                                        color = if (isAlreadyExcluded) dangerRed else Color.DarkGray,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. 내가 고른 제외 재료 칩 목록 (입력되거나 태그를 눌렀을 때 나타남)
                if (dislikedIngredients.isNotEmpty()) {
                    Text("선택된 제외 재료", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column {
                        dislikedIngredients.chunked(4).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                rowItems.forEach { ingredient ->
                                    InputTagChip(
                                        name = ingredient,
                                        onDelete = { dislikedIngredients.remove(ingredient) },
                                        primaryColor = Color(0xFFE53935) // 빨간색 테마 유지
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun InputTagChip(name: String, onDelete: () -> Unit, primaryColor: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFE8F5E9), border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f))) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(name, fontSize = 13.sp, color = primaryColor, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.Close, contentDescription = "삭제", tint = primaryColor, modifier = Modifier.size(14.dp).clickable { onDelete() })
        }
    }
}

@Composable
fun SelectionCard(modifier: Modifier, title: String, description: String, isSelected: Boolean, onClick: () -> Unit, primaryColor: Color) {
    Card(modifier = modifier.height(220.dp).clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface), border = if (isSelected) BorderStroke(2.dp, primaryColor) else BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isSelected) Icon(Icons.Default.CheckCircle, contentDescription = "선택됨", tint = primaryColor, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(modifier = Modifier.size(80.dp).background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if(title.contains("없음")) Icons.Default.Kitchen else Icons.Default.ShoppingBasket, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if(isSelected) primaryColor else Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Text(description, fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

// ==========================================
// 2단계: 에이전트 분석 브리핑 및 선택 (맞춤 칼로리 반영)
// ==========================================
// ==========================================
// 2단계: 에이전트 분석 브리핑 및 선택 (맞춤 칼로리 반영)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateStep2Screen(
    dietViewModel: DietViewModel, // ★ 누락되었던 뷰모델 파라미터 추가!
    hasIngredients: Boolean,
    ticketCount: Int,
    userCalories: Int?,
    userIngredients: List<String>,
    userExcludedIngredients: List<String>,
    onBackClick: () -> Unit,
    onMealPlanGenerated: (WeeklyMealPlan, String) -> Unit
) {
    val backgroundColor = androidx.compose.material3.MaterialTheme.colorScheme.background
    val primaryGreen = Color(0xFF5A8754)
    val ticketCost = 3

    var isAnalyzing by remember { mutableStateOf(hasIngredients) }
    var selectedAgent by remember { mutableStateOf<String?>(null) }
    var familyMemberCount by remember { mutableIntStateOf(3) }
    var mealsPerDay by remember { mutableIntStateOf(3) }
    var includeSnack by remember { mutableStateOf(false) }
    var mealStyle by remember { mutableStateOf("골고루") }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // ★ API 호출 상태 관리
    var isGenerating by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val dummyBriefing = remember {
        ChefBriefing(
            ingredientsToAdd = listOf("대파", "양파"),
            ingredientsToRemove = listOf("설탕", "나트륨"),
            possibleDishes = listOf("두부계란국", "두부부침", "뚝배기계란찜")
        )
    }

    LaunchedEffect(hasIngredients) {
        kotlinx.coroutines.delay(2500)
        isAnalyzing = false
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("식단표 생성", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBackIosNew, contentDescription = "뒤로가기") } },
                actions = { Spacer(modifier = Modifier.width(48.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        },
        bottomBar = {
            // ★ 중첩되어 꼬여있던 bottomBar 코드를 깔끔하게 1개로 정리했습니다.
            if (!isAnalyzing) {
                Column(modifier = Modifier.padding(20.dp)) {
                    val canAfford = ticketCount >= ticketCost
                    Button(
                        onClick = {
                            if (canAfford) {
                                if (selectedAgent != null) {
                                    // ★ 선택된 영양사와 끼니 수를 DB에 영구 저장 (develop)
                                    dietViewModel.saveUserProfile(
                                        agent = selectedAgent ?: "실속관리",
                                        meals = mealsPerDay
                                    )

                                    isGenerating = true
                                    coroutineScope.launch {
                                        // 1. 에이전트 설정
                                        val agentType = when (selectedAgent) {
                                            "실속관리" -> AgentType.Budget(10000)
                                            "패밀리케어" -> AgentType.Family(familyMemberCount)
                                            "혈당케어" -> AgentType.BloodSugar
                                            else -> AgentType.Budget()
                                        }

                                        // 2. RAG 검색 (DB에서 보유 재료로 레시피 검색)
                                        val db = AppDatabase.getDatabase(context)
                                        val repository = RecipeRepository(context, db.recipeDao())
                                        val allowedRecipes = repository.searchRecipesByIngredients(userIngredients, limit = 10)

                                        // 3. API 호출
                                        val service = GeminiService()
                                        val result = service.generateMealPlan(
                                            agentType = agentType,
                                            userCalories = userCalories,
                                            ingredients = userIngredients,
                                            excludedIngredients = userExcludedIngredients,
                                            mealsPerDay = mealsPerDay,
                                            includeSnack = includeSnack,
                                            mealStyle = mealStyle,
                                            allowedRecipes = allowedRecipes
                                        )

                                        isGenerating = false
                                        // 4. 결과 처리
                                        when (result) {
                                            is GeminiService.MealPlanResult.Success -> {
                                                onMealPlanGenerated(result.plan, selectedAgent!!)
                                            }
                                            is GeminiService.MealPlanResult.Error -> {
                                                android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }
                            } else {
                                android.widget.Toast.makeText(context, "티켓이 부족합니다. 메인 화면에서 충전해주세요.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = selectedAgent != null && !isGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = primaryGreen, disabledContainerColor = Color(0xFFD6D6D6)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("식단 생성 중...", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        } else if (canAfford) {
                            Text("🎫 ${ticketCost}개를 사용하여 식단 만들기", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if(selectedAgent != null) Color.White else Color.Gray)
                        } else {
                            Text("티켓이 부족해요 (현재: ${ticketCount}개)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.Start) {
            Spacer(modifier = Modifier.height(16.dp))
            StepIndicator(currentStep = 2)
            Spacer(modifier = Modifier.height(40.dp))

            if (isAnalyzing) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = primaryGreen)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("👨‍🍳 요리사 에이전트가\n맞춤 식단을 분석 중입니다...", fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 30.sp)
                }
            } else {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    if (userCalories != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF4F9F4),
                            border = BorderStroke(1.dp, primaryGreen.copy(alpha=0.3f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("🩺", fontSize = 28.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("영양사 Agent 브리핑", fontSize = 12.sp, color = primaryGreen, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("입력된 신체 정보 기준 권장 섭취량은\n하루 ${userCalories} kcal 입니다.", fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("이 칼로리 기준에 맞춰 식단을 짤게요!", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    if (hasIngredients) {
                        ChefBriefingCard(dummyBriefing, primaryGreen)
                        Spacer(modifier = Modifier.height(32.dp))
                        HorizontalDivider(color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, thickness = 8.dp)
                        Spacer(modifier = Modifier.height(32.dp))

                        Text("맞춤 식단 기본 설정", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(20.dp))

                        Text("하루에 몇 끼를 드시나요?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SelectableOptionChip(modifier = Modifier.weight(1f), text = "2끼 (점심/저녁)", isSelected = mealsPerDay == 2, onClick = { mealsPerDay = 2 }, primaryColor = primaryGreen)
                            SelectableOptionChip(modifier = Modifier.weight(1f), text = "3끼 (아침/점심/저녁)", isSelected = mealsPerDay == 3, onClick = { mealsPerDay = 3 }, primaryColor = primaryGreen)
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth().clickable { includeSnack = !includeSnack }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = if (includeSnack) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, contentDescription = null, tint = if (includeSnack) primaryGreen else Color.LightGray)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("식단에 가벼운 간식 포함하기", fontSize = 14.sp, color = if (includeSnack) Color.Black else Color.Gray)
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        Text("식단 구성 스타일", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectableOptionChip(modifier = Modifier.weight(1f), text = "밥+국 필수", isSelected = mealStyle == "밥+국 필수", onClick = { mealStyle = "밥+국 필수" }, primaryColor = primaryGreen)
                            SelectableOptionChip(modifier = Modifier.weight(1f), text = "일품/간편식", isSelected = mealStyle == "일품/간편식", onClick = { mealStyle = "일품/간편식" }, primaryColor = primaryGreen)
                            SelectableOptionChip(modifier = Modifier.weight(1f), text = "골고루 섞어서", isSelected = mealStyle == "골고루", onClick = { mealStyle = "골고루" }, primaryColor = primaryGreen)
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                        HorizontalDivider(color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(32.dp))
                        Text("이 후보들로 어떤 식단표를 짤까요?", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("👨‍🍳 보관 중인 재료가 없으시군요!", fontSize = 16.sp, color = primaryGreen, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("다양한 식재료를 활용해 어떤 식단표를 짤까요?", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                    Text("전문 영양사가 당신의 목표에 맞는 식단을 설계해드려요.", fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(24.dp))

                    AgentCard("자취생 영양사", "가성비와 식재료 낭비 방지에 초점을 맞춘 1인 가구 추천 식단", Icons.Default.Eco, "절약형 식단을 원하는 분", true, selectedAgent == "실속관리", { selectedAgent = "실속관리" }, primaryGreen)
                    Spacer(modifier = Modifier.height(16.dp))
                    AgentCard("가족 영양사", "3~4인 가구가 선택하기 좋은 식단 추천", Icons.Default.FamilyRestroom, "주부 및 다인 가구", false, selectedAgent == "패밀리케어", { selectedAgent = "패밀리케어" }, primaryGreen)
                    androidx.compose.animation.AnimatedVisibility(visible = selectedAgent == "패밀리케어") {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp).background(Color(0xFFF4F9F4), RoundedCornerShape(12.dp)).border(1.dp, primaryGreen.copy(alpha = 0.2f), RoundedCornerShape(12.dp)).padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("👨‍👩‍👧‍👦 식사 인원을 알려주세요", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("인원에 맞춰 양과 레시피를 조절할게요", fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { if (familyMemberCount > 1) familyMemberCount-- }, modifier = Modifier.size(36.dp).background(Color.White, CircleShape).border(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, CircleShape)) {
                                    Icon(Icons.Default.Remove, contentDescription = "빼기", tint = if (familyMemberCount > 1) Color.Black else Color.LightGray)
                                }
                                Text("$familyMemberCount 명", modifier = Modifier.padding(horizontal = 24.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryGreen)
                                IconButton(onClick = { if (familyMemberCount < 10) familyMemberCount++ }, modifier = Modifier.size(36.dp).background(Color.White, CircleShape).border(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, CircleShape)) { Icon(Icons.Default.Add, contentDescription = "더하기") }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    AgentCard("혈당 케어 영양사", "혈당 스파이크를 방지하는 저당, 저탄수화물 위주의 건강 식단", Icons.Default.MonitorHeart, "당뇨 및 건강 관리가 필요한 분", false, selectedAgent == "혈당케어", { selectedAgent = "혈당케어" }, primaryGreen)
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

// ==========================================
// ★ 새로 추가할 데이터 클래스 및 UI 컴포넌트
// ==========================================
data class ChefBriefing(
    val ingredientsToAdd: List<String>,
    val ingredientsToRemove: List<String>,
    val possibleDishes: List<String>
)

@Composable
fun ChefBriefingCard(briefing: ChefBriefing, primaryColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FBF9)),
        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("👨‍🍳", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("요리사 Agent 분석 결과", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = primaryColor)
            }
            Spacer(modifier = Modifier.height(20.dp))

            // 추가/제외 재료 칩
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                briefing.ingredientsToAdd.forEach { ingredient ->
                    HintChip(text = "+ $ingredient", bgColor = Color(0xFFE8F5E9), textColor = primaryColor)
                }
                briefing.ingredientsToRemove.forEach { ingredient ->
                    HintChip(text = "- $ingredient", bgColor = Color(0xFFFFEBEE), textColor = Color(0xFFD32F2F))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))

            // 추천 요리 리스트
            Text("추천 요리 후보군", fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = briefing.possibleDishes.joinToString(", "),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
fun HintChip(text: String, bgColor: Color, textColor: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun AgentCard(title: String, description: String, targetIcon: androidx.compose.ui.graphics.vector.ImageVector, targetText: String, isRecommended: Boolean, isSelected: Boolean, onClick: () -> Unit, primaryColor: Color) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface), border = if (isSelected) BorderStroke(1.5.dp, primaryColor) else BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant), elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(Color(0xFFF0F0F0)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(40.dp)) }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (isRecommended) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.background(Color(0xFFE8F5E9), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("추천", color = primaryColor, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(description, fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(targetIcon, contentDescription = null, tint = primaryColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(targetText, fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            RadioButton(selected = isSelected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = primaryColor, unselectedColor = Color.LightGray))
        }
    }
}

// ==========================================
// 3단계: 식단 확인 (재생성 팝업 및 과금 로직 추가)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GenerateStep3Screen(
    ticketCount: Int,
    mealPlan: WeeklyMealPlan?,
    agentName: String,
    userCalories: Int?,
    userIngredients: List<String>,
    userExcludedIngredients: List<String>,
    onDeductTicket: (Int) -> Unit,
    onMealPlanRegenerated: (WeeklyMealPlan?) -> Unit,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit, // ★ 실제 DB 저장(mealPlanDao)은 이 컴포저블 내부에서 수행하고 호출부는 화면 전환만 담당
    onChangeAgentClick: () -> Unit
) {
    val backgroundColor = androidx.compose.material3.MaterialTheme.colorScheme.background
    val primaryGreen = Color(0xFF5A8754)
    val context = androidx.compose.ui.platform.LocalContext.current

    val savedDaysState = remember { mutableStateListOf(*Array(7) { true }) }
    val days = remember {
        val baseDays = listOf("월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일")
        val todayIndex = java.time.LocalDate.now().dayOfWeek.value - 1
        baseDays.subList(todayIndex, 7) + baseDays.subList(0, todayIndex)
    }

    // ★ 재생성 관련 상태 변수들
    var regenCount by remember { mutableIntStateOf(0) }
    var showRegenDialog by remember { mutableStateOf(false) }
    var additionalRequest by remember { mutableStateOf("") }
    var isRegenerating by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // ★ 요금 계산 로직 (0, 0, 1, 2, 3...)
    val currentRegenCost = if (regenCount < 2) 0 else regenCount - 1
    val remainingFreeCount = if (regenCount < 2) 2 - regenCount else 0

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("생성된 식단 확인", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBackIosNew, contentDescription = "뒤로가기") } },
                actions = { Spacer(modifier = Modifier.width(48.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        },
        bottomBar = {
            if (!isRegenerating) {
                Column(modifier = Modifier.background(backgroundColor).padding(horizontal = 20.dp).padding(bottom = 24.dp, top = 8.dp)) {
                    Button(
                        onClick = {
                            if (mealPlan != null) {
                                coroutineScope.launch {
                                    val db = AppDatabase.getDatabase(context)
                                    val dao = db.mealPlanDao()
                                    val entities = mutableListOf<MealPlanEntity>()
                                    val formatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
                                    val baseDate = java.time.LocalDate.now()

                                    for (i in 0 until 7) {
                                        if (savedDaysState[i] && i < mealPlan.days.size) {
                                            val dailyPlan = mealPlan.days[i]
                                            val dateStr = baseDate.plusDays(i.toLong()).format(formatter)

                                            val createEntity = { mealType: String, meal: Meal ->
                                                MealPlanEntity(
                                                    date = dateStr, dayName = dailyPlan.dayName,
                                                    agentType = agentName, mealType = mealType,
                                                    menuName = meal.menuName, ingredients = Json.encodeToString(meal.ingredients),
                                                    calories = meal.calories, recipe = meal.recipe,
                                                    totalDayCalories = dailyPlan.totalCalories
                                                )
                                            }
                                            if (dailyPlan.breakfast.menuName != "없음") entities.add(createEntity("breakfast", dailyPlan.breakfast))
                                            if (dailyPlan.lunch.menuName != "없음") entities.add(createEntity("lunch", dailyPlan.lunch))
                                            if (dailyPlan.dinner.menuName != "없음") entities.add(createEntity("dinner", dailyPlan.dinner))
                                            if (dailyPlan.snack != null && dailyPlan.snack.menuName != "없음") entities.add(createEntity("snack", dailyPlan.snack))
                                        }
                                    }
                                    val startDateStr = baseDate.format(formatter)
                                    val endDateStr = baseDate.plusDays(6).format(formatter)
                                    dao.deleteMealsByDateRange(startDateStr, endDateStr) // ★ 중복 적재(하루 5끼 등) 방지를 위해 기존 7일치 삭제
                                    dao.insertAll(entities)
                                    onSaveClick()
                                }
                            }
                        },
                        enabled = savedDaysState.any { it } && mealPlan != null,
                        colors = ButtonDefaults.buttonColors(containerColor = primaryGreen, disabledContainerColor = Color(0xFFD6D6D6)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = if (savedDaysState.any { it }) Color.White else Color.Gray)
                        Spacer(modifier = Modifier.width(8.dp))
                        val selectedCount = savedDaysState.count { it }
                        Text(
                            text = if (selectedCount == 7) "이 식단으로 저장" else "${selectedCount}개 요일 식단만 저장",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (savedDaysState.any { it }) Color.White else Color.Gray
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // ★ 다시 생성하기 버튼 (비용을 버튼에 띄워줍니다)
                        OutlinedButton(
                            onClick = { showRegenDialog = true },
                            border = BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface, contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            val btnText = if (remainingFreeCount > 0) "다시 생성 (무료 ${remainingFreeCount}번)" else "다시 생성 (🎫 $currentRegenCost)"
                            Text(btnText, fontSize = 13.sp)
                        }
                        OutlinedButton(onClick = onChangeAgentClick, border = BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface, contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(1f).height(50.dp)) {
                            Icon(Icons.Default.PersonOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("다른 영양사 선택", fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("선택하여 체크된 요일만 내 식단표 및 DB에 기록됩니다.", fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(16.dp))
            StepIndicator(currentStep = 3)
            Spacer(modifier = Modifier.height(24.dp))

            if (isRegenerating) {
                // ★ 재생성 로딩 뷰
                Column(modifier = Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = primaryGreen)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("요청사항을 반영하여\n식단을 다시 짜고 있습니다...", fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 30.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("조금만 기다려주세요!", fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // 기존 캘린더 및 식단 뷰
                AgentSummaryCard(agentName, primaryGreen)
                Spacer(modifier = Modifier.height(32.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("이번 주 식단표", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
                val pagerState = rememberPagerState(pageCount = { 7 })
                HorizontalPager(state = pagerState, contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 16.dp) { page ->
                    val displayDayName = if (page == 0) "${days[page]} (오늘)" else days[page]
                    val dailyPlan = mealPlan?.days?.getOrNull(page)
                    
                    DailyDietCard(
                        dayName = dailyPlan?.dayName ?: displayDayName,
                        dailyPlan = dailyPlan,
                        isCurrentPage = pagerState.currentPage == page,
                        primaryColor = primaryGreen,
                        isSavedChecked = savedDaysState[page],
                        onCheckedChange = { isChecked -> savedDaysState[page] = isChecked }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    repeat(7) { iteration ->
                        val color = if (pagerState.currentPage == iteration) primaryGreen else Color(0xFFE0E0E0)
                        Box(modifier = Modifier.padding(4.dp).size(8.dp).clip(CircleShape).background(color))
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // ==========================================
    // ★ 추가 요청사항 다이얼로그 (팝업창)
    // ==========================================
    if (showRegenDialog) {
        AlertDialog(
            onDismissRequest = { showRegenDialog = false },
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
            title = { Text("식단 다시 생성하기", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column {
                    Text("마음에 들지 않는 부분이 있다면 알려주세요!\nAI 에이전트가 적극 반영하여 다시 짜드립니다.", fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = additionalRequest,
                        onValueChange = { additionalRequest = it },
                        placeholder = { Text("예: 점심에는 면 요리를 넣어줘, 매운 건 빼줘", fontSize = 13.sp, color = Color.LightGray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryGreen, unfocusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(color = Color(0xFFF9F9F9), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = if (currentRegenCost == 0) primaryGreen else Color(0xFFE53935), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            if (currentRegenCost == 0) {
                                Text("현재 무료 재생성 기회가 ${remainingFreeCount}번 남았습니다.", color = primaryGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("이번 재생성에는 🎫 티켓 ${currentRegenCost}개가 소모됩니다.", color = Color(0xFFE53935), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (ticketCount >= currentRegenCost) {
                            onDeductTicket(currentRegenCost)
                            regenCount++
                            showRegenDialog = false
                            val requestToPass = additionalRequest
                            additionalRequest = ""
                            isRegenerating = true // 로딩 화면 트리거
                            
                            coroutineScope.launch {
                                val type = when (agentName) {
                                    "실속관리" -> AgentType.Budget(10000)
                                    "패밀리케어" -> AgentType.Family(3)
                                    "혈당케어" -> AgentType.BloodSugar
                                    else -> AgentType.Budget()
                                }
                                
                                // 2. RAG 검색
                                val db = AppDatabase.getDatabase(context)
                                val repository = RecipeRepository(context, db.recipeDao())
                                val allowedRecipes = repository.searchRecipesByIngredients(userIngredients, limit = 10)

                                // 3. API 호출
                                val result = GeminiService().generateMealPlan(
                                    agentType = type,
                                    userCalories = userCalories,
                                    ingredients = userIngredients,
                                    excludedIngredients = userExcludedIngredients,
                                    mealsPerDay = 3,
                                    includeSnack = true,
                                    mealStyle = "골고루",
                                    additionalRequest = requestToPass,
                                    allowedRecipes = allowedRecipes
                                )
                                isRegenerating = false
                                when (result) {
                                    is GeminiService.MealPlanResult.Success -> onMealPlanRegenerated(result.plan)
                                    is GeminiService.MealPlanResult.Error -> android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            android.widget.Toast.makeText(context, "티켓이 부족합니다. 메인 화면에서 충전해주세요.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryGreen)
                ) {
                    Text("생성하기", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenDialog = false }) { Text("취소", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }
}

@Composable
fun AgentSummaryCard(agentName: String, primaryColor: Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFF0F0F0)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(32.dp)) }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("선택된 Agent: ", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(agentName.ifEmpty { "실속 관리 Agent" }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("이번 주 맞춤 식단이 생성되었어요", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("균형 잡힌 영양으로 건강한 식습관을 도와드릴게요!", fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Eco, contentDescription = null, tint = primaryColor, modifier = Modifier.size(24.dp).padding(end = 8.dp))
        }
    }
}

// ==========================================
// 일별 식단 카드 컴포넌트 (체크박스 기능 내장)
// ==========================================
@Composable
fun DailyDietCard(
    dayName: String,
    dailyPlan: DailyMealPlan?,
    isCurrentPage: Boolean,
    primaryColor: Color,
    isSavedChecked: Boolean,           // ★ 추가: 저장 활성화 여부 상태값
    onCheckedChange: (Boolean) -> Unit // ★ 추가: 체크 상태 변경 콜백 함수
) {
    val borderColor = if (isCurrentPage) primaryColor else androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (isCurrentPage) 1.5.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 상단 영역을 가로 배치용 Box로 묶어 요일 이름은 중앙, 체크박스는 우측 끝에 정렬합니다.
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.background(Color(0xFFF9F9F9), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(dayName, fontWeight = FontWeight.Bold, color = if (isCurrentPage) primaryColor else Color.Gray, fontSize = 14.sp)
                }

                // ★ 우측 상단 저장 체크박스 레이아웃
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable { onCheckedChange(!isSavedChecked) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSavedChecked) "저장함" else "제외됨",
                        fontSize = 11.sp,
                        color = if (isSavedChecked) primaryColor else Color.LightGray,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Checkbox(
                        checked = isSavedChecked,
                        onCheckedChange = onCheckedChange,
                        colors = CheckboxDefaults.colors(checkedColor = primaryColor, uncheckedColor = Color.LightGray),
                        modifier = Modifier.scale(0.85f) // 체크박스 크기가 너무 크지 않게 0.85배로 줄임
                    )
                }
            }

            // ★ 체크 해제 시 컴포넌트를 흐리게 보이도록 반투명도 설정 (해제 시 투명도 30%)
            val contentAlpha = if (isSavedChecked) 1f else 0.3f

            Column(modifier = Modifier.graphicsLayer(alpha = contentAlpha)) {
                Spacer(modifier = Modifier.height(24.dp))
                if (dailyPlan != null) {
                    if (dailyPlan.breakfast.menuName != "없음") {
                        MealRow("아침", primaryColor, "${dailyPlan.breakfast.menuName}\n(${dailyPlan.breakfast.calories}kcal)")
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    if (dailyPlan.lunch.menuName != "없음") {
                        MealRow("점심", primaryColor, "${dailyPlan.lunch.menuName}\n(${dailyPlan.lunch.calories}kcal)")
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    if (dailyPlan.dinner.menuName != "없음") {
                        MealRow("저녁", primaryColor, "${dailyPlan.dinner.menuName}\n(${dailyPlan.dinner.calories}kcal)")
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    if (dailyPlan.snack != null && dailyPlan.snack.menuName != "없음") {
                        MealRow("간식", primaryColor, "${dailyPlan.snack.menuName}\n(${dailyPlan.snack.calories}kcal)")
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Eco, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("총 열량 ${dailyPlan.totalCalories} kcal", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                        }
                    }
                } else {
                    MealRow("아침", primaryColor, "밥, 된장국, 계란말이,\n시금치나물")
                    Spacer(modifier = Modifier.height(16.dp))
                    MealRow("점심", primaryColor, "밥, 된장국, 닭가슴살볶음,\n나물무침")
                    Spacer(modifier = Modifier.height(16.dp))
                    MealRow("저녁", primaryColor, "밥, 된장국, 두부조림,\n브로콜리무침")
                    Spacer(modifier = Modifier.height(16.dp))
                    MealRow("간식", primaryColor, "사과, 견과류")
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(modifier = Modifier.fillMaxWidth().background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Eco, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("총 열량 1,780 kcal", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MealRow(mealType: String, primaryColor: Color, menu: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Default.Restaurant, contentDescription = null, tint = Color.LightGray) }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(mealType, color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(menu, fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, lineHeight = 20.sp)
        }
    }
}

// ==========================================
// 4단계: 완료
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateStep4Screen(onBackClick: () -> Unit, onGoMainClick: () -> Unit, onEditClick: () -> Unit) {
    val backgroundColor = androidx.compose.material3.MaterialTheme.colorScheme.background
    val primaryGreen = Color(0xFF5A8754)

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("생성된 식단 확인", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBackIosNew, contentDescription = "뒤로가기") } },
                actions = { Spacer(modifier = Modifier.width(48.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp).padding(bottom = 8.dp)) {
                Button(onClick = onGoMainClick, colors = ButtonDefaults.buttonColors(containerColor = primaryGreen), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Default.Home, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("메인으로 가기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onEditClick, border = BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface, contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("식단 수정하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(16.dp))
            StepIndicator(currentStep = 4)
            Spacer(modifier = Modifier.height(48.dp))
            Box(contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(140.dp).clip(CircleShape).background(Color(0xFFF1F8F1)))
                Box(modifier = Modifier.size(110.dp).clip(CircleShape).background(Color(0xFFE3F2E3)))
                Box(modifier = Modifier.size(80.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, contentDescription = "완료", tint = primaryGreen, modifier = Modifier.size(48.dp))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = buildAnnotatedString {
                    append("식단표 ")
                    withStyle(style = SpanStyle(color = primaryGreen)) { append("저장 완료") }
                },
                fontSize = 28.sp, fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("이번주 식단이 내 식단표에 반영되었어요", fontSize = 15.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(40.dp))
            AgentFinalSummaryCard(primaryGreen)
            Spacer(modifier = Modifier.height(40.dp))
            Text("꾸준한 실천이 건강한 변화를 만듭니다.", fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(4.dp))
            Text("다음주에도 균형 잡힌 식단으로 함께해요!", fontSize = 13.sp, color = primaryGreen, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun AgentFinalSummaryCard(primaryColor: Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant), elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEAEAEA)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.background(Color(0xFFE8F5E9), RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Eco, contentDescription = null, tint = primaryColor, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("실속 관리 Agent 추천", color = primaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("이번주 월요일부터 금요일까지 적용", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Restaurant, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("5일 식단 저장됨", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

// ==========================================
// ★ 새로운 화면: 레시피 메인 탭 (실제 DB 연동 버전)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(navController: androidx.navigation.NavController, onNavigateToDetail: (String) -> Unit) {
    val backgroundColor = androidx.compose.material3.MaterialTheme.colorScheme.background
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var searchQuery by remember { mutableStateOf("") }
    var recipeList by remember { mutableStateOf<List<RecipeEntity>>(emptyList()) }

    fun performSearch(query: String) {
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val results = if (query.isBlank()) {
                db.recipeDao().getAllRecipes() // 검색어가 비어있으면 전체 레시피 보여주기
            } else {
                db.recipeDao().searchRecipes(query)
            }
            withContext(Dispatchers.Main) {
                recipeList = results
            }
        }
    }

    // 화면 진입 시 처음에 전체 리스트를 한 번 불러옵니다.
    LaunchedEffect(Unit) {
        performSearch("")
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("레시피", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        },
        bottomBar = { BottomNavigationBar(navController, "recipe") }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { newText ->
                    searchQuery = newText
                    performSearch(newText)
                },
                placeholder = { Text("어떤 요리를 만들어볼까요?", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "검색",
                        tint = Color.Gray
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                    focusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant
                ),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (searchQuery.isNotBlank() && recipeList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("'$searchQuery'에 대한 검색 결과가 없습니다.", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(recipeList.size) { index ->
                        val recipe = recipeList[index]
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onNavigateToDetail(recipe.menuName) }
                        ) {
                            AsyncImage(
                                model = recipe.imageUrl,
                                contentDescription = recipe.menuName,
                                modifier = Modifier.size(90.dp).clip(CircleShape)
                                    .background(Color(0xFFF0F0F0)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                recipe.menuName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// ★ 새로운 화면: 레시피 상세 화면
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(menuName: String, viewModel: DietViewModel, onBackClick: () -> Unit) {
    val backgroundColor = androidx.compose.material3.MaterialTheme.colorScheme.background
    val primaryGreen = Color(0xFF5A8754)

    // ★ 화면이 열릴 때 뷰모델에게 레시피 정보를 찾아오라고 명령
    LaunchedEffect(menuName) {
        viewModel.fetchRecipeByName(menuName)
    }

    // ★ DB에서 찾은 레시피 데이터를 관찰
    val recipe by viewModel.selectedRecipe.collectAsState()

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBackIosNew, contentDescription = "뒤로가기") } },
                actions = { IconButton(onClick = { }) { Icon(Icons.Default.BookmarkBorder, contentDescription = "저장") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState())) {

            // 데이터 로딩 중이거나 없을 때
            if (recipe == null) {
                Box(modifier = Modifier.fillMaxWidth().height(250.dp).background(Color(0xFFF0F0F0)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = primaryGreen)
                }
            } else {
                // 1. 진짜 사진 뿌려주기
                AsyncImage(
                    model = recipe!!.imageUrl,
                    contentDescription = recipe!!.menuName,
                    modifier = Modifier.fillMaxWidth().height(250.dp),
                    contentScale = ContentScale.Crop
                )

                Column(modifier = Modifier.padding(20.dp)) {
                    // 2. 진짜 이름 뿌려주기
                    Text(text = recipe!!.menuName, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("20분", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("350 kcal", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) // 시간/칼로리는 추후 확장 가능
                    }
                    Spacer(modifier = Modifier.height(32.dp))

                    // 3. 진짜 재료 파싱해서 뿌려주기
                    Text("필요한 재료", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // "차돌박이 150g, 두부 반 모" -> 쉼표로 분리
                            val ingredientsList = recipe!!.ingredients.split(",")
                            ingredientsList.forEachIndexed { index, item ->
                                val parts = item.trim().split(" ") // "차돌박이"와 "150g" 분리
                                val name = if (parts.size > 1) parts.dropLast(1).joinToString(" ") else item.trim()
                                val amount = if (parts.size > 1) parts.last() else ""

                                RecipeIngredientRow(name, amount)

                                if (index < ingredientsList.size - 1) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))

                    // 4. 진짜 조리 순서 파싱해서 뿌려주기
                    Text("조리 순서", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    // "\n" 단위로 분리해서 줄마다 표시
                    val stepsList = recipe!!.instructions.split("\n")
                    stepsList.forEachIndexed { index, step ->
                        // "1. 냄비에..." 처럼 앞에 붙은 숫자와 점을 지워줌 (UI에서 예쁘게 숫자 박스를 그려주니까)
                        val cleanStep = step.replace(Regex("^[0-9]+\\.\\s*"), "").trim()
                        if (cleanStep.isNotBlank()) {
                            RecipeStepRow((index + 1).toString(), cleanStep, primaryGreen)
                        }
                    }
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

// ==========================================
// 식단 캘린더 → 메뉴 상세 화면 (실제 DB 데이터 기반)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealDetailScreen(mealId: Int, onBackClick: () -> Unit) {
    val backgroundColor = Color(0xFFFCFCFA)
    val primaryGreen = Color(0xFF5A8754)
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { com.example.menu_recipe_app.db.AppDatabase.getDatabase(context) }
    val dao = db.mealPlanDao()

    var meal by remember { mutableStateOf<com.example.menu_recipe_app.db.MealPlanEntity?>(null) }

    LaunchedEffect(mealId) {
        meal = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            dao.getMealById(mealId)
        }
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text(meal?.menuName ?: "", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBackIosNew, contentDescription = "뒤로가기") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        }
    ) { innerPadding ->
        if (meal == null) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = primaryGreen)
            }
        } else {
            val currentMeal = meal!!
            // 재료 목록 파싱 (JSON 배열 문자열 → List<String>)
            val ingredients = remember(currentMeal.ingredients) {
                try {
                    kotlinx.serialization.json.Json.decodeFromString<List<String>>(currentMeal.ingredients)
                } catch (e: Exception) {
                    listOf(currentMeal.ingredients)
                }
            }
            // 조리법 파싱 (번호. 단계 형식으로 분리)
            val steps = remember(currentMeal.recipe) {
                currentMeal.recipe
                    .split(Regex("(?=\\d+\\. )"))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }

            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // 상단 히어로 이미지 영역
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.RestaurantMenu,
                            contentDescription = null,
                            tint = primaryGreen.copy(alpha = 0.4f),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            currentMeal.menuName,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E5D29)
                        )
                    }
                }

                Column(modifier = Modifier.padding(20.dp)) {
                    // 칼로리 및 메타 정보
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color(0xFFFF5722), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("${currentMeal.calories} kcal", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("칼로리", color = Color.Gray, fontSize = 12.sp)
                            }
                            // 구분선
                            Box(modifier = Modifier.width(1.dp).height(48.dp).background(Color(0xFFEEEEEE)))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Restaurant, contentDescription = null, tint = primaryGreen, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                val mealTypeName = when (currentMeal.mealType) {
                                    "breakfast" -> "아침"
                                    "lunch" -> "점심"
                                    "dinner" -> "저녁"
                                    "snack" -> "간식"
                                    else -> currentMeal.mealType
                                }
                                Text(mealTypeName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("끼니", color = Color.Gray, fontSize = 12.sp)
                            }
                            Box(modifier = Modifier.width(1.dp).height(48.dp).background(Color(0xFFEEEEEE)))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFF5C7AEA), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(currentMeal.date.substring(5), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("날짜", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 재료 목록
                    Text("필요한 재료", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            ingredients.forEachIndexed { index, ingredient ->
                                Text(
                                    text = "• $ingredient",
                                    fontSize = 15.sp,
                                    color = Color.DarkGray,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                if (index < ingredients.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFF5F5F5))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 조리 순서
                    Text("조리 순서", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (steps.size > 1) {
                        steps.forEachIndexed { index, step ->
                            // 번호와 내용 분리 ("1. 내용" 형식)
                            val content = step.replace(Regex("^\\d+\\.\\s*"), "")
                            RecipeStepRow("${index + 1}", content, primaryGreen)
                        }
                    } else {
                        // 스텝 파싱이 안 된 경우 원본 텍스트 그대로 표시
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = currentMeal.recipe,
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                color = Color.DarkGray,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
fun RecipeIngredientRow(name: String, amount: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, fontSize = 15.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
        Text(amount, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun RecipeStepRow(stepNum: String, instruction: String, primaryColor: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.size(24.dp).background(primaryColor, CircleShape).padding(top = 2.dp), contentAlignment = Alignment.Center) {
            Text(stepNum, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(instruction, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 2.dp))
    }
}@Composable
fun SelectableOptionChip(
    modifier: Modifier = Modifier,
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    primaryColor: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) primaryColor.copy(alpha = 0.08f) else Color.White,
        border = BorderStroke(1.dp, if (isSelected) primaryColor else androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            color = if (isSelected) primaryColor else Color.Gray,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp)
        )
    }
}
    // ==========================================
// ★ 새로운 화면: 상세 캘린더 탭
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    navController: androidx.navigation.NavController,
    dietViewModel: DietViewModel // ★ CalendarCard가 요구하므로 파라미터로 받음
) {
    val backgroundColor = androidx.compose.material3.MaterialTheme.colorScheme.background
    val primaryGreen = Color(0xFF5A8754)
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { com.example.menu_recipe_app.db.AppDatabase.getDatabase(context) }
    val dao = db.mealPlanDao()

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var dailyMeals by remember(selectedDate) { mutableStateOf<List<com.example.menu_recipe_app.db.MealPlanEntity>>(emptyList()) }

    LaunchedEffect(selectedDate) {
        val dateStr = selectedDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        // 코루틴 내에서 DB 조회 (AI가 생성해 저장한 식단 - MealPlanEntity 기준)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            dailyMeals = dao.getMealsByDate(dateStr)
        }
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("식단 캘린더", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        },
        bottomBar = { BottomNavigationBar(navController, "calendar") }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // ★ 상세 캘린더 화면 안에서는 '더보기'를 눌러도 아무 일도 안 일어나게 빈 괄호 {} 를 넘깁니다.
            CalendarCard(
                onNavigateToCalendar = {},
                selectedDate = selectedDate,
                onDateSelected = { selectedDate = it },
                dietViewModel = dietViewModel
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text("선택한 날짜의 식단", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            if (dailyMeals.isEmpty()) {
                // 임시로 보여줄 빈 상태(Empty State) UI
                Card(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Restaurant, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("해당 날짜에 등록된 식단이 없습니다.", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            } else {
                // 식단 목록 렌더링 - 클릭 시 상세 화면으로 이동
                dailyMeals.forEach { meal ->
                    val mealTypeName = when (meal.mealType) {
                        "breakfast" -> "아침"
                        "lunch" -> "점심"
                        "dinner" -> "저녁"
                        "snack" -> "간식"
                        else -> meal.mealType
                    }
                    Card(
                        onClick = { navController.navigate("meal_detail/${meal.id}") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.background(primaryGreen, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(mealTypeName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(meal.menuName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = "상세보기", tint = Color.LightGray, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("${meal.calories} kcal", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun TicketShopSheetContent(primaryColor: Color, onBuySuccess: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🎫", fontSize = 40.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text("티켓 충전소", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("AI 에이전트 식단 생성을 위해 티켓이 필요해요.", fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(32.dp))

        // 1. 일반 결제 패키지
        TicketPackageCard(
            title = "베이직 패키지 (10장)",
            price = "₩ 1,500",
            description = "가장 인기 있는 기본 패키지",
            iconColor = primaryColor,
            onClick = { onBuySuccess(10) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. MetaMask 지갑 연동 결제 패키지 (스마트 컨트랙트용 껍데기)
        TicketPackageCard(
            title = "Web3 패키지 (30장)",
            price = "0.002 ETH",
            description = "MetaMask 지갑 연결 및 스마트 컨트랙트 결제",
            iconColor = Color(0xFFF6851B), // 메타마스크 상징색 (여우 오렌지)
            onClick = { onBuySuccess(30) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. 광고 보고 무료 충전
        TicketPackageCard(
            title = "광고 보고 1장 받기",
            price = "무료",
            description = "짧은 영상 시청 후 즉시 지급",
            iconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = { onBuySuccess(1) }
        )
    }
}

@Composable
fun TicketPackageCard(title: String, price: String, description: String, iconColor: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = iconColor)
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(price, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
    }
}

// ==========================================
// ★ 새로운 화면: 마이페이지 (신체 정보 입력 기능 추가)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPageScreen(
    navController: androidx.navigation.NavController,
    isLoggedIn: Boolean,
    ticketCount: Int,
    userCalories: Int?,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onCaloriesCalculated: (Int) -> Unit
) {
    val backgroundColor = androidx.compose.material3.MaterialTheme.colorScheme.background
    val primaryGreen = Color(0xFF5A8754)

    // 신체 정보 입력 팝업 상태
    var showBodyInfoDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = backgroundColor,
        topBar = { TopAppBar(title = { Text("마이페이지", fontWeight = FontWeight.Bold, fontSize = 20.sp) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)) },
        bottomBar = { BottomNavigationBar(navController, "my") }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                if (isLoggedIn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFE8F5E9)), contentAlignment = Alignment.Center) {
                            Text("👨‍💻", fontSize = 32.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("개발자님, 환영합니다!", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("dev@startup.com", fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    // 티켓 박스
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFAFAFA), border = BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎫", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("내 보유 티켓", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            Text("${ticketCount}장", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = primaryGreen)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // ★ 나의 하루 권장 칼로리 박스
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF4F9F4), border = BorderStroke(1.dp, primaryGreen.copy(alpha=0.3f)), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("🔥 나의 맞춤 권장 칼로리", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = primaryGreen)
                                Spacer(modifier = Modifier.height(4.dp))
                                if (userCalories != null) {
                                    Text("${userCalories} kcal", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                } else {
                                    Text("신체 정보를 입력해주세요", fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Button(onClick = { showBodyInfoDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = primaryGreen), shape = RoundedCornerShape(8.dp)) {
                                Text(if (userCalories != null) "수정" else "입력", fontSize = 13.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(onClick = onLogoutClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant)) {
                        Text("로그아웃", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                } else {
                    // 로그아웃 상태 UI
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFF0F0F0)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(40.dp)) }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("로그인이 필요합니다", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("로그인하고 맞춤 식단을 관리해보세요!", fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onLoginClick, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = primaryGreen), shape = RoundedCornerShape(12.dp)) { Text("로그인 / 회원가입", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            }

            HorizontalDivider(color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, thickness = 8.dp)

            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text("설정 및 안내", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                MyPageToggleItem(icon = Icons.Default.Settings, title = "다크모드", checked = isDarkMode, onCheckedChange = onDarkModeChange)
                if (isLoggedIn) {
                    MyPageMenuItem(icon = Icons.Default.CreditCard, title = "결제 내역", onClick = {})
                    MyPageMenuItem(icon = Icons.Default.DeleteForever, title = "회원 탈퇴", onClick = {}, isDanger = true)
                }
            }
        }
    }

    // 신체 정보 입력 다이얼로그
    if (showBodyInfoDialog) {
        BodyInfoDialog(
            primaryColor = primaryGreen,
            onDismiss = { showBodyInfoDialog = false },
            onCalculate = { calories ->
                onCaloriesCalculated(calories)
                showBodyInfoDialog = false
            }
        )
    }
}

// ★ 신체 정보 입력 및 칼로리 계산 컴포넌트 (식단 목표 및 맞춤 칼로리 로직 추가)


@Composable
fun BodyInfoDialog(primaryColor: Color, onDismiss: () -> Unit, onCalculate: (Int) -> Unit) {
    var gender by remember { mutableStateOf("남성") }
    var age by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var activityLevel by remember { mutableStateOf(1.375) } // 기본값: 가벼운 활동

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        title = { Text("신체 정보 설정", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("정확한 맞춤 식단을 위해 신체 정보를 입력해주세요.", fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))

                Text("성별", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                    SelectableOptionChip(modifier = Modifier.weight(1f), text = "남성", isSelected = gender == "남성", onClick = { gender = "남성" }, primaryColor = primaryColor)
                    SelectableOptionChip(modifier = Modifier.weight(1f), text = "여성", isSelected = gender == "여성", onClick = { gender = "여성" }, primaryColor = primaryColor)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = age, onValueChange = { age = it }, label = { Text("나이") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor)
                    )
                    OutlinedTextField(
                        value = height, onValueChange = { height = it }, label = { Text("키 (cm)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = weight, onValueChange = { weight = it }, label = { Text("몸무게 (kg)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text("평소 활동량", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectableOptionChip(modifier = Modifier.fillMaxWidth(), text = "앉아있는 시간이 많음 (운동 거의 안함)", isSelected = activityLevel == 1.2, onClick = { activityLevel = 1.2 }, primaryColor = primaryColor)
                    SelectableOptionChip(modifier = Modifier.fillMaxWidth(), text = "보통 (주 1~3회 가벼운 운동)", isSelected = activityLevel == 1.375, onClick = { activityLevel = 1.375 }, primaryColor = primaryColor)
                    SelectableOptionChip(modifier = Modifier.fillMaxWidth(), text = "활동적 (주 3~5회 운동)", isSelected = activityLevel == 1.55, onClick = { activityLevel = 1.55 }, primaryColor = primaryColor)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val a = age.toIntOrNull() ?: 0
                    val h = height.toDoubleOrNull() ?: 0.0
                    val w = weight.toDoubleOrNull() ?: 0.0
                    if (a > 0 && h > 0 && w > 0) {
                        // Mifflin-St Jeor 기초대사량 계산식 (보다 최신/정확한 공식)
                        val bmr = if (gender == "남성") (10 * w) + (6.25 * h) - (5 * a) + 5 else (10 * w) + (6.25 * h) - (5 * a) - 161
                        val tdee = (bmr * activityLevel).toInt() // 활동 대사량(TDEE) 계산
                        onCalculate(tdee)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) { Text("계산 완료", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}

@Composable
fun MyPageMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit, isDanger: Boolean = false) {
    val tintColor = if (isDanger) Color(0xFFE53935) else Color.DarkGray

    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tintColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, fontSize = 15.sp, color = tintColor, fontWeight = if (isDanger) FontWeight.Medium else FontWeight.Normal)
        }
        if (!isDanger) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
        }
    }
}
}


@Composable
fun MyPageToggleItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, fontSize = 15.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

