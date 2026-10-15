package com.yinwei.v2v.transcript

import com.yinwei.v2v.speech.*
import org.junit.Assert.*
import org.junit.Test

class SpeechPlanTest {
    @Test fun longTranslationIsLosslessAndEveryPhraseIsShort() {
        val text = "這是用來驗證長篇翻譯可以分成多個短句接續播報的測試內容。".repeat(3) + "😀ABC ending"
        val phrases = SpeechPlan.phrases(text)
        assertEquals(text, phrases.joinToString(""))
        assertTrue(phrases.size > 3)
        assertTrue(phrases.all { it.codePointCount(0, it.length) <= 18 })
        assertTrue(phrases.none { it.firstOrNull()?.isLowSurrogate() == true || it.lastOrNull()?.isHighSurrogate() == true })
    }
    @Test fun preservesNumbersWhitespacePunctuationAndLatinWords() {
        val text = "費用是 1,234.50 元，speaker Alice 說 Hello world！"
        assertEquals(text, SpeechPlan.phrases(text).joinToString(""))
        assertTrue(SpeechPlan.phrases(text).any { "Alice" in it })
    }
    @Test fun automaticRateCanReachTwoButRespectsManualChoice() {
        assertEquals(2f, SpeechPlan.rate(1f, true, "中".repeat(120), 10000, 1, 0), 0f)
        assertEquals(2f, SpeechPlan.rate(1f, true, "短句", 10000, 2, 6000), 0f)
        assertEquals(0.8f, SpeechPlan.rate(0.8f, false, "中".repeat(80), 1000, 3, 10000), 0f)
        assertEquals(2f, SpeechPlan.rate(2f, false, "短句", 10000, 1, 0), 0f)
        assertEquals(1f, SpeechPlan.rate(1f, true, "短句", 10000, 1, 0), 0f)
    }
    @Test fun wholeParagraphLongerThanTenSecondsPlaysInOrderWithinPcmLimit() {
        val queue = SpeechQueue()
        val request = SpeechRequest(1, 0, 2, "中".repeat(66), 10000)
        assertTrue(queue.enqueue(request)); assertEquals(request, queue.next())
        val played = mutableListOf<Short>()
        for ((index, phrase) in SpeechPlan.phrases(request.text).withIndex()) {
            val pcm = ShortArray(phrase.length * 10000) { (index + 1).toShort() }
            while (!queue.append(request.key, pcm)) played.addAll(queue.take(9600).toList())
            assertTrue(queue.bufferedMs() <= 10000)
        }
        queue.complete(request.key)
        while (queue.hasAudio()) played.addAll(queue.take(9600).toList())
        val voiced = played.filter { it != 0.toShort() }
        assertEquals(660000, voiced.size)
        assertTrue(voiced.zipWithNext().all { it.first <= it.second })
        assertEquals(0, queue.pending())
    }
    @Test fun segmentsRemainPendingUntilLastPhraseIsPlayed() {
        val queue = SpeechQueue()
        repeat(3) { assertTrue(queue.enqueue(SpeechRequest(it.toLong(), it.toLong(), 2, "文字", 1000))) }
        assertFalse(queue.enqueue(SpeechRequest(4, 4, 2, "過載", 1000)))
        val request = queue.next()!!
        assertTrue(queue.append(request.key, shortArrayOf(1, 2)))
        queue.take(2)
        assertEquals(3, queue.pending()) // Synthesis can still add more phrases.
        assertTrue(queue.append(request.key, shortArrayOf(3)))
        queue.complete(request.key)
        assertEquals(3, queue.pending())
        assertArrayEquals(shortArrayOf(3), queue.take(1))
        assertEquals(2, queue.pending())
    }
    @Test fun clearRejectsLateAudioAndAllowsNewSessionWork() {
        val queue = SpeechQueue()
        assertTrue(queue.enqueue(SpeechRequest(1, 1, 2, "舊", 1000)))
        queue.next(); queue.append(1, shortArrayOf(7))
        queue.clear()
        assertFalse(queue.append(1, shortArrayOf(8)))
        queue.complete(1)
        assertArrayEquals(shortArrayOf(0), queue.take(1))
        assertTrue(queue.enqueue(SpeechRequest(2, 2, 2, "新", 1000)))
        assertEquals(2L, queue.next()!!.key)
    }
}
