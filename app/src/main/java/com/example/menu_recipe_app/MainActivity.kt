package com.example.menu_recipe_app // ★ 본인 패키지명으로 꼭 확인하세요!

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.time.LocalDate
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                AppNavigation()
            }
        }
    }
}

// ==========================================
// 1. 네비게이션 라우터 (레시피 탭 포함)
// ==========================================
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                navController = navController,
                onNavigateToGenerate = { navController.navigate("generate_step1") }
            )
        }
        composable("generate_step1") {
            GenerateStep1Screen(onBackClick = { navController.popBackStack() }, onNextClick = { navController.navigate("generate_step2") })
        }
        composable("generate_step2") {
            GenerateStep2Screen(onBackClick = { navController.popBackStack() }, onNextClick = { navController.navigate("generate_step3") })
        }
        composable("generate_step3") {
            GenerateStep3Screen(onBackClick = { navController.popBackStack() }, onSaveClick = { navController.navigate("generate_step4") }, onRegenerateClick = { }, onChangeAgentClick = { navController.popBackStack() })
        }
        composable("generate_step4") {
            GenerateStep4Screen(onBackClick = { navController.popBackStack() }, onGoMainClick = { navController.navigate("main") { popUpTo("main") { inclusive = false } } }, onEditClick = { navController.popBackStack() })
        }
        composable("recipe") {
            RecipeScreen(
                navController = navController,
                onNavigateToDetail = { navController.navigate("recipe_detail") }
            )
        }
        composable("recipe_detail") {
            RecipeDetailScreen(onBackClick = { navController.popBackStack() })
        }
    }
}

// ==========================================
// 공통 컴포넌트: 스마트한 진행 단계 표시기
// ==========================================
@Composable
fun StepIndicator(currentStep: Int) {
    val primaryGreen = Color(0xFF5A8754)
    val grayColor = Color(0xFFEEEEEE)
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
// 메인 화면
// ==========================================
@Composable
fun MainScreen(navController: androidx.navigation.NavController, onNavigateToGenerate: () -> Unit) {
    val backgroundColor = Color(0xFFFCFCFA)
    val primaryGreen = Color(0xFF5A8754)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = backgroundColor,
        bottomBar = { BottomNavigationBar(navController, "main") }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            TopHeaderSection(primaryGreen)
            Spacer(modifier = Modifier.height(24.dp))
            WeeklyGenerateCard(primaryGreen, onNavigateToGenerate)
            Spacer(modifier = Modifier.height(24.dp))
            CalendarCard()
            Spacer(modifier = Modifier.height(24.dp))
            IngredientsCard()
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun TopHeaderSection(primaryColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Eco, contentDescription = "로고", tint = primaryColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "식단관리", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Text(text = "건강한 하루, 균형 잡힌 식단", fontSize = 14.sp, color = Color.Gray)
        }
        Row {
            IconButton(onClick = { }) { Icon(Icons.Default.Notifications, contentDescription = "알림") }
            IconButton(onClick = { }) { Icon(Icons.Default.AccountCircle, contentDescription = "프로필", tint = primaryColor) }
        }
    }
}

