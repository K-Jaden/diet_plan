package com.example.menu_recipe_app.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.menu_recipe_app.db.RecipeEntity

/**
 * ★ 기존 MainActivity.kt의 RecipeScreen을 이 파일로 교체
 *
 * 기존과 달라진 점:
 *   - 하드코딩 리스트(SimpleRecipe + loremflickr) 제거 → Room DB의 실제 레시피 표시
 *   - 검색창이 실제로 동작함 (실시간 검색 + 300ms debounce)
 *   - onNavigateToDetail이 음식 이름(String)을 전달하도록 변경
 *     → NavHost에서 "recipe_detail/{menuName}" 라우트로 수정 필요 (README 참고)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(
    navController: NavController,
    onNavigateToDetail: (menuName: String) -> Unit,
    bottomBar: @Composable () -> Unit  // 기존 BottomNavigationBar(navController, "recipe")를 그대로 넘겨받음
) {
    val backgroundColor = Color(0xFFFCFCFA)
    val primaryGreen = Color(0xFF5A8754)

    val context = LocalContext.current
    val viewModel: RecipeViewModel = viewModel(factory = RecipeViewModel.factory(context))

    val recipes by viewModel.recipes.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val preload by viewModel.preloadState.collectAsState()
    val prefetch by com.example.menu_recipe_app.repository.RecipePrefetcher.state.collectAsState()

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("레시피", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        },
        bottomBar = bottomBar
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // ── [v2] 프리로드 진행/에러 배너 ──
            when (val p = preload) {
                is PreloadUiState.Loading -> {
                    Surface(
                        color = Color(0xFFF4F9F4),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp, color = primaryGreen
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = if (p.total > 0)
                                        "레시피 데이터 준비 중... (${p.loaded} / ${p.total})"
                                    else "레시피 데이터 준비 중...",
                                    fontSize = 13.sp, color = primaryGreen, fontWeight = FontWeight.Medium
                                )
                            }
                            if (p.total > 0) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { p.loaded.toFloat() / p.total },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = primaryGreen, trackColor = Color(0xFFE0EBE0)
                                )
                            }
                        }
                    }
                }
                is PreloadUiState.Error -> {
                    Surface(
                        color = Color(0xFFFFF3F3),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                p.message, fontSize = 12.sp, color = Color(0xFFD32F2F),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.retryPreload() }) {
                                Text("재시도", fontSize = 12.sp, color = Color(0xFFD32F2F))
                            }
                        }
                    }
                }
                else -> Unit
            }
            // ── 식단표 레시피 프리페치 진행 배너 ──
            when (val pf = prefetch) {
                is com.example.menu_recipe_app.repository.PrefetchState.Running -> {
                    Surface(
                        color = Color(0xFFFFF8E1),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp, color = Color(0xFFF57F17)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "식단표 레시피 준비 중... (${pf.done}/${pf.total}) ${pf.current}",
                                fontSize = 12.sp, color = Color(0xFFF57F17)
                            )
                        }
                    }
                }
                else -> Unit
            }
            // ── 실시간 검색창 (기존 디자인 유지, 기능만 연결) ──
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text("어떤 요리를 만들어볼까요?", color = Color.Gray, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "검색", tint = Color.Gray) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "지우기", tint = Color.Gray)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White,
                    unfocusedBorderColor = Color(0xFFEEEEEE),
                    focusedBorderColor = primaryGreen
                ),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (recipes.isEmpty()) {
                // ── 빈 상태 (아직 저장된 레시피가 없거나 검색 결과 없음) ──
                Column(
                    modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.RestaurantMenu, contentDescription = null,
                        tint = Color.LightGray, modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (query.isBlank())
                            "아직 저장된 레시피가 없어요.\n식단표의 음식을 눌러 레시피를 받아보세요!"
                        else "\"$query\" 검색 결과가 없어요.",
                        color = Color.Gray, fontSize = 14.sp,
                        textAlign = TextAlign.Center, lineHeight = 20.sp
                    )
                    if (query.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { onNavigateToDetail(query.trim()) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5A8754))
                        ) {
                            Text("'${query.trim()}' 레시피 가져오기")
                        }
                    }
                }
            } else {
                // ── 레시피 그리드 (기존 3열 원형 디자인 유지) ──
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(recipes, key = { it.id }) { recipe ->
                        RecipeGridItem(
                            recipe = recipe,
                            onClick = { onNavigateToDetail(recipe.menuName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeGridItem(recipe: RecipeEntity, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        if (recipe.imageUrl.isNullOrBlank()) {
            // 이미지 없는 레시피 폴백
            Box(
                modifier = Modifier.size(90.dp).clip(CircleShape).background(Color(0xFFF0F0F0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.RestaurantMenu, contentDescription = null, tint = Color.LightGray)
            }
        } else {
            /**
             * URL만으로 실제 요리 이미지 동적 로딩 (Coil)
             * Coil이 메모리+디스크 캐시를 자동 관리 → 스크롤해도 재다운로드 없음
             */
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(recipe.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = recipe.menuName,
                modifier = Modifier.size(90.dp).clip(CircleShape).background(Color(0xFFF0F0F0)),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            recipe.menuName,
            fontSize = 14.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}
