package com.example.menu_recipe_app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    // 1. 프로필 저장 및 수정 (id가 항상 1이므로 덮어쓰기가 됨)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserProfile(profile: UserProfileEntity)

    // 2. 내 프로필 정보 가져오기 (데이터가 아직 없으면 null 반환)
    @Query("SELECT * FROM user_profile_table WHERE id = 1")
    fun getUserProfile(): Flow<UserProfileEntity?>
}