@Composable
fun WeeklyGenerateCard(primaryColor: Color, onNavigate: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
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

@Composable
fun CalendarCard() {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val primaryGreen = Color(0xFF5A8754)

    Card(colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = primaryGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("캘린더", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "이전 달")
                    }
                    Text(text = "${currentMonth.year}년 ${currentMonth.monthValue}월", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                    IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "다음 달")
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(primaryGreen))
                Spacer(modifier = Modifier.width(4.dp))
                Text("식단 계획", fontSize = 10.sp, color = Color.Gray)
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF8D6E63)))
                Spacer(modifier = Modifier.width(4.dp))
                Text("기록", fontSize = 10.sp, color = Color.Gray)
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
                        CalendarDayItem(date = date, isSelected = date == selectedDate, onClick = { selectedDate = date }, primaryGreen = primaryGreen)
                    } else {
                        Box(modifier = Modifier.size(48.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarDayItem(date: LocalDate, isSelected: Boolean, onClick: () -> Unit, primaryGreen: Color) {
    val hasPlan = date.dayOfMonth % 2 != 0 || date.dayOfMonth % 5 == 0
    val hasRecord = date.dayOfMonth % 3 == 0

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
        Text(count, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun BottomNavigationBar(navController: androidx.navigation.NavController, currentRoute: String) {
    val primaryGreen = Color(0xFF5A8754)
    NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "홈") },
            label = { Text("홈") },
            selected = currentRoute == "main",
            onClick = { if (currentRoute != "main") navController.navigate("main") { popUpTo("main") { saveState = true } } },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = primaryGreen, selectedTextColor = primaryGreen, indicatorColor = Color(0xFFE8F5E9))
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.FormatListBulleted, contentDescription = "식단") }, // List 대신 FormatListBulleted 사용
            label = { Text("식단") },
            selected = currentRoute == "diet",
            onClick = { }
        )
        NavigationBarItem(
            icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "레시피") }, // 신형 MenuBook 아이콘 적용
            label = { Text("레시피") },
            selected = currentRoute == "recipe",
            onClick = { if (currentRoute != "recipe") navController.navigate("recipe") { popUpTo("main") { saveState = true } } },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = primaryGreen, selectedTextColor = primaryGreen, indicatorColor = Color(0xFFE8F5E9))
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Person, contentDescription = "MY") },
            label = { Text("MY") },
            selected = currentRoute == "my",
            onClick = { }
        )
    }
}

