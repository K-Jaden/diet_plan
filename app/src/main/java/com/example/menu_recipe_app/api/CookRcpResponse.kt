package com.example.menu_recipe_app.api

import com.google.gson.annotations.SerializedName

data class CookRcpResponse(
    @SerializedName("COOKRCP01")
    val cookRcp01: CookRcp01?
)

data class CookRcp01(
    @SerializedName("total_count")
    val totalCount: String?,
    @SerializedName("row")
    val row: List<CookRcpRow>?
)

data class CookRcpRow(
    @SerializedName("RCP_SEQ") val rcpSeq: String?,         // 일련번호
    @SerializedName("RCP_NM") val rcpNm: String?,           // 메뉴명
    @SerializedName("RCP_WAY2") val rcpWay2: String?,       // 조리방법
    @SerializedName("RCP_PAT2") val rcpPat2: String?,       // 요리종류
    @SerializedName("INFO_ENG") val infoEng: String?,       // 열량(kcal)
    @SerializedName("RCP_PARTS_DTLS") val rcpPartsDtls: String?, // 재료정보
    @SerializedName("ATT_FILE_NO_MAIN") val attFileNoMain: String?, // 이미지경로(소)
    
    // 조리방법 (최대 20개까지 있지만, 보통 1~10개 사용됨)
    @SerializedName("MANUAL01") val manual01: String?,
    @SerializedName("MANUAL02") val manual02: String?,
    @SerializedName("MANUAL03") val manual03: String?,
    @SerializedName("MANUAL04") val manual04: String?,
    @SerializedName("MANUAL05") val manual05: String?,
    @SerializedName("MANUAL06") val manual06: String?,
    @SerializedName("MANUAL07") val manual07: String?,
    @SerializedName("MANUAL08") val manual08: String?,
    @SerializedName("MANUAL09") val manual09: String?,
    @SerializedName("MANUAL10") val manual10: String?
) {
    // 모든 매뉴얼을 하나로 합치는 헬퍼 함수
    fun getInstructions(): String {
        val manuals = listOfNotNull(
            manual01, manual02, manual03, manual04, manual05,
            manual06, manual07, manual08, manual09, manual10
        )
        return manuals.filter { it.isNotBlank() }
            .joinToString("\n") { it.trim() }
    }
}
