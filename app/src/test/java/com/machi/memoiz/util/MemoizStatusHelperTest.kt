package com.machi.memoiz.util

import com.machi.memoiz.data.datastore.MemoizStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoizStatusHelperTest {

    // Debug-mode tests (simulate BuildConfig.DEBUG = true)
    @Test
    fun debug_kindness_above_highThreshold_is_last() {
        val s = MemoizStatus(exp = 0, kindness = 3, coolness = 0, smartness = 0, curiosity = 0)
        val label = MemoizStatusHelper.computeStatusLabel(s, debugOverride = true)
        assertEquals("kindness_last", label)
    }

    @Test
    fun debug_neutral_by_exp_is_last() {
        val s = MemoizStatus(exp = 6, kindness = 0, coolness = 0, smartness = 0, curiosity = 0)
        val label = MemoizStatusHelper.computeStatusLabel(s, debugOverride = true)
        assertEquals("neutral_last", label)
    }

    @Test
    fun debug_kindness_equal_paramThreshold_not_last_unless_exp() {
        val s = MemoizStatus(exp = 0, kindness = 1, coolness = 0, smartness = 0, curiosity = 0)
        val label = MemoizStatusHelper.computeStatusLabel(s, debugOverride = true)
        // paramThreshold == 1 and exp < 5, should not be _last
        assertEquals("kindness", label)
    }

    @Test
    fun debug_kindness_boundary_param_and_exp_makes_last() {
        val s = MemoizStatus(exp = 5, kindness = 2, coolness = 0, smartness = 0, curiosity = 0)
        val label = MemoizStatusHelper.computeStatusLabel(s, debugOverride = true)
        // kindness == highLastThreshold? highLastThreshold == 2; rule is > highLastThreshold OR (>= paramThreshold and exp>=expLastThreshold)
        // here kindness >= paramThreshold (true) and exp >= 5 (true) so should be _last
        assertEquals("kindness_last", label)
    }

    // Release-mode tests (simulate BuildConfig.DEBUG = false)
    @Test
    fun release_kindness_above_30_is_last() {
        val s = MemoizStatus(exp = 0, kindness = 31, coolness = 0, smartness = 0, curiosity = 0)
        val label = MemoizStatusHelper.computeStatusLabel(s, debugOverride = false)
        assertEquals("kindness_last", label)
    }

    @Test
    fun release_neutral_by_exp_is_last() {
        val s = MemoizStatus(exp = 50, kindness = 0, coolness = 0, smartness = 0, curiosity = 0)
        val label = MemoizStatusHelper.computeStatusLabel(s, debugOverride = false)
        assertEquals("neutral_last", label)
    }

    @Test
    fun release_kindness_param_ge_15_and_exp_50_is_last() {
        val s = MemoizStatus(exp = 50, kindness = 15, coolness = 0, smartness = 0, curiosity = 0)
        val label = MemoizStatusHelper.computeStatusLabel(s, debugOverride = false)
        assertEquals("kindness_last", label)
    }

    @Test
    fun release_kindness_param_15_exp_49_not_last() {
        val s = MemoizStatus(exp = 49, kindness = 15, coolness = 0, smartness = 0, curiosity = 0)
        val label = MemoizStatusHelper.computeStatusLabel(s, debugOverride = false)
        assertEquals("kindness", label)
    }
}

