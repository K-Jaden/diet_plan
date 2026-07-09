# 🥗 식단관리 앱 (Diet Plan & AI Recipe App)

AI 기반 맞춤형 주간 식단 생성 및 공공데이터 포털(식품안전나라) 레시피 연동 안드로이드 앱입니다. 냉장고 속 남은 재료나 선호/기피하는 식재료를 바탕으로 AI 에이전트가 1주일치 식단을 자동으로 구성해 주고, 그에 맞는 실제 레시피를 연결하여 사용자에게 제공합니다.

---

## 🌟 주요 기능 (Features)

### 1. 🤖 AI 에이전트 맞춤형 식단 생성
- **사용자 맞춤 생성**: 사용자가 보유한 재료, 제외하고 싶은 재료(알레르기 등), 목표 칼로리에 맞춰 Gemini 1.5 Flash 기반의 AI 에이전트가 균형 잡힌 주간 식단(7일)을 자동 설계합니다.
- **다양한 페르소나**: 실속 관리, 영양 가득 등 다양한 테마의 AI 에이전트가 특색 있는 식단을 제안합니다.

### 2. 🍲 하이브리드 RAG (Retrieval-Augmented Generation) 
- AI가 제안한 메뉴 이름을 기반으로, `text-embedding-004` 모델을 활용하여 로컬 DB 내 레시피들의 벡터 유사도를 검색해 실제 존재하는 레시피 정보와 매핑합니다.
- 단순한 이름 텍스트 생성에서 끝나지 않고, 실제 요리가 가능한 조리법과 재료 정보를 묶어줍니다.

### 3. 🌐 공공데이터 API 연동 및 Lazy Loading (지연 로딩)
- **식품안전나라 API**: 로컬 DB에 일치하는 레시피가 부족할 경우, 실시간으로 식품안전나라 조기데이터 API를 호출하여 최신 레시피를 가져옵니다.
- **Lazy Loading 아키텍처**: 최초 실행 시 모든 데이터를 받아오지 않고, 유저가 특정 메뉴를 검색하거나 AI가 메뉴를 추천했을 때 DB에 없으면 그때그때 네트워크를 통해 가져와 영구 저장(Room DB)합니다.

### 4. 📅 캘린더 동기화 및 식단 기록
- 캘린더 화면을 통해 이번 달의 식단 계획을 한눈에 파악할 수 있습니다.
- 데이터베이스(MealPlanDao)와 실시간으로 동기화되어 식단이 존재하는 날짜를 달력에 정확하게 표시합니다.
- 중복 생성 방지 기능이 탑재되어 하루에 동일한 식단이 무한정 생성되는 것을 방지합니다.

---

## 🛠 기술 스택 (Tech Stack)

- **Language**: Kotlin 
- **UI Framework**: Jetpack Compose (Material Design 3)
- **Architecture**: MVVM, Repository Pattern
- **Local Database**: Room Database (SQLite)
- **Networking**: Retrofit2, OkHttp3
- **AI & Embedding**: Gemini API (`gemini-1.5-flash`, `text-embedding-004`)
- **Dependency & Build**: Gradle (KTS), KSP (Kotlin Symbol Processing)

---

## 🚀 시작하기 (Getting Started)

### 1. 필수 요구사항
- Android Studio Iguana (또는 최신 버전)
- JDK 17
- Android SDK 34 (Min SDK 26)

### 2. API Key 설정
이 프로젝트를 정상적으로 빌드하고 실행하려면 두 가지 API 키가 필요합니다. 프로젝트 루트의 `local.properties` 파일에 아래와 같이 키를 추가하세요:

```properties
# local.properties (프로젝트 폴더 내부에 생성)
GEMINI_API_KEY=당신의_제미나이_API_키
PUBLIC_DATA_API_KEY=식품안전나라_오픈API_키
```

### 3. 빌드 및 실행
1. 저장소를 클론합니다: `git clone https://github.com/K-Jaden/diet_plan.git`
2. Android Studio에서 프로젝트를 오픈합니다.
3. Gradle Sync가 완료되면, 에뮬레이터나 실기기를 연결하고 **Run App (Shift+F10)** 을 실행합니다.

---

## 🏗 아키텍처 구조 (Architecture)

- `ai/`: Gemini Service 연동, AI 에이전트 프롬프트 및 응답 파싱(DTO) 처리
- `api/`: 공공데이터 포털 Retrofit 인터페이스 및 Client 설정
- `db/`: Room Entity, DAO, Repository (데이터 소스 및 Lazy Loading 로직 포함)
- `MainActivity.kt`: Compose NavHost 기반의 전반적인 UI 라우팅 및 뷰 구성 

---

## 📝 향후 개선 계획 (TODO)
- 사용자의 일일 실제 섭취 기록(Record) 기능 및 그래프 시각화 추가
- 다크모드 완벽 지원
- 서버 연동을 통한 사용자 계정(로그인/회원가입) 영구 백업 기능 도입