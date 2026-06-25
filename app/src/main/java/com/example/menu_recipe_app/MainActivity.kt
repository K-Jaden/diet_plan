package com.example.menu_recipe_app // 본인 패키지명으로 꼭 확인하세요!

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.ui.text.SpanStyle

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
// 1. 네비게이션 라우터 (4단계 및 메인 복귀 완벽 적용)
// ==========================================
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(onNavigateToGenerate = { navController.navigate("generate_step1") })
        }
        composable("generate_step1") {
            GenerateStep1Screen(
                onBackClick = { navController.popBackStack() },
                onNextClick = { navController.navigate("generate_step2") }
            )
        }
        composable("generate_step2") {
            GenerateStep2Screen(
                onBackClick = { navController.popBackStack() },
                onNextClick = { navController.navigate("generate_step3") }
            )
        }
        composable("generate_step3") {
            GenerateStep3Screen(
                onBackClick = { navController.popBackStack() },
                onSaveClick = { navController.navigate("generate_step4") }, // 4단계로 이동!
                onRegenerateClick = { /* TODO: 식단 다시 생성 로직 */ },
                onChangeAgentClick = { navController.popBackStack() }
            )
        }
        composable("generate_step4") {
            GenerateStep4Screen(
                onBackClick = { navController.popBackStack() },
                onGoMainClick = {
                    // 메인으로 돌아갈 때, 중간에 쌓인 1, 2, 3단계 화면 기록을 싹 지웁니다.
                    navController.navigate("main") {
                        popUpTo("main") { inclusive = false }
                    }
                },
                onEditClick = { navController.popBackStack() } // 다시 3단계(수정)로 돌아가기
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
    val grayColor = Color(0xFFEEEEEE)
    val textGray = Color.Gray

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
    ) {
        val steps = listOf("재료 선택", "식단 유형", "식단 확인", "완료")

        steps.forEachIndexed { index, title ->
            val stepNumber = index + 1
            val isCompleted = stepNumber < currentStep
            val isActive = stepNumber == currentStep

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(if (isActive || isCompleted) primaryGreen else grayColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(Icons.Default.Check, contentDescription = "완료", tint = Color.White, modifier = Modifier.size(18.dp))
                    } else {
                        Text(stepNumber.toString(), color = if (isActive) Color.White else textGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(title, fontSize = 10.sp, color = if (isActive || isCompleted) primaryGreen else textGray, modifier = Modifier.padding(top = 4.dp))
            }

            if (index < steps.size - 1) {
                HorizontalDivider(
                    modifier = Modifier
                        .width(40.dp)
                        .padding(horizontal = 4.dp)
                        .offset(y = (-8).dp),
                    color = if (isCompleted) primaryGreen else grayColor,
                    thickness = 1.dp
                )
            }
        }
    }
}

// ==========================================
// 2. 새로운 화면: 식단표 생성 2단계 (Agent 선택)
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
                Button(
                    onClick = onNextClick,
                    enabled = selectedAgent != null,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryGreen, disabledContainerColor = Color(0xFFD6D6D6)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("다음", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if(selectedAgent != null) Color.White else Color.Gray)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            StepIndicator(currentStep = 2) // 2단계로 설정!

            Spacer(modifier = Modifier.height(40.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("영양사 Agent를 선택하세요", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("전문 영양사가 당신의 목표에 맞는 식단을 설계해드려요.", fontSize = 14.sp, color = Color.Gray)

                Spacer(modifier = Modifier.height(32.dp))

                // Agent 리스트
                AgentCard(
                    title = "균형식 Agent",
                    description = "일반적인 영양 균형 중심 식단 추천",
                    targetIcon = Icons.Default.Eco,
                    targetText = "건강한 식습관을 만들고 싶은 분",
                    isRecommended = true,
                    isSelected = selectedAgent == "균형식",
                    onClick = { selectedAgent = "균형식" },
                    primaryColor = primaryGreen
                )
                Spacer(modifier = Modifier.height(16.dp))

                AgentCard(
                    title = "체중관리 Agent",
                    description = "체중 감량 및 체지방 감소에\n최적화된 식단 추천",
                    targetIcon = Icons.Default.LocalFireDepartment, // 임시 불꽃 아이콘
                    targetText = "체중 감량을 목표로 하는 분",
                    isRecommended = false,
                    isSelected = selectedAgent == "체중관리",
                    onClick = { selectedAgent = "체중관리" },
                    primaryColor = primaryGreen
                )
                Spacer(modifier = Modifier.height(16.dp))

                AgentCard(
                    title = "운동영양 Agent",
                    description = "운동 퍼포먼스 향상과 근육 증가에\n특화된 식단 추천",
                    targetIcon = Icons.Default.FitnessCenter, // 덤벨 아이콘
                    targetText = "운동 중이거나 근육량 증가를 원하는 분",
                    isRecommended = false,
                    isSelected = selectedAgent == "운동영양",
                    onClick = { selectedAgent = "운동영양" },
                    primaryColor = primaryGreen
                )
            }
        }
    }
}

@Composable
fun AgentCard(
    title: String, description: String, targetIcon: androidx.compose.ui.graphics.vector.ImageVector, targetText: String,
    isRecommended: Boolean, isSelected: Boolean, onClick: () -> Unit, primaryColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = if (isSelected) BorderStroke(1.5.dp, primaryColor) else BorderStroke(1.dp, Color(0xFFEEEEEE)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            // 왼쪽 3D 캐릭터 프로필 자리 (원형 회색 박스로 임시 처리)
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(Color(0xFFF0F0F0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 중앙 텍스트 영역
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (isRecommended) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.background(Color(0xFFE8F5E9), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("추천", color = primaryColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
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

            // 우측 라디오 버튼 영역
            RadioButton(
                selected = isSelected,
                onClick = null, // Card 자체 클릭으로 처리
                colors = RadioButtonDefaults.colors(selectedColor = primaryColor, unselectedColor = Color.LightGray)
            )
        }
    }
}

// ==========================================
// 1단계 화면 (수정됨: 2단계 이동 연결 및 공통 마일스톤 적용)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateStep1Screen(onBackClick: () -> Unit, onNextClick: () -> Unit) {
    val backgroundColor = Color(0xFFFCFCFA)
    val primaryGreen = Color(0xFF5A8754)
    var selectedOption by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = backgroundColor,
        topBar = { /* ... 이전과 동일한 TopAppBar ... */
            TopAppBar(
                title = { Text("식단표 생성", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBackIosNew, contentDescription = "뒤로가기") } },
                actions = { Spacer(modifier = Modifier.width(48.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        },
        bottomBar = { /* ... 이전과 동일한 버튼 ... */
            Button(
                onClick = onNextClick,
                enabled = selectedOption != null,
                colors = ButtonDefaults.buttonColors(containerColor = primaryGreen, disabledContainerColor = Color(0xFFE0E0E0)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(20.dp).height(56.dp)
            ) {
                Text("다음", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if(selectedOption != null) Color.White else Color.Gray)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            StepIndicator(currentStep = 1) // 스마트 마일스톤 적용!

            Spacer(modifier = Modifier.height(40.dp))
            Box(modifier = Modifier.size(56.dp).background(Color(0xFFE8F5E9), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Eco, contentDescription = null, tint = primaryGreen, modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("현재 사용할 수 있는\n재료가 있나요?", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 34.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text("보유한 재료에 맞춰 맛있고 건강한 식단을 추천해드려요.", fontSize = 14.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(40.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SelectionCard(modifier = Modifier.weight(1f), title = "재료 없음", description = "보유한 재료 없이\n식단을 추천받을래요.", isSelected = selectedOption == "없음", onClick = { selectedOption = "없음" }, primaryColor = primaryGreen)
                SelectionCard(modifier = Modifier.weight(1f), title = "재료 있음", description = "가지고 있는 재료로\n식단을 추천받을래요.", isSelected = selectedOption == "있음", onClick = { selectedOption = "있음" }, primaryColor = primaryGreen)
            }
        }
    }
}

@Composable
fun SelectionCard(modifier: Modifier, title: String, description: String, isSelected: Boolean, onClick: () -> Unit, primaryColor: Color) {
    Card(
        modifier = modifier.height(220.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = if (isSelected) BorderStroke(2.dp, primaryColor) else BorderStroke(1.dp, Color(0xFFEEEEEE)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, contentDescription = "선택됨", tint = primaryColor, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
            }
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

// --- 아래 MainScreen 영역은 이전 답변과 완벽히 동일하므로 길이 상 축약했습니다 ---
// (복사하실 때 기존 MainActivity.kt 맨 아래에 있던 MainScreen, TopHeaderSection, WeeklyGenerateCard 등의 코드는 그대로 두시면 됩니다!)

// ==========================================
// 3. 기존 메인 화면 (네비게이션 연동 위해 일부 수정됨)
// ==========================================
@Composable
fun MainScreen(onNavigateToGenerate: () -> Unit) { // 매개변수 추가됨!
    val backgroundColor = Color(0xFFFCFCFA)
    val primaryGreen = Color(0xFF5A8754)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = backgroundColor,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToGenerate, // FAB 클릭 시 화면 이동
                shape = CircleShape,
                containerColor = primaryGreen,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "생성")
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        bottomBar = { BottomNavigationBar() }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            TopHeaderSection(primaryGreen)
            Spacer(modifier = Modifier.height(24.dp))
            WeeklyGenerateCard(primaryGreen, onNavigateToGenerate) // 버튼 클릭 시 화면 이동
            Spacer(modifier = Modifier.height(24.dp))
            CalendarCard()
            Spacer(modifier = Modifier.height(24.dp))
            IngredientsCard()
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// --- 아래는 이전 답변과 동일한 MainScreen용 UI 컴포넌트들입니다 ---
@Composable
fun TopHeaderSection(primaryColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Eco, contentDescription = "로고", tint = primaryColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "식단관리", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Text(text = "건강한 하루, 균형 잡힌 식단 💚", fontSize = 14.sp, color = Color.Gray)
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
            Button(
                onClick = onNavigate, // 버튼 클릭 시 화면 이동
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("생성하러 가기", modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
fun CalendarCard() {
    // 현재 달과 선택된 날짜 상태 관리 (기본값: 오늘)
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val primaryGreen = Color(0xFF5A8754)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // 1. 상단 헤더 (타이틀, 월 이동, 범례)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 타이틀
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = primaryGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("캘린더", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                // 월 이동 컨트롤러 (< 2026년 6월 >)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { currentMonth = currentMonth.minusMonths(1) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "이전 달")
                    }
                    Text(
                        text = "${currentMonth.year}년 ${currentMonth.monthValue}월",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(
                        onClick = { currentMonth = currentMonth.plusMonths(1) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "다음 달")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 범례 (식단 계획, 기록)
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

            // 2. 요일 헤더 (일 ~ 토)
            val daysOfWeek = listOf("일", "월", "화", "수", "목", "금", "토")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                daysOfWeek.forEachIndexed { index, day ->
                    val color = when (index) {
                        0 -> Color.Red        // 일요일
                        6 -> Color(0xFF1976D2) // 토요일
                        else -> Color.DarkGray
                    }
                    Text(text = day, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. 달력 그리드 생성
            val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value % 7 // 일요일=0, 월요일=1...
            val daysInMonth = currentMonth.lengthOfMonth()

            // 총 보여줄 셀 개수 (빈 칸 포함, 최대 6줄 * 7일 = 42)
            val totalCells = ((firstDayOfWeek + daysInMonth - 1) / 7 + 1) * 7

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.heightIn(min = 250.dp, max = 320.dp), // 달력 높이 동적 설정
                userScrollEnabled = false
            ) {
                items(totalCells) { index ->
                    val dayOffset = index - firstDayOfWeek + 1
                    if (dayOffset in 1..daysInMonth) {
                        val date = currentMonth.atDay(dayOffset)
                        CalendarDayItem(
                            date = date,
                            isSelected = date == selectedDate,
                            onClick = { selectedDate = date },
                            primaryGreen = primaryGreen
                        )
                    } else {
                        // 날짜가 없는 빈 칸
                        Box(modifier = Modifier.size(48.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarDayItem(date: LocalDate, isSelected: Boolean, onClick: () -> Unit, primaryGreen: Color) {
    // 더미 데이터: 이미지처럼 그럴싸하게 보이도록 짝수/홀수일에 식단 아이콘 배치
    val hasPlan = date.dayOfMonth % 2 != 0 || date.dayOfMonth % 5 == 0
    val hasRecord = date.dayOfMonth % 3 == 0

    Column(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) primaryGreen.copy(alpha = 0.1f) else Color.Transparent)
            .clickable { onClick() }
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 날짜 텍스트
        Box(
            modifier = Modifier.size(24.dp).background(if (isSelected) primaryGreen else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else Color.Black
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 아이콘 영역 (식단 계획 & 기록)
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            if (hasPlan) {
                Icon(Icons.Default.Restaurant, contentDescription = "계획", tint = primaryGreen, modifier = Modifier.size(10.dp))
            }
            if (hasPlan && hasRecord) Spacer(modifier = Modifier.width(2.dp))
            if (hasRecord) {
                // 냄비 모양 대신 간단한 갈색 아이콘
                Icon(Icons.Default.EmojiFoodBeverage, contentDescription = "기록", tint = Color(0xFF8D6E63), modifier = Modifier.size(10.dp))
            }
            // 둘 다 없으면 빈 점으로 자리 차지 (레이아웃 유지)
            if (!hasPlan && !hasRecord) {
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.LightGray))
            }
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
fun BottomNavigationBar() {
    NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
        NavigationBarItem(icon = { Icon(Icons.Default.Home, contentDescription = "홈") }, label = { Text("홈") }, selected = true, onClick = { })
        NavigationBarItem(icon = { Icon(Icons.Default.FormatListBulleted, contentDescription = "식단") }, label = { Text("식단") }, selected = false, onClick = { })
        NavigationBarItem(icon = { }, label = { }, selected = false, onClick = { }, enabled = false)
        NavigationBarItem(icon = { Icon(Icons.Default.BarChart, contentDescription = "통계") }, label = { Text("통계") }, selected = false, onClick = { })
        NavigationBarItem(icon = { Icon(Icons.Default.Person, contentDescription = "MY") }, label = { Text("MY") }, selected = false, onClick = { })
    }
}

// ==========================================
// 3. 수정된 화면: 식단표 생성 3단계 (식단 확인 - 하단 버튼 고정)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GenerateStep3Screen(
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    onRegenerateClick: () -> Unit,
    onChangeAgentClick: () -> Unit
) {
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
        // 하단 액션 버튼 영역을 Scaffold의 bottomBar에 고정하여 스크롤에 관계없이 항상 보이도록 수정
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(backgroundColor)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp, top = 8.dp)
            ) {
                // [★ 4단계 이동 버튼] 이 버튼을 누르면 4단계로 넘어갑니다!
                Button(
                    onClick = onSaveClick,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("이 식단으로 저장", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 서브 버튼 2개
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onRegenerateClick,
                        border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color.DarkGray),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("다시 생성하기", fontSize = 14.sp)
                    }
                    OutlinedButton(
                        onClick = onChangeAgentClick,
                        border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color.DarkGray),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Icon(Icons.Default.PersonOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("다른 Agent 선택", fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 안내 문구
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("저장된 식단은 언제든 수정하거나 다시 생성할 수 있어요.", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    ) { innerPadding ->
        // 스크롤되는 상단 콘텐츠 영역
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            StepIndicator(currentStep = 3)

            Spacer(modifier = Modifier.height(24.dp))

            // 1. 상단 Agent 요약 카드
            AgentSummaryCard(primaryGreen)

            Spacer(modifier = Modifier.height(32.dp))

            // 2. 타이틀 및 요약 보기 버튼
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("이번 주 식단표", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFEEEEEE))
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("주간 요약 보기", fontSize = 12.sp, color = Color.DarkGray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. 요일별 식단 카드 (가로 스크롤 Pager)
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 7 })
            val days = listOf("월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일")

            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 16.dp
            ) { page ->
                DailyDietCard(
                    dayName = days[page],
                    isCurrentPage = pagerState.currentPage == page,
                    primaryColor = primaryGreen
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Pager 인디케이터 (점)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(7) { iteration ->
                    val color = if (pagerState.currentPage == iteration) primaryGreen else Color(0xFFE0E0E0)
                    Box(modifier = Modifier.padding(4.dp).size(8.dp).clip(CircleShape).background(color))
                }
            }

            Spacer(modifier = Modifier.height(24.dp)) // 버튼 영역과 겹치지 않게 여백 추가
        }
    }
}

// --- 3단계 전용 UI 컴포넌트들 ---

@Composable
fun AgentSummaryCard(primaryColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFF0F0F0)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("선택된 Agent: ", fontSize = 12.sp, color = Color.Gray)
                    Text("균형식 Agent", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = primaryColor)
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(if (isCurrentPage) 1.5.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 요일 뱃지
            Box(
                modifier = Modifier.align(Alignment.CenterHorizontally).background(Color(0xFFF9F9F9), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(dayName, fontWeight = FontWeight.Bold, color = if (isCurrentPage) primaryColor else Color.Gray, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 식사 리스트 (더미 데이터)
            MealRow(mealType = "아침", primaryColor = primaryColor, menu = "밥, 된장국, 계란말이,\n시금치나물")
            Spacer(modifier = Modifier.height(16.dp))
            MealRow(mealType = "점심", primaryColor = primaryColor, menu = "밥, 된장국, 닭가슴살볶음,\n나물무침")
            Spacer(modifier = Modifier.height(16.dp))
            MealRow(mealType = "저녁", primaryColor = primaryColor, menu = "밥, 된장국, 두부조림,\n브로콜리무침")
            Spacer(modifier = Modifier.height(16.dp))
            MealRow(mealType = "간식", primaryColor = primaryColor, menu = "사과, 견과류")

            Spacer(modifier = Modifier.height(24.dp))

            // 총 열량
            Box(
                modifier = Modifier.fillMaxWidth().background(Color(0xFFF9F9F9), RoundedCornerShape(8.dp)).padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
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
        // 음식 이미지 임시 박스
        Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFF5F5F5)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Restaurant, contentDescription = null, tint = Color.LightGray)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(mealType, color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(menu, fontSize = 14.sp, color = Color.DarkGray, lineHeight = 20.sp)
        }
    }
}

// ==========================================
// 4. 새로운 화면: 식단표 생성 4단계 (완료)
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .padding(bottom = 8.dp) // 시스템 네비게이션 바 여백 고려
            ) {
                // 메인으로 가기 버튼
                Button(
                    onClick = onGoMainClick,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("메인으로 가기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                // 식단 수정하기 버튼
                OutlinedButton(
                    onClick = onEditClick,
                    border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color.DarkGray),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("식단 수정하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            StepIndicator(currentStep = 4) // 4단계 완료 마일스톤 (모두 초록색 체크)

            Spacer(modifier = Modifier.height(48.dp))

            // 1. 대형 완료 아이콘 (원형 겹침 디자인)
            Box(contentAlignment = Alignment.Center) {
                // 가장 큰 옅은 원
                Box(modifier = Modifier.size(140.dp).clip(CircleShape).background(Color(0xFFF1F8F1)))
                // 중간 원
                Box(modifier = Modifier.size(110.dp).clip(CircleShape).background(Color(0xFFE3F2E3)))
                // 안쪽 흰색 원 + 그림자
                Box(modifier = Modifier.size(80.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, contentDescription = "완료", tint = primaryGreen, modifier = Modifier.size(48.dp))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 2. 타이틀 텍스트 (부분 색상 적용)


                    Text(
                        text = androidx.compose.ui.text.buildAnnotatedString {
                            append("식단표 ")
                            withStyle(style = SpanStyle(color = primaryGreen)) {
                                append("저장 완료")
                            }
                        },
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )

            Spacer(modifier = Modifier.height(12.dp))
            Text("이번주 식단이 내 식단표에 반영되었어요", fontSize = 15.sp, color = Color.DarkGray)

            Spacer(modifier = Modifier.height(40.dp))

            // 3. Agent 요약 카드
            AgentFinalSummaryCard(primaryGreen)

            Spacer(modifier = Modifier.height(40.dp))

            // 4. 하단 응원 문구
            Text("꾸준한 실천이 건강한 변화를 만듭니다.", fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(4.dp))
            Text("다음주에도 균형 잡힌 식단으로 함께해요!", fontSize = 13.sp, color = primaryGreen, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// --- 4단계 전용 UI 컴포넌트 ---
@Composable
fun AgentFinalSummaryCard(primaryColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFBFBFB)),
        border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            // 왼쪽 3D 캐릭터 프로필 자리
            Box(
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEAEAEA)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.width(20.dp))

            // 오른쪽 정보 영역
            Column(modifier = Modifier.weight(1f)) {
                // 배지
                Box(modifier = Modifier.background(Color(0xFFE8F5E9), RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Eco, contentDescription = null, tint = primaryColor, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("균형식 Agent 추천", color = primaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 캘린더 아이콘 + 기간
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("이번주 월요일부터 금요일까지 적용", fontSize = 12.sp, color = Color.DarkGray)
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // 식기 아이콘 + 저장 일수
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Restaurant, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("5일 식단 저장됨", fontSize = 12.sp, color = Color.DarkGray)
                }
            }
        }
    }
}