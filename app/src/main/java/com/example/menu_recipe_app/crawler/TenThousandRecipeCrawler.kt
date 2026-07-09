package com.example.menu_recipe_app.crawler

import com.example.menu_recipe_app.db.RecipeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * [B 담당 핵심 모듈] 만개의레시피(10000recipe.com) 크롤러
 *
 * 동작 흐름:
 *   1. 검색: /recipe/list.html?q={음식이름}&order=reco (추천순)
 *   2. 첫 번째 레시피 상세 페이지 URL 획득
 *   3. 제목/이미지URL/재료/조리순서 파싱 + 이름 기반 카테고리 추론
 *
 * 파싱 전략: JSON-LD 우선, CSS 셀렉터 폴백 (2중 안전장치)
 */
class TenThousandRecipeCrawler {

    companion object {
        private const val BASE_URL = "https://www.10000recipe.com"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val TIMEOUT_MS = 10_000
    }

    /**
     * 음식 이름 키워드로 식약처 분류 체계(밥/국&찌개/반찬/일품/후식/기타)에 맞춰 추론
     * 크롤링 레시피는 카테고리 정보가 없어서 이름 기반으로 최선 추정
     * (규칙 순서 중요: "김치볶음밥"이 반찬(볶음)이 아닌 밥으로 분류되도록 배치)
     */
    private fun inferCategory(menuName: String): String {
        val n = menuName.replace(" ", "")
        return when {
            listOf("찌개", "국", "탕", "전골", "스프", "수프").any { n.contains(it) } -> "국&찌개"
            listOf("밥", "죽", "리소토", "필라프").any { n.contains(it) } -> "밥"
            listOf("케이크", "쿠키", "빵", "디저트", "젤리", "푸딩", "마카롱", "타르트",
                "머핀", "스콘", "아이스크림", "음료", "주스", "라떼", "차").any { n.contains(it) } -> "후식"
            listOf("면", "국수", "파스타", "스파게티", "라면", "우동", "냉면", "짜장", "짬뽕",
                "떡볶이", "피자", "버거", "샌드위치", "토스트", "카레",
                "스테이크", "돈까스", "돈가스", "마라").any { n.contains(it) } -> "일품"
            listOf("볶음", "무침", "조림", "구이", "전", "튀김", "나물", "김치", "장아찌",
                "젓갈", "샐러드", "말이", "찜").any { n.contains(it) } -> "반찬"
            else -> "기타"
        }
    }

    /**
     * 음식 이름으로 검색 → 첫 번째(추천순) 레시피를 RecipeEntity로 반환
     * 실패 시 Result.failure (앱이 튕기지 않도록 예외를 밖으로 던지지 않음)
     */
    suspend fun crawlRecipe(menuName: String): Result<RecipeEntity> = withContext(Dispatchers.IO) {
        runCatching {
            // ── 1단계: 검색 결과 페이지에서 첫 번째 레시피 링크 찾기 ──
            val searchUrl = "$BASE_URL/recipe/list.html?q=${java.net.URLEncoder.encode(menuName, "UTF-8")}&order=reco"
            val searchDoc = Jsoup.connect(searchUrl)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get()

            val firstLink = searchDoc.selectFirst("a.common_sp_link")?.attr("href")
                ?: searchDoc.select("a[href]").map { it.attr("href") }
                    .firstOrNull { it.matches(Regex(".*/recipe/\\d+.*")) }
                ?: throw NoSuchElementException("'$menuName' 검색 결과가 없습니다.")

            val detailUrl = if (firstLink.startsWith("http")) firstLink else BASE_URL + firstLink

            // ── 2단계: 상세 페이지 파싱 ──
            val detailDoc = Jsoup.connect(detailUrl)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get()

            parseWithJsonLd(detailDoc, menuName)   // 1순위: JSON-LD
                ?: parseWithSelectors(detailDoc, menuName)  // 2순위: CSS 셀렉터
                ?: throw IllegalStateException("레시피 파싱 실패: $detailUrl")
        }
    }

    // ==========================================
    // 1순위 파싱: JSON-LD (schema.org Recipe)
    // ==========================================
    private fun parseWithJsonLd(doc: Document, fallbackName: String): RecipeEntity? {
        return try {
            val script = doc.select("script[type=application/ld+json]")
                .map { it.data() }
                .firstOrNull { it.contains("\"Recipe\"") } ?: return null

            val json = JSONObject(script)

            val name = json.optString("name").ifBlank { fallbackName }

            // image는 문자열 또는 배열 두 형태 모두 대응
            val imageUrl: String? = when (val img = json.opt("image")) {
                is JSONArray -> if (img.length() > 0) img.optString(0) else null
                is String -> img.ifBlank { null }
                else -> null
            }

            // 재료: ["돼지고기 600g", "양파 1/2개", ...]
            val ingredients = json.optJSONArray("recipeIngredient")
                ?.let { arr -> (0 until arr.length()).map { arr.optString(it) } }
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            // 조리순서: [{ "@type": "HowToStep", "text": "..." }, ...]
            val steps = json.optJSONArray("recipeInstructions")
                ?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        when (val item = arr.opt(i)) {
                            is JSONObject -> item.optString("text").ifBlank { null }
                            is String -> item.ifBlank { null }
                            else -> null
                        }
                    }
                } ?: emptyList()

            if (ingredients.isEmpty() && steps.isEmpty()) return null

            RecipeEntity(
                menuName = name,
                // ★ 팀 Entity 스펙(String)에 맞춰 줄바꿈으로 합침
                ingredients = ingredients.joinToString("\n"),
                instructions = steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n"),
                imageUrl = imageUrl,
                category = inferCategory(name)   // ★ 이름 기반 카테고리 추론
            )
        } catch (e: Exception) {
            null // JSON-LD 실패 시 조용히 폴백으로
        }
    }

    // ==========================================
    // 2순위 파싱: CSS 셀렉터 (JSON-LD가 없거나 깨졌을 때)
    // ==========================================
    private fun parseWithSelectors(doc: Document, fallbackName: String): RecipeEntity? {
        return try {
            val name = doc.selectFirst("div.view2_summary h3")?.text()?.ifBlank { null }
                ?: fallbackName

            val imageUrl = doc.selectFirst("img#main_thumbs")?.attr("src")
                ?.ifBlank { null }

            // 재료 영역: div.ready_ingre3 안의 li들 (이름 + 용량)
            val ingredients = doc.select("div.ready_ingre3 ul li").mapNotNull { li ->
                val ingName = li.selectFirst(".ingre_list_name a")?.text()
                    ?: li.selectFirst("a")?.text()
                val amount = li.selectFirst("span.ingre_list_ea")?.text() ?: ""
                when {
                    ingName.isNullOrBlank() -> li.text().ifBlank { null } // 최후의 폴백
                    else -> "$ingName $amount".trim()
                }
            }.distinct()

            // 조리순서 영역: div.view_step_cont 각각의 media-body 텍스트
            val steps = doc.select("div.view_step_cont .media-body")
                .map { it.text() }
                .filter { it.isNotBlank() }

            if (ingredients.isEmpty() && steps.isEmpty()) return null

            RecipeEntity(
                menuName = name,
                ingredients = ingredients.joinToString("\n"),
                instructions = steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n"),
                imageUrl = imageUrl,
                category = inferCategory(name)   // ★ 이름 기반 카테고리 추론
            )
        } catch (e: Exception) {
            null
        }
    }
}