// ==========================================
// 1단계: 재료 선택
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateStep1Screen(onBackClick: () -> Unit, onNextClick: () -> Unit) {
    val backgroundColor = Color(0xFFFCFCFA)
    val primaryGreen = Color(0xFF5A8754)
    var selectedOption by remember { mutableStateOf<String?>(null) }
    var textInput by remember { mutableStateOf("") }
    val myIngredients = remember { mutableStateListOf<String>() }
    val recommendedIngredients = listOf("계란", "양파", "대파", "마늘", "두부", "닭가슴살", "돼지고기", "감자")

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
                onClick = onNextClick,
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
            Text("보유한 재료에 맞춰 맛있고 건강한 식단을 추천해드려요.", fontSize = 14.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(32.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SelectionCard(modifier = Modifier.weight(1f), title = "재료 없음", description = "보유한 재료 없이\n식단을 추천받을래요.", isSelected = selectedOption == "없음", onClick = { selectedOption = "없음" }, primaryColor = primaryGreen)
                SelectionCard(modifier = Modifier.weight(1f), title = "재료 있음", description = "가지고 있는 재료로\n식단을 추천받을래요.", isSelected = selectedOption == "있음", onClick = { selectedOption = "있음" }, primaryColor = primaryGreen)
            }
            if (selectedOption == "있음") {
                Spacer(modifier = Modifier.height(32.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = Color(0xFFEEEEEE))
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
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryGreen, unfocusedBorderColor = Color(0xFFEEEEEE), focusedContainerColor = Color.White, unfocusedContainerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (myIngredients.isNotEmpty()) {
                        Text("선택된 재료", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
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
                    Text("자주 쓰는 추천 재료", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column {
                        recommendedIngredients.chunked(4).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                rowItems.forEach { ingredient ->
                                    val isAlreadyAdded = myIngredients.contains(ingredient)
                                    Surface(
                                        shape = RoundedCornerShape(20.dp), color = if (isAlreadyAdded) Color(0xFFF0F0F0) else Color.White, border = BorderStroke(1.dp, if (isAlreadyAdded) Color.Transparent else Color(0xFFEEEEEE)),
                                        modifier = Modifier.clickable { if (!isAlreadyAdded) myIngredients.add(ingredient) }
                                    ) { Text(text = if (isAlreadyAdded) "$ingredient ✓" else "+ $ingredient", fontSize = 13.sp, color = if (isAlreadyAdded) Color.LightGray else Color.DarkGray, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
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
    Card(modifier = modifier.height(220.dp).clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = Color.White), border = if (isSelected) BorderStroke(2.dp, primaryColor) else BorderStroke(1.dp, Color(0xFFEEEEEE)), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isSelected) Icon(Icons.Default.CheckCircle, contentDescription = "선택됨", tint = primaryColor, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(modifier = Modifier.size(80.dp).background(Color(0xFFF5F5F5), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if(title.contains("없음")) Icons.Default.Kitchen else Icons.Default.ShoppingBasket, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if(isSelected) primaryColor else Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Text(description, fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
            }
        }
    }
}

// ==========================================
// 2단계: 에이전트 선택
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateStep2Screen(onBackClick: () -> Unit, onNextClick: () -> Unit) {
    val backgroundColor = Color(0xFFFCFCFA)
    val primaryGreen = Color(0xFF5A8754)
    var selectedAgent by remember { mutableStateOf<String?>(null) }

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
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("선택한 Agent는 언제든 변경할 수 있어요.", color = Color.Gray, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onNextClick, enabled = selectedAgent != null, colors = ButtonDefaults.buttonColors(containerColor = primaryGreen, disabledContainerColor = Color(0xFFD6D6D6)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("다음", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if(selectedAgent != null) Color.White else Color.Gray)
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.Start) {
            Spacer(modifier = Modifier.height(16.dp))
            StepIndicator(currentStep = 2)
            Spacer(modifier = Modifier.height(40.dp))
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("영양사 Agent를 선택하세요", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("전문 영양사가 당신의 목표에 맞는 식단을 설계해드려요.", fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(32.dp))
                AgentCard("자취생 영양사", "가성비와 식재료 낭비 방지에 초점을 맞춘 1인 가구 추천 식단", Icons.Default.Eco, "절약형 식단을 원하는 분", true, selectedAgent == "실속관리", { selectedAgent = "실속관리" }, primaryGreen)
                Spacer(modifier = Modifier.height(16.dp))
                AgentCard("가족 영양사", "3~4인 가구가 선택하기 좋은 식단 추천", Icons.Default.FamilyRestroom, "주부 및 다인 가구", false, selectedAgent == "패밀리케어", { selectedAgent = "패밀리케어" }, primaryGreen)
                Spacer(modifier = Modifier.height(16.dp))
                AgentCard("혈당 케어 영양사", "혈당 스파이크를 방지하는 저당, 저탄수화물 위주의 건강 식단", Icons.Default.MonitorHeart, "당뇨 및 건강 관리가 필요한 분", false, selectedAgent == "혈당케어", { selectedAgent = "혈당케어" }, primaryGreen)
            }
        }
    }
}

@Composable
fun AgentCard(title: String, description: String, targetIcon: androidx.compose.ui.graphics.vector.ImageVector, targetText: String, isRecommended: Boolean, isSelected: Boolean, onClick: () -> Unit, primaryColor: Color) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = Color.White), border = if (isSelected) BorderStroke(1.5.dp, primaryColor) else BorderStroke(1.dp, Color(0xFFEEEEEE)), elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)) {
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
                Text(description, fontSize = 13.sp, color = Color.DarkGray, lineHeight = 18.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(targetIcon, contentDescription = null, tint = primaryColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(targetText, fontSize = 12.sp, color = Color.Gray)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            RadioButton(selected = isSelected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = primaryColor, unselectedColor = Color.LightGray))
        }
    }
}

// ==========================================
// 3단계: 식단 확인
// ==========================================
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GenerateStep3Screen(onBackClick: () -> Unit, onSaveClick: () -> Unit, onRegenerateClick: () -> Unit, onChangeAgentClick: () -> Unit) {
    val backgroundColor = Color(0xFFFCFCFA)
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
            Column(modifier = Modifier.background(backgroundColor).padding(horizontal = 20.dp).padding(bottom = 24.dp, top = 8.dp)) {
                Button(onClick = onSaveClick, colors = ButtonDefaults.buttonColors(containerColor = primaryGreen), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("이 식단으로 저장", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onRegenerateClick, border = BorderStroke(1.dp, Color(0xFFEEEEEE)), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color.DarkGray), modifier = Modifier.weight(1f).height(50.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("다시 생성하기", fontSize = 14.sp)
                    }
                    OutlinedButton(onClick = onChangeAgentClick, border = BorderStroke(1.dp, Color(0xFFEEEEEE)), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color.DarkGray), modifier = Modifier.weight(1f).height(50.dp)) {
                        Icon(Icons.Default.PersonOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("다른 Agent 선택", fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("저장된 식단은 언제든 수정하거나 다시 생성할 수 있어요.", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(16.dp))
            StepIndicator(currentStep = 3)
            Spacer(modifier = Modifier.height(24.dp))
            AgentSummaryCard(primaryGreen)
            Spacer(modifier = Modifier.height(32.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("이번 주 식단표", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFEEEEEE))) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("주간 요약 보기", fontSize = 12.sp, color = Color.DarkGray)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            val pagerState = rememberPagerState(pageCount = { 7 })
            val days = listOf("월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일")
            HorizontalPager(state = pagerState, contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 16.dp) { page ->
                DailyDietCard(dayName = days[page], isCurrentPage = pagerState.currentPage == page, primaryColor = primaryGreen)
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

@Composable
fun AgentSummaryCard(primaryColor: Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFEEEEEE)), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFF0F0F0)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(32.dp)) }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("선택된 Agent: ", fontSize = 12.sp, color = Color.Gray)
                    Text("실속 관리 Agent", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("이번 주 맞춤 식단이 생성되었어요", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("균형 잡힌 영양으로 건강한 식습관을 도와드릴게요!", fontSize = 11.sp, color = Color.Gray)
            }
            Icon(Icons.Default.Eco, contentDescription = null, tint = primaryColor, modifier = Modifier.size(24.dp).padding(end = 8.dp))
        }
    }
}

@Composable
fun DailyDietCard(dayName: String, isCurrentPage: Boolean, primaryColor: Color) {
    val borderColor = if (isCurrentPage) primaryColor else Color(0xFFEEEEEE)
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(if (isCurrentPage) 1.5.dp else 1.dp, borderColor), elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(modifier = Modifier.align(Alignment.CenterHorizontally).background(Color(0xFFF9F9F9), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(dayName, fontWeight = FontWeight.Bold, color = if (isCurrentPage) primaryColor else Color.Gray, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(24.dp))
            MealRow("아침", primaryColor, "밥, 된장국, 계란말이,\n시금치나물")
            Spacer(modifier = Modifier.height(16.dp))
            MealRow("점심", primaryColor, "밥, 된장국, 닭가슴살볶음,\n나물무침")
            Spacer(modifier = Modifier.height(16.dp))
            MealRow("저녁", primaryColor, "밥, 된장국, 두부조림,\n브로콜리무침")
            Spacer(modifier = Modifier.height(16.dp))
            MealRow("간식", primaryColor, "사과, 견과류")
            Spacer(modifier = Modifier.height(24.dp))
            Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF9F9F9), RoundedCornerShape(8.dp)).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Eco, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("총 열량 1,780 kcal", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray)
                }
            }
        }
    }
}

@Composable
fun MealRow(mealType: String, primaryColor: Color, menu: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFF5F5F5)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Restaurant, contentDescription = null, tint = Color.LightGray) }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(mealType, color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(menu, fontSize = 14.sp, color = Color.DarkGray, lineHeight = 20.sp)
        }
    }
}

