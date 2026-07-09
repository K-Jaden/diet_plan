# 개발 결정 및 문제 해결 기록 (diet_plan)

프로젝트 진행 중 겪은 기술적 난관과 해결 과정을 기록합니다. 새 항목은 최상단에 추가합니다.

---

## 2026-07-09: origin/develop 재머지 - 4단계→5단계 생성 플로우 전환, AI 로직 재배치
**문제**: `origin/develop`에 식단 생성 플로우가 4단계에서 5단계(재료선택→기본설정→영양사선택→식단확인→완료)로 재설계된 커밋이 새로 올라왔는데, `MainActivity.kt` 한 파일에서 12개 충돌이 발생. 단순 텍스트 병합이 불가능한 이유는 develop의 새 Step2/Step3가 UI만 있고 실제 동작이 없는 뼈대였기 때문 — 재료 입력, 끼니 수, 간식 여부, 식단 스타일을 입력받는 화면(새 Step2)과 영양사를 고르는 화면(새 Step3)이 서로 상태를 주고받지 않았고, 실제 `GeminiService`/RAG 검색 호출은 어디에도 연결되어 있지 않았음.

또한 조사 중 기존 코드의 잠재 버그를 하나 발견: `AppNavigation`의 `userIngredients`/`userExcludedIngredients` state가 선언만 되고 어디서도 재할당되지 않아, 실제 AI 생성 호출이 항상 빈 재료 목록을 받고 있었음(화면상으로는 드러나지 않는 조용한 버그).

**시도한 것**: 처음엔 `git merge origin/develop --no-edit`로 바로 충돌 해결을 시도했으나, 4단계 구조를 유지할지 5단계로 갈아탈지는 코드만 봐서는 판단할 수 없는 제품 방향 결정이라 `git merge --abort`로 안전하게 되돌리고 사용자에게 확인.

**해결**: 사용자가 "5단계 채택, AI 로직 재배치"를 선택. develop의 새 Step2(재료/기피재료/끼니설정 UI)·Step3(영양사 카드 UI)·Step5(완료 화면)를 그대로 채택하되, 기존 feat/agents의 Step2에 있던 실제 생성 로직(티켓 차감, `RagRecipeRepository` RAG 검색, `GeminiService().generateMealPlan()` 호출)을 새 Step3로 옮기고, 기존 Step3(리뷰/재생성/저장 로직)는 새 Step4로 이름만 바꿔 그대로 이식. `AppNavigation`에 `userIngredients`/`mealsPerDay`/`includeSnack`/`mealStyle` state를 추가해 새 Step2 → Step3로 실제 값이 흐르도록 배선하면서 위에서 발견한 빈 재료 목록 버그도 함께 해소. 충돌 범위가 방대해(약 1200줄) `Edit` 도구로 개별 처리하는 대신, 충돌 없는 앞/뒤 구간(716줄까지, 1941줄부터)을 안전 경계로 확정하고 그 사이를 통째로 새로 작성한 내용으로 교체하는 방식을 사용. 이 과정에서 develop 쪽에만 있던 죽은 코드(`ChefBriefingCard`/`HintChip`/`ChefBriefing`)와 중복 정의된 `AgentSummaryCard`(develop 버전은 에이전트 이름이 하드코딩되어 있어 우리 쪽 동적 버전을 채택)도 함께 정리.
**결과**: `./gradlew compileDebugKotlin` 1회 시도로 컴파일 성공. 5단계 플로우 전체가 실제 AI 생성 로직과 연결된 상태로 병합 완료.

---

## 2026-07-03 (회고 작성): AI 응답 할루시네이션 → 하이브리드 RAG 도입
**문제**: Gemini 기반 AI 에이전트가 실제로 존재하지 않는 레시피/메뉴를 만들어내는 할루시네이션 발생
**시도한 것**: 프롬프트 제약만으로 막아보려 했으나 근본적으로 해결되지 않음
**해결**: `text-embedding-004`로 로컬 DB 레시피와 벡터 유사도 검색을 붙여, AI가 제안한 메뉴명을 실제 존재하는 레시피에 매핑하는 하이브리드 RAG 구조 도입
**결과**: AI가 지어낸 이름이어도 실제 레시피와 매칭되어 결과 신뢰도 확보

