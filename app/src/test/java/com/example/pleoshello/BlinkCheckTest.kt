package com.example.pleoshello

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// 눈 깜빡임 확인: 감았다가 뜬 순서일 때만 통과
class BlinkCheckTest {
    @Test
    fun openOnlyNeverPasses() {
        val b = BlinkCheck()
        assertFalse(b.feed(0.95f))
        assertFalse(b.feed(0.9f))
    }

    @Test
    fun closedThenOpenPasses() {
        val b = BlinkCheck()
        assertFalse(b.feed(0.9f))
        assertFalse(b.feed(0.1f))
        assertFalse(b.feed(0.5f))  // 반쯤 뜬 상태는 아직
        assertTrue(b.feed(0.85f))
    }

    @Test
    fun resetForgetsClosedEyes() {
        val b = BlinkCheck()
        b.feed(0.1f)
        b.reset()
        assertFalse(b.feed(0.9f))
    }
}