// ==========================================
// 4단계: 완료
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateStep4Screen(onBackClick: () -> Unit, onGoMainClick: () -> Unit, onEditClick: () -> Unit) {
    val backgroundColor = Color(0xFFFCFCFA)
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
                OutlinedButton(onClick = onEditClick, border = BorderStroke(1.dp, Color(0xFFEEEEEE)), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color.DarkGray), modifier = Modifier.fillMaxWidth().height(56.dp)) {
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
            Text("이번주 식단이 내 식단표에 반영되었어요", fontSize = 15.sp, color = Color.DarkGray)
            Spacer(modifier = Modifier.height(40.dp))
            AgentFinalSummaryCard(primaryGreen)
            Spacer(modifier = Modifier.height(40.dp))
            Text("꾸준한 실천이 건강한 변화를 만듭니다.", fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(4.dp))
            Text("다음주에도 균형 잡힌 식단으로 함께해요!", fontSize = 13.sp, color = primaryGreen, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun AgentFinalSummaryCard(primaryColor: Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFBFBFB)), border = BorderStroke(1.dp, Color(0xFFEEEEEE)), elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)) {
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
                    Text("이번주 월요일부터 금요일까지 적용", fontSize = 12.sp, color = Color.DarkGray)
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Restaurant, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("5일 식단 저장됨", fontSize = 12.sp, color = Color.DarkGray)
                }
            }
        }
    }
}

