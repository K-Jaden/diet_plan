package com.example.menu_recipe_app // 본인 패키지명으로 꼭 확인하세요!

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    // 배경색을 이미지처럼 아주 연한 베이지/그레이 톤으로 설정
    val backgroundColor = Color(0xFFFCFCFA)
    val primaryGreen = Color(0xFF5A8754)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = backgroundColor,
        // 정중앙 하단에 떠있는 '생성' 버튼 (FAB)
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* TODO: 식단 생성 화면으로 이동 */ },
                shape = CircleShape,
                containerColor = primaryGreen,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "생성")
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        // 하단 네비게이션 바
        bottomBar = {
            BottomNavigationBar(primaryGreen)
        }
    ) { innerPadding ->
        // 메인 콘텐츠 영역 (스크롤 가능)
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            TopHeaderSection(primaryGreen)
            Spacer(modifier = Modifier.height(24.dp))

            WeeklyGenerateCard(primaryGreen)
            Spacer(modifier = Modifier.height(24.dp))

            CalendarCardPlaceholder()
            Spacer(modifier = Modifier.height(24.dp))

            IngredientsCard()
            Spacer(modifier = Modifier.height(32.dp)) // 하단 바에 가려지지 않게 여백 추가
        }
    }
}

@Composable
fun TopHeaderSection(primaryColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Eco, contentDescription = "로고", tint = primaryColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "식단관리", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Text(text = "건강한 하루, 균형 잡힌 식단 💚", fontSize = 14.sp, color = Color.Gray)
        }
        Row {
            IconButton(onClick = { /* 알림 */ }) {
                Icon(Icons.Default.Notifications, contentDescription = "알림")
            }
            IconButton(onClick = { /* 마이페이지 */ }) {
                Icon(Icons.Default.AccountCircle, contentDescription = "프로필", tint = primaryColor)
            }
        }
    }
}

@Composable
fun WeeklyGenerateCard(primaryColor: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "이번주 식단표",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(24.dp))

            // 일러스트 들어갈 자리 (임시 아이콘)
            Icon(
                Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.LightGray
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "이번주 식단을 생성해보세요!", fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { /* TODO */ },
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
fun CalendarCardPlaceholder() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = Color.Gray)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("캘린더", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Text("<   2026년 6월   >", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // 캘린더 그리드 임시 구현 (실제로는 LazyVerticalGrid 등을 사용)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color(0xFFF9F9F9), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("여기에 캘린더 라이브러리 연동 예정", color = Color.Gray)
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 카테고리 박스 4개 (임시)
            IngredientItem("채소", "9가지", Color(0xFFE8F5E9))
            IngredientItem("단백질", "6가지", Color(0xFFFFF3E0))
            IngredientItem("곡류", "5가지", Color(0xFFEFEBE9))
            IngredientItem("기타", "7가지", Color(0xFFF3E5F5))
        }
    }
}

@Composable
fun RowScope.IngredientItem(title: String, count: String, bgColor: Color) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(count, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun BottomNavigationBar(primaryColor: Color) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "홈") },
            label = { Text("홈") },
            selected = true,
            onClick = { }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.List, contentDescription = "식단") },
            label = { Text("식단") },
            selected = false,
            onClick = { }
        )
        // 가운데 '생성' 버튼 자리를 비워두기 위한 투명 아이템
        NavigationBarItem(
            icon = { },
            label = { },
            selected = false,
            onClick = { },
            enabled = false
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.BarChart, contentDescription = "통계") },
            label = { Text("통계") },
            selected = false,
            onClick = { }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Person, contentDescription = "MY") },
            label = { Text("MY") },
            selected = false,
            onClick = { }
        )
    }
}