## 2026-07-03 (회고 작성): 토큰 사용량 과다 → 결정론적 로직은 백엔드 처리로 전환
**문제**: 요청마다 LLM 호출이 많아지며 토큰 비용과 응답 지연 증가
**해결**: 중복 생성 방지, 필터링처럼 규칙 기반으로 계산 가능한 부분은 LLM 호출 없이 코드로 직접 구현
**결과**: 불필요한 LLM 호출 감소, 비용/지연 개선

---

## 2026-07-09: develop + feat/recipe-caching를 feat/agents로 3-way 머지 (레시피 파이프라인 이원화 문제)
**문제**: `develop`(다크모드/캘린더/스텝폼 등 UI 작업)과 `feat/recipe-caching`(만개의레시피 크롤러+공공API+Room 캐싱)이 각자 `feat/roomdb`에서 갈라진 뒤, `db/RecipeDao.kt`·`db/RecipeEntity.kt`·`db/AppDatabase.kt`·`MainActivity.kt`·`app/build.gradle.kts`를 서로 다르게 수정해옴. 특히 두 브랜치가 "레시피를 어떻게 가져오고 저장할지"에 대해 완전히 다른 설계(develop: MainActivity 내장 화면 + RecipeApiService 직접호출 / recipe-caching: 별도 `ui/recipe/` 화면 + 크롤러 + 자체 repository)를 갖고 있어 자동 병합이 불가능했음.

**해결**: 사용자 확인 후 우선순위를 `feat/recipe-caching`(레시피 파이프라인) > `develop`(그 외 UI)로 정하고 순서대로 머지:
1. `origin/develop` 머지 — `build.gradle.kts`/`MainActivity.kt`(18개 충돌)/`db/AppDatabase.kt`/`db/RecipeEntity.kt` 충돌을 해결하면서, 두 브랜치가 각자 만든 "AI 생성 식단 저장" 경로(feat/agents: `MealPlanEntity`/`mealPlanDao`, develop: `MealEntity`/`dietViewModel.saveGeneratedMeals` 더미 구현)를 발견 — develop 쪽은 실제로 미완성 스텁(하드코딩된 더미 데이터)이었어서 feat/agents의 실제 구현을 유지하고 콜백 시그니처(`onSaveClick`)를 그에 맞게 정리.
2. `feat/recipe-caching` 머지 — `RecipeEntity.calories` 타입이 develop 머지 때 정한 `Int`와 recipe-caching의 `String`이 다시 충돌. 실제 데이터 소스(`FoodSafetyApiClient`가 `optString`으로 원문 문자열 반환)를 확인하고 `String`으로 최종 확정, feat/agents의 구버전 `RecipeScreen`/`RecipeDetailScreen`(MainActivity 내장)을 삭제하고 recipe-caching의 `ui/recipe/*` 버전으로 교체. `RecipeDao`에 있던 중복 함수 시그니처(`getRecipeById`, `searchRecipes`)도 이번에 정리됨.
3. 두 머지 모두 `./gradlew compileDebugKotlin`(KSP/Room 스키마 검증 포함)으로 실제 컴파일 성공까지 확인 — 1차 시도에서 구버전 `db/RecipeRepository.kt`(RAG 임베딩 시딩용, feat/agents 전용이라 충돌 표시조차 안 됐던 파일)가 `calories: Int`를 가정하고 있어 컴파일 에러 2건 발생, `String` 변환으로 수정.

**결과**: `feat/agents`에 develop의 UI 작업 + recipe-caching의 크롤러 기반 레시피 파이프라인 + 기존 AI 식단 생성(RAG/GeminiService/MealPlanEntity)이 모두 한 브랜치에 공존. 레시피 저장 시 실제 생성된 메뉴로 `RecipePrefetcher`를 트리거하도록 연결해 recipe-caching이 남겨둔 TODO(더미 메뉴 목록)도 함께 해소.

**후속 필요 항목** (이번엔 손대지 않음, 자체 판단으로 남김):
- ~~develop이 만든 `MealEntity`/`dietViewModel.saveGeneratedMeals` 경로가 AI 생성 플로우와 아직 연결 안 됨~~ → 2026-07-09 `MealPlanEntity`로 통합 완료 (아래 항목 참고)
- ~~`db/RecipeRepository.kt`(구, RAG용)와 `repository/RecipeRepository.kt`(신, recipe-caching)가 같은 이름으로 공존~~ → 2026-07-09 `RagRecipeRepository`로 리네이밍 완료 (아래 항목 참고)

