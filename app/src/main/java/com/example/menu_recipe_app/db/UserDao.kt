package com.example.menu_recipe_app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface UserDao {
    // 회원 가입
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity): Long

    // 로그인 확인 (아이디와 비밀번호로 사용자 찾기)
    @Query("SELECT * FROM user_table WHERE userId = :userId AND password = :password LIMIT 1")
    suspend fun login(userId: String, password: String): UserEntity?

    // 아이디 중복 확인용
    @Query("SELECT COUNT(*) FROM user_table WHERE userId = :userId")
    suspend fun checkIdExists(userId: String): Int

    // 내 정보 조회
    @Query("SELECT * FROM user_table WHERE id = :id")
    suspend fun getUserById(id: Int): UserEntity?

    @Update
    suspend fun updateUser(user: UserEntity)

    // 회원 탈퇴
    @androidx.room.Delete
    suspend fun deleteUser(user: UserEntity)
}
