package com.example.pleoshello

import android.content.Context

// 최초 설정에서 입력하는 사업자·차량·사용자 정보 (운행기록부 머리말과 부서·성명 칸에 들어감)
data class Profile(
    val company: String = "",      // 상호명
    val bizNo: String = "",        // 사업자등록번호 (000-00-00000)
    val carModel: String = "",     // 차종
    val carNo: String = "",        // 자동차등록번호
    val dept: String = "",         // 부서 (개인사업자는 비워도 됨)
    val name: String = "",         // 성명
    val consent: Boolean = false,  // 차량 데이터 이용 동의
    val fyStartMonth: Int = 1,     // 사업연도 시작 월 (법인: 정관의 사업연도, 개인사업자: 1월)
)

// 앱 전용 설정 저장소에 보관 (차량의 운전자 프로필마다 따로 저장됨)
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)

    fun load() = Profile(
        company = prefs.getString("company", "") ?: "",
        bizNo = prefs.getString("bizNo", "") ?: "",
        carModel = prefs.getString("carModel", "") ?: "",
        carNo = prefs.getString("carNo", "") ?: "",
        dept = prefs.getString("dept", "") ?: "",
        name = prefs.getString("name", "") ?: "",
        consent = prefs.getBoolean("consent", false),
        fyStartMonth = prefs.getInt("fyStartMonth", 1).coerceIn(1, 12),
    )

    fun save(p: Profile) {
        prefs.edit()
            .putString("company", p.company)
            .putString("bizNo", p.bizNo)
            .putString("carModel", p.carModel)
            .putString("carNo", p.carNo)
            .putString("dept", p.dept)
            .putString("name", p.name)
            .putBoolean("consent", p.consent)
            .putInt("fyStartMonth", p.fyStartMonth)
            .apply()
    }
}

// 사업자등록번호 검증: 숫자 10자리 + 마지막 자리(검증번호) 계산이 맞는지
fun isValidBizNo(input: String): Boolean {
    val d = input.filter { it.isDigit() }.map { it - '0' }
    if (d.size != 10) return false
    val w = intArrayOf(1, 3, 7, 1, 3, 7, 1, 3, 5)
    var sum = 0
    for (i in 0 until 9) sum += d[i] * w[i]
    sum += d[8] * 5 / 10
    return (10 - sum % 10) % 10 == d[9]
}

// "1234567891" → "123-45-67891"
fun formatBizNo(input: String): String {
    val d = input.filter { it.isDigit() }
    return if (d.length == 10) "${d.take(3)}-${d.substring(3, 5)}-${d.drop(5)}" else input
}

// 자동차등록번호 형식: "12가3456", "123가4567", "서울12가3456" (띄어쓰기 허용)
private val PLATE = Regex("^([가-힣]{2})?\\d{2,3}[가-힣]\\d{4}$")

fun isValidCarNo(input: String): Boolean = PLATE.matches(input.replace(" ", ""))
