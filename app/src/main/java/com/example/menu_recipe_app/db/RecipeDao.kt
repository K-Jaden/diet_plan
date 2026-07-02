package com.example.menu_recipe_app.db
// ★ 주의: 기존 파일은 package가 "com.example.menu_recipe_app.dbimport"로 되어 있었음 (오타)
//   → "db"로 수정했으니 AppDatabase.kt의 import문도 아래처럼 바꿔야 함:
//   import com.example.menu_recipe_app.dbimport.RecipeDao  (X, 삭제)
//   → db 패키지 안에 같이 있으므로 import 자체가 필요 없어짐

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    // ===== 기존 메서드 (다른 팀원이 쓰고 있을 수 있으므로 그대로 유지) =====

    // 1. 레시피 저장 (이미 같은 ID가 있다면 덮어쓰기)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: RecipeEntity)

    // 2. 이름으로 특정 레시피 하나만 찾기 → 캐싱 로직의 "DB Hit 확인" 단계에서 사용
    @Query("SELECT * FROM recipe_table WHERE menuName = :name LIMIT 1")
    suspend fun getRecipeByName(name: String): RecipeEntity?

    // 3. 저장된 모든 레시피 다 가져오기
    @Query("SELECT * FROM recipe_table ORDER BY id DESC")
    suspend fun getAllRecipes(): List<RecipeEntity>

    // 4. 레시피 삭제하기
    @Query("DELETE FROM recipe_table WHERE id = :recipeId")
    suspend fun deleteRecipe(recipeId: Int)

    // ===== [B 담당 추가] 레시피 탭용 Flow 쿼리 =====
    // Flow를 쓰면 DB에 새 레시피가 저장되는 순간 그리드 화면이 자동으로 갱신됨 (관찰형 UI)

    /** 레시피 그리드: 전체 목록을 실시간 관찰 */
    @Query("SELECT * FROM recipe_table ORDER BY id DESC")
    fun observeAllRecipes(): Flow<List<RecipeEntity>>

    /** 실시간 텍스트 검색: 음식 이름 부분 일치 */
    @Query("SELECT * FROM recipe_table WHERE menuName LIKE '%' || :query || '%' ORDER BY menuName")
    fun searchRecipes(query: String): Flow<List<RecipeEntity>>
}
