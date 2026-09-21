package com.sinelynx.grindingrobot.core.util

import android.media.AudioManager
import android.media.ToneGenerator

object AudioUtils {
    // 初始化：设置音频通道为系统警报或媒体音量，音量设为最大
    val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)

    /**
     * 1. 缓慢滴滴（到达目标面）
     * 配合 Handler 或协程，每隔约 500ms 调用一次
     */
    fun playTargetReachedBeep() {
        // TONE_PROP_BEEP 是系统自带的标准滴声，响 200 毫秒
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
    }

    /**
     * 2. 急速滴滴（超挖警告）
     * 配合 Handler 或协程，每隔约 150ms 快速调用一次
     */
    fun playOverDiggingBeep() {
        // 可以选择更刺耳的警报音类型，响 100 毫秒
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 100)
    }

    // 停止发声并释放资源
    fun stopBeep() {
        toneGenerator.stopTone()
    }
}