package io.legado.app.service

import android.app.PendingIntent
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.ReadBook
import io.legado.app.utils.LogUtils
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * GSV TTS 朗读服务
 */
class GsvTTSReadAloudService : BaseReadAloudService() {

    private var speakJob: Coroutine<*>? = null
    private val TAG = "GsvTTSReadAloudService"

    override fun onCreate() {
        super.onCreate()
        LogUtils.d(TAG, "GsvTTSReadAloudService created")
    }

    override fun onDestroy() {
        super.onDestroy()
        speakJob?.cancel()
        LogUtils.d(TAG, "GsvTTSReadAloudService destroyed")
    }

    @Synchronized
    override fun play() {
        if (!requestFocus()) return
        if (contentList.isEmpty()) {
            AppLog.putDebug("朗读列表为空")
            ReadBook.readAloud()
            return
        }
        super.play()
        speakJob?.cancel()
        speakJob = execute {
            LogUtils.d(TAG, "开始GSV朗读，列表大小: ${contentList.size}")
            val contentList = contentList
            for (i in nowSpeak until contentList.size) {
                ensureActive()
                var text = contentList[i]
                if (paragraphStartPos > 0 && i == nowSpeak) {
                    text = text.substring(paragraphStartPos)
                }
                if (text.matches(AppPattern.notReadAloudRegex)) {
                    continue
                }
                
                // TODO: 在这里实现GSV TTS的具体播放逻辑
                // 这里只是一个示例实现，您需要根据实际的GSV TTS API进行调整
                LogUtils.d(TAG, "GSV朗读文本: $text")
                
                // 模拟朗读时间（实际实现时应该根据GSV TTS的回调来处理）
                val speakDuration = text.length * 100L // 假设每个字符100ms
                delay(speakDuration)
                
                // 更新进度
                readAloudNumber += contentList[nowSpeak].length + 1 - paragraphStartPos
                paragraphStartPos = 0
                upTtsProgress(readAloudNumber + 1)
                
                // 检查是否需要翻页
                textChapter?.let { chapter ->
                    if (pageIndex + 1 < chapter.pageSize
                        && readAloudNumber >= chapter.getReadLength(pageIndex + 1)
                    ) {
                        pageIndex++
                        ReadBook.moveToNextPage()
                    }
                }
                
                if (nowSpeak < contentList.lastIndex) {
                    nowSpeak++
                } else {
                    // 朗读完成，跳转到下一章
                    nextChapter()
                    break
                }
            }
        }.onError {
            AppLog.put("GSV朗读出错\n${it.localizedMessage}", it, true)
            toastOnUi("GSV朗读出错: ${it.localizedMessage}")
        }
    }

    override fun playStop() {
        speakJob?.cancel()
        LogUtils.d(TAG, "GSV朗读停止")
    }

    override fun upSpeechRate(reset: Boolean) {
        // TODO: 实现GSV TTS的语速调整
        LogUtils.d(TAG, "GSV语速调整, reset: $reset")
        if (reset) {
            // 重置语速到默认值
        } else {
            // 根据配置调整语速
        }
    }

    override fun pauseReadAloud(abandonFocus: Boolean) {
        super.pauseReadAloud(abandonFocus)
        speakJob?.cancel()
        LogUtils.d(TAG, "GSV朗读暂停")
    }

    override fun resumeReadAloud() {
        super.resumeReadAloud()
        if (pageChanged) {
            play()
        } else {
            // TODO: 实现GSV TTS的恢复播放逻辑
            LogUtils.d(TAG, "GSV朗读恢复")
        }
    }

    override fun aloudServicePendingIntent(actionStr: String): PendingIntent? {
        return servicePendingIntent<GsvTTSReadAloudService>(actionStr)
    }
}