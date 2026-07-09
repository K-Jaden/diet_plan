package com.example.menu_recipe_app.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.menu_recipe_app.db.RecipeEntity

/**
 * ★ 기존 MainActivity.kt의 RecipeDetailScreen을 이 파일로 교체
 *
 * 진입 경로 2가지 모두 지원:
 *   1. 메인 화면 식단표에서 음식 클릭 → "recipe_detail/{menuName}"
 *   2. 레시피 탭 그리드에서 클릭   → "recipe_detail/{menuName}"
 *
 * 화면 진입 시 캐싱 로직(loadRecipe)이 자동 작동:
 *   - DB에 있으면: 거의 즉시 표시
 *   - DB에 없으면: "레시피를 가져오는 중" 로딩 → 만개의레시피 크롤링 → DB 저장 → 표시
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    menuName: String,
    onBackClick: () -> Unit
) {
    val backgroundColor = Color(0xFFFCFCFA)
    val primaryGreen = Color(0xFF5A8754)

    val context = LocalContext.current
    val viewModel: RecipeViewModel = viewModel(factory = RecipeViewModel.factory(context))
    val state by viewModel.detailState.collectAsState()

    // 화면 진입 시 1회 캐싱 로직 실행
    LaunchedEffect(menuName) { viewModel.loadRecipe(menuName) }
    DisposableEffect(Unit) { onDispose { viewModel.resetDetailState() } }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBackIosNew, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    val fav = (state as? RecipeDetailUiState.Success)?.recipe?.isFavorite == true
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            if (fav) Icons.Filled.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "즐겨찾기",
                            tint = if (fav) primaryGreen else Color.Unspecified
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        when (val s = state) {
            is RecipeDetailUiState.Idle, RecipeDetailUiState.Loading -> {
                Column(
                    modifier = Modifier.padding(innerPadding).fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = primaryGreen)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "'$menuName' 레시피를 가져오고 있어요...",
                        fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("처음 보는 요리는 잠시 시간이 걸릴 수 있어요", fontSize = 12.sp, color = Color.Gray)
                }
            }

            is RecipeDetailUiState.Error -> {
                Column(
                    modifier = Modifier.padding(innerPadding).fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(s.message, fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onBackClick) { Text("돌아가기", color = Color.Gray) }
                        Button(
                            onClick = { viewModel.loadRecipe(menuName) },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryGreen)
                        ) { Text("다시 시도") }
                    }
                }
            }

            is RecipeDetailUiState.Success -> {
                RecipeDetailContent(
                    recipe = s.recipe,
                    primaryGreen = primaryGreen,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun RecipeDetailContent(
    recipe: RecipeEntity,
    primaryGreen: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        // ── 대표 이미지 (URL 동적 로딩) ──
        if (recipe.imageUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(250.dp).background(Color(0xFFF0F0F0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.RestaurantMenu, contentDescription = null,
                    tint = Color.LightGray, modifier = Modifier.size(80.dp)
                )
            }
        } else {
            AsyncImage(
                model = recipe.imageUrl,
                contentDescription = recipe.menuName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(250.dp)
            )
        }

        Column(modifier = Modifier.padding(20.dp)) {
            Text(recipe.menuName, fontSize = 24.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(32.dp))

            // ── 재료 (Entity의 ingredients: "재료1\n재료2\n..." 형태) ──
            Text("필요한 재료", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEEEEE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val ingredientList = recipe.ingredients.split("\n").filter { it.isNotBlank() }
                    ingredientList.forEachIndexed { index, line ->
                        // "돼지고기 앞다리살 600g" → 마지막 공백 기준으로 이름/용량 분리 시도
                        val lastSpace = line.lastIndexOf(' ')
                        val (name, amount) = if (lastSpace > 0)
                            line.substring(0, lastSpace) to line.substring(lastSpace + 1)
                        else line to ""

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(name, fontSize = 15.sp, color = Color.DarkGray, modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(amount, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                        if (index < ingredientList.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = Color(0xFFF5F5F5)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── 조리 순서 (instructions: "1. ...\n2. ..." 형태) ──
            Text("조리 순서", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            recipe.instructions.split("\n").filter { it.isNotBlank() }.forEach { line ->
                // "1. 재료를 준비해주세요" → 번호와 내용 분리
                val match = Regex("""^(\d+)\.\s*(.*)""").find(line.trim())
                val stepNum = match?.groupValues?.get(1) ?: "•"
                val instruction = match?.groupValues?.get(2) ?: line.trim()

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier.size(24.dp).background(primaryGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stepNum, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        instruction, fontSize = 15.sp, lineHeight = 22.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