---

## 2026-07-09: 메인/캘린더 화면 계획일 표시 불일치 → CalendarCard가 mealPlanDao 직접 조회하도록 수정
**문제**: 메인 화면 임베디드 캘린더와 캘린더 탭이 서로 다른 정보를 보여줌. 원인은 위 3-way 머지에서 `CalendarCard`의 "계획 있음" 점 표시가 `dietViewModel.currentMonthMeals`(develop이 만든 `mealDao`/`MealEntity`)를 근거로 했는데, AI 식단 생성 플로우(`GenerateStep3Screen`)는 `mealPlanDao`/`MealPlanEntity`에만 저장함. `MealEntity` 테이블에 아무도 쓰지 않으니 두 화면 모두 캘린더 점 표시가 항상 비어있었고, 캘린더 탭 아래의 실제 식단 목록(`mealPlanDao` 기준)과 어긋나 보였음.

**해결**: `CalendarCard`가 `dietViewModel` 의존을 끊고 `mealPlanDao.getAllMealPlanDates()`를 직접 조회하도록 변경 (메인 화면의 "오늘 이미 계획 있음" 체크가 쓰던 것과 동일한 소스). `CalendarCard`/`CalendarScreen`에서 더 이상 안 쓰는 `dietViewModel` 파라미터 제거.

**결과**: 메인 화면 캘린더와 캘린더 탭이 같은 쿼리를 보므로 항상 일치. `./gradlew compileDebugKotlin` 통과 확인.

## 2026-07-09: 식단 저장소 이원화(MealEntity vs MealPlanEntity) → MealPlanEntity로 통합
**문제**: `MealEntity`(develop, `mealDao`)와 `MealPlanEntity`(feat/agents, `mealPlanDao`) 두 테이블이 공존. AI 생성 플로우(`GenerateStep3Screen`)는 `MealPlanEntity`에만 저장하고, `MealEntity`는 어디서도 쓰이지 않는 빈 테이블로 남아있었음(위 캘린더 불일치 버그의 근본 원인). 필드를 비교해보니 `MealEntity`(date, mealType, menuName, calories, isEaten)가 `MealPlanEntity`(같은 필드 + agentType, ingredients, recipe, totalDayCalories)의 사실상 부분집합이라 통합이 자연스러웠음.

**해결**: `MealPlanEntity`에 `isEaten`(둘 중 유일하게 없던 필드) 추가. `MealPlanDao`는 기존 suspend 쿼리 외에 `observeMealsByDate`/`observeMealsBetweenDates`(Flow 반환)를 추가해 `MealDao`가 제공하던 "DB 변경 시 자동 갱신" 기능을 대체. `DietViewModel`이 `MealPlanDao`/`MealPlanEntity`를 쓰도록 재작성하고 죽은 코드(`saveGeneratedMeals`)는 삭제. `AppDatabase`에서 `MealEntity` 제거하며 버전 5→6, `MealDao.kt`/`MealEntity.kt` 삭제.

**결과**: 식단 데이터의 단일 진실 공급원이 `MealPlanEntity` 하나로 정리됨. `./gradlew compileDebugKotlin` 통과 확인.

## 2026-07-09: RecipeRepository 이름 중복 → RagRecipeRepository로 리네이밍
**문제**: `db/RecipeRepository.kt`(feat/agents, RAG 임베딩 시딩/검색용)와 `repository/RecipeRepository.kt`(recipe-caching, 레시피 탭 조회용)가 같은 클래스명으로 다른 패키지에 공존해 혼동 소지.
**해결**: 전자를 `RagRecipeRepository`로 리네이밍 (역할이 이름에 드러나도록). `repository/RecipeRepository.kt`는 recipe-caching이 만든 "주" 레시피 조회 경로라 원래 이름 유지.
**결과**: `MainActivity.kt`의 4개 참조(import 1 + 생성자 호출 3) 갱신, 컴파일 확인.

---

## 새 항목 추가 형식
```
## YYYY-MM-DD: 제목 (문제 → 해결 한 줄 요약)
**문제**: 어떤 상황에서 무엇이 문제였는지
**시도한 것**: (선택) 시도했지만 안 통했던 방법
**해결**: 최종적으로 어떻게 해결했는지
**결과**: 해결 후 효과
```
