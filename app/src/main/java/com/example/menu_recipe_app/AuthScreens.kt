package com.example.menu_recipe_app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.menu_recipe_app.db.AppDatabase
import com.example.menu_recipe_app.db.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController, onLoginSuccess: (UserEntity) -> Unit) {
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val primaryGreen = Color(0xFF5A8754)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("로그인", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로 가기")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("식단 관리의 시작!", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = primaryGreen)
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = userId,
                onValueChange = { userId = it },
                label = { Text("아이디") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("비밀번호") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (userId.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "아이디와 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    coroutineScope.launch(Dispatchers.IO) {
                        val db = AppDatabase.getDatabase(context)
                        val hashedPw = com.example.menu_recipe_app.util.SecurityUtil.hashPassword(password)
                        val user = db.userDao().login(userId, hashedPw)
                        withContext(Dispatchers.Main) {
                            if (user != null) {
                                Toast.makeText(context, "${user.name}님 환영합니다!", Toast.LENGTH_SHORT).show()
                                onLoginSuccess(user)
                            } else {
                                Toast.makeText(context, "아이디 또는 비밀번호가 잘못되었습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("로그인", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))

            Row {
                Text("아직 계정이 없으신가요?", color = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "회원가입",
                    color = primaryGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { navController.navigate("signup") }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(navController: NavController) {
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val primaryGreen = Color(0xFF5A8754)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("회원가입", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로 가기")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("간편 회원가입", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = primaryGreen)
            Text("기본 정보만 입력하고 바로 시작하세요!", fontSize = 14.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = userId,
                onValueChange = { userId = it },
                label = { Text("아이디") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("비밀번호") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("이름 (또는 닉네임)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (userId.isBlank() || password.isBlank() || name.isBlank()) {
                        Toast.makeText(context, "모든 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    coroutineScope.launch(Dispatchers.IO) {
                        val db = AppDatabase.getDatabase(context)
                        val exists = db.userDao().checkIdExists(userId) > 0
                        
                        if (exists) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "이미 존재하는 아이디입니다.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val hashedPw = com.example.menu_recipe_app.util.SecurityUtil.hashPassword(password)
                            val newUser = UserEntity(
                                userId = userId,
                                password = hashedPw,
                                name = name
                            )
                            db.userDao().insertUser(newUser)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "회원가입이 완료되었습니다!", Toast.LENGTH_SHORT).show()
                                navController.popBackStack() // 가입 성공 시 이전 화면(로그인)으로 돌아감
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("가입하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
