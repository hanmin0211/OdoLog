package com.example.pleoshello

import org.junit.Assert.assertEquals
import org.junit.Test

// 두벌식 조합 확인: 자모를 입력 순서대로 넣었을 때 완성되는 글자
class HangulTest {
    private fun c(s: String) = composeHangul(s.toList())

    @Test fun basic() {
        assertEquals("강지민", c("ㄱㅏㅇㅈㅣㅁㅣㄴ"))
        assertEquals("총무팀", c("ㅊㅗㅇㅁㅜㅌㅣㅁ"))
    }

    @Test fun finalMovesToNextSyllable() {
        assertEquals("가나", c("ㄱㅏㄴㅏ"))       // ㄴ 이 받침이 아니라 다음 글자 첫소리
        assertEquals("갃", c("ㄱㅏㄱㅅ"))        // 겹받침
        assertEquals("각사", c("ㄱㅏㄱㅅㅏ"))     // 겹받침이 나뉨
    }

    @Test fun doubleVowelsAndConsonants() {
        assertEquals("과", c("ㄱㅗㅏ"))
        assertEquals("의", c("ㅇㅡㅣ"))
        assertEquals("쏘나타", c("ㅆㅗㄴㅏㅌㅏ"))
        assertEquals("까", c("ㄲㅏ"))
    }

    // PC 키보드 알파벳 순서 그대로 쳤을 때 (두벌식 자판 위치)
    @Test fun keyboardLayout() {
        fun type(keys: String) = composeHangul(keys.map { jamoForKey(it.lowercaseChar(), it.isUpperCase()) })
        assertEquals("강지민", type("rkdwlals"))
        assertEquals("쏘나타", type("Thskxk"))
        assertEquals("영업팀", type("duddjqxla"))
        assertEquals("ㅁ", type("a"))
    }

    @Test fun incomplete() {
        assertEquals("ㄱ", c("ㄱ"))
        assertEquals("ㅏ", c("ㅏ"))
        assertEquals("갇ㄸ", c("ㄱㅏㄷㄸ"))      // ㄸ 은 받침이 될 수 없음
        assertEquals("영업팀", c("ㅇㅕㅇㅇㅓㅂㅌㅣㅁ"))
    }
}