// ==========================================
// ★ 새로운 화면: 레시피 메인 탭 (3xN 원형 그리드)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(navController: androidx.navigation.NavController, onNavigateToDetail: () -> Unit) {
    val backgroundColor = Color(0xFFFCFCFA)
    // 쓰지 않는 primaryGreen 변수 삭제 완료
    val recipeList = listOf("된장찌개", "김치볶음밥", "계란말이", "제육볶음", "시금치무침", "두부조림", "오징어볶음", "감자채볶음", "소고기무국")

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(title = { Text("레시피", fontWeight = FontWeight.Bold, fontSize = 20.sp) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor))
        },
        bottomBar = { BottomNavigationBar(navController, "recipe") }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            OutlinedTextField(
                value = "", onValueChange = {}, placeholder = { Text("어떤 요리를 만들어볼까요?", color = Color.Gray, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "검색", tint = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White, unfocusedBorderColor = Color(0xFFEEEEEE)),
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxSize()
            ) {
                items(recipeList.size) { index ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onNavigateToDetail() }) {
                        Box(modifier = Modifier.size(90.dp).clip(CircleShape).background(Color(0xFFF0F0F0)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.RestaurantMenu, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(36.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(recipeList[index], fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
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
fun RecipeDetailScreen(onBackClick: () -> Unit) {
    val backgroundColor = Color(0xFFFCFCFA)
    val primaryGreen = Color(0xFF5A8754)

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
        // ★ 여기에 innerPadding을 필수로 넣어주어야 합니다!
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(modifier = Modifier.fillMaxWidth().height(250.dp).background(Color(0xFFF0F0F0)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.RestaurantMenu, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(80.dp))
            }
            Column(modifier = Modifier.padding(20.dp)) {
                Text("차돌박이 된장찌개", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("20분", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("350 kcal", color = Color.Gray, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text("필요한 재료", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFEEEEEE)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        RecipeIngredientRow("차돌박이", "150g")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFF5F5F5))
                        RecipeIngredientRow("애호박", "1/2개")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFF5F5F5))
                        RecipeIngredientRow("두부", "반 모")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFF5F5F5))
                        RecipeIngredientRow("시판 된장", "2큰술")
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text("조리 순서", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                RecipeStepRow("1", "애호박과 두부를 먹기 좋은 크기로 깍둑썰기 해줍니다.", primaryGreen)
                RecipeStepRow("2", "냄비에 차돌박이를 넣고 중불에서 겉면이 익을 때까지 볶아줍니다.", primaryGreen)
                RecipeStepRow("3", "고기가 익으면 물 500ml를 넣고 된장을 풀어줍니다.", primaryGreen)
                RecipeStepRow("4", "물이 끓어오르면 썰어둔 야채와 두부를 넣고 5분간 끓여 완성합니다.", primaryGreen)
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun RecipeIngredientRow(name: String, amount: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, fontSize = 15.sp, color = Color.DarkGray)
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
}