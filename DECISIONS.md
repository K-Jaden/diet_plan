# 개발 결정 및 문제 해결 기록 (diet_plan)

프로젝트 진행 중 겪은 기술적 난관과 해결 과정을 기록합니다. 새 항목은 최상단에 추가합니다.

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
- develop이 만든 `MealEntity`/`dietViewModel.saveGeneratedMeals` 경로가 AI 생성 플로우와 아직 연결 안 됨 (달력/메인화면 표시용 별도 테이블로 남아있음) — 두 저장 경로를 통합할지는 제품 결정 필요
- `db/RecipeRepository.kt`(구, RAG용)와 `repository/RecipeRepository.kt`(신, recipe-caching)가 같은 이름으로 공존 — 혼동 방지를 위해 리네이밍 검토 권장

---

## 새 항목 추가 형식
```
## YYYY-MM-DD: 제목 (문제 → 해결 한 줄 요약)
**문제**: 어떤 상황에서 무엇이 문제였는지
**시도한 것**: (선택) 시도했지만 안 통했던 방법
**해결**: 최종적으로 어떻게 해결했는지
**결과**: 해결 후 효과
```
