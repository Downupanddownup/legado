package io.legado.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.manager.ToneVoiceManager
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.exoplayer.InputStreamDataSource
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.newCallResponse
import io.legado.app.model.ReadBook
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.FormBody
import okhttp3.Response
import okhttp3.ResponseBody
import splitties.init.appCtx
import java.io.File
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlin.coroutines.coroutineContext

/**
 * GSV TTS 朗读服务
 */
@SuppressLint("UnsafeOptInUsageError")
class GsvTTSReadAloudService : BaseReadAloudService(), Player.Listener {

    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(this).build()
    }
    private val ttsFolderPath: String by lazy {
        cacheDir.absolutePath + File.separator + "gsvTTS" + File.separator
    }
    private val cache by lazy {
        SimpleCache(
            File(cacheDir, "gsvTTS_cache"),
            LeastRecentlyUsedCacheEvictor(128 * 1024 * 1024),
            StandaloneDatabaseProvider(appCtx)
        )
    }
    private val cacheDataSinkFactory by lazy {
        CacheDataSink.Factory()
            .setCache(cache)
    }
    private val loadErrorHandlingPolicy by lazy {
        CustomLoadErrorHandlingPolicy()
    }
    private var speechRate: Int = AppConfig.speechRatePlay + 5
    private var downloadTask: Coroutine<*>? = null
    private var playIndexJob: Job? = null
    private var downloadErrorNo: Int = 0
    private var playErrorNo = 0
    private val downloadTaskActiveLock = Mutex()
    private val TAG = "GsvTTSReadAloudService"

    override fun onCreate() {
        super.onCreate()
        exoPlayer.addListener(this)
        LogUtils.d(TAG, "GsvTTSReadAloudService created")
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadTask?.cancel()
        exoPlayer.release()
        runCatching {
            cache.release()
        }
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
        downloadAndPlayAudios()
    }

    override fun playStop() {
        exoPlayer.stop()
    }

    private fun updateNextPos() {
        readAloudNumber += contentList[nowSpeak].length + 1 - paragraphStartPos
        paragraphStartPos = 0
        nowSpeak++
        if (nowSpeak >= contentList.size) {
            nextChapter()
        }
    }

    private fun downloadAndPlayAudios() {
        exoPlayer.clearMediaItems()
        downloadTask?.cancel()
        downloadTask = execute {
            downloadTaskActiveLock.withLock {
                ensureActive()
                contentList.forEachIndexed { index, content ->
                    ensureActive()
                    if (index < nowSpeak) return@forEachIndexed
                    var text = content
                    if (paragraphStartPos > 0 && index == nowSpeak) {
                        text = text.substring(paragraphStartPos)
                    }
                    val fileName = md5SpeakFileName(text)
                    val speakText = text.replace(AppPattern.notReadAloudRegex, "")
                    if (speakText.isEmpty()) {
                        AppLog.put("阅读段落内容为空，使用无声音频代替。\n朗读文本：$text")
                        createSilentSound(fileName)
                    } else if (!hasSpeakFile(fileName)) {
                        runCatching {
                            val inputStream = getSpeakStream(speakText)
                            if (inputStream != null) {
                                createSpeakFile(fileName, inputStream)
                            } else {
                                createSilentSound(fileName)
                            }
                        }.onFailure {
                            AppLog.put("GSV TTS下载音频出错，使用无声音频代替。\n朗读文本：$speakText", it)
                            createSilentSound(fileName)
                        }
                    }
                    val mediaSource = createMediaSource(fileName)
                    launch(Main) {
                        exoPlayer.addMediaSource(mediaSource)
                    }
                }
                launch(Main) {
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
            }
        }.onError {
            AppLog.put("GSV朗读下载出错\n${it.localizedMessage}", it, true)
        }
    }

    private fun createMediaSource(fileName: String): MediaSource {
        val factory = DataSource.Factory {
            InputStreamDataSource {
                getSpeakFileAsMd5(fileName).inputStream()
            }
        }
        return DefaultMediaSourceFactory(factory)
            .createMediaSource(MediaItem.fromUri(Uri.parse("file:///$fileName")))
    }

    private suspend fun getSpeakStream(speakText: String): InputStream? {
        while (true) {
            try {
                val ttsUrl = ToneVoiceManager.getTtsRequestUrl()
                val selectedTone = ToneVoiceManager.getSelectedTone()
                    ?: throw NoStackTraceException("未选择音色")
                
                LogUtils.d(TAG, "GSV TTS请求: URL=$ttsUrl, 音色=${selectedTone.name}, 文本=$speakText")
                
                val formBody = FormBody.Builder()
                    .add("text", speakText)
                    .add("text_language", "zh")
//                    .add("ref_audio_path", selectedTone.id) // 使用音色ID作为参考音频路径
                    // .add("prompt_language", "zh")
                    // .add("prompt_text", "")
                    // .add("top_k", "5")
                    // .add("top_p", "1")
                    // .add("temperature", "1")
                    // .add("text_split_method", "cut5")
                    // .add("batch_size", "1")
                    // .add("batch_threshold", "0.75")
                    // .add("split_bucket", "true")
                    .add("speed", (speechRate / 10.0).toString())
                    // .add("streaming_mode", "false")
                    // .add("seed", "-1")
                    // .add("parallel_infer", "true")
                    // .add("repetition_penalty", "1.35")
                    .build()
                
                val response = okHttpClient.newCallResponse {
                    url(ttsUrl)
                    post(formBody)
                    addHeader("Content-Type", "application/x-www-form-urlencoded")
                    addHeader("Accept", "audio/wav")
                }
                
                coroutineContext.ensureActive()
                
                if (!response.isSuccessful) {
                    throw NoStackTraceException("GSV TTS请求失败: HTTP ${response.code} - ${response.message}")
                }
                
                response.headers["Content-Type"]?.let { contentType ->
                    val contentType = contentType.substringBefore(";")
                    if (contentType == "application/json" || contentType.startsWith("text/")) {
                        throw NoStackTraceException("GSV TTS服务器返回错误：" + response.body?.string())
                    }
                }
                
                coroutineContext.ensureActive()
                val responseBody = response.body ?: throw NoStackTraceException("GSV TTS响应体为空")
                val stream = responseBody.byteStream()
                downloadErrorNo = 0
                return stream
            } catch (e: Exception) {
                when (e) {
                    is CancellationException -> throw e
                    is SocketTimeoutException, is ConnectException -> {
                        downloadErrorNo++
                        if (downloadErrorNo > 5) {
                            val msg = "GSV TTS超时或连接错误超过5次\n${e.localizedMessage}"
                            AppLog.put(msg, e, true)
                            throw e
                        }
                    }
                    else -> {
                        downloadErrorNo++
                        val msg = "GSV TTS下载错误\n${e.localizedMessage}"
                        AppLog.put(msg, e)
                        e.printOnDebug()
                        if (downloadErrorNo > 5) {
                            val msg1 = "GSV TTS服务器连续5次错误，已暂停阅读。"
                            AppLog.put(msg1, e, true)
                            throw e
                        } else {
                            AppLog.put("GSV TTS下载音频出错，使用无声音频代替。\n朗读文本：$speakText")
                            break
                        }
                    }
                }
            }
        }
        return null
    }

    private suspend fun md5SpeakFileName(content: String): String {
        val selectedTone = ToneVoiceManager.getSelectedTone()
        return MD5Utils.md5Encode16(textChapter?.title ?: "") + "_" +
                MD5Utils.md5Encode16("GSV-${selectedTone?.id ?: "default"}-|-$speechRate-|-$content")
    }

    private fun createSilentSound(fileName: String) {
        FileUtils.createFileIfNotExist(getSpeakFileAsMd5(fileName), "")
    }

    private fun hasSpeakFile(name: String): Boolean {
        return getSpeakFileAsMd5(name).exists()
    }

    private fun getSpeakFileAsMd5(name: String): File {
        return File(ttsFolderPath + name + ".wav")
    }

    private fun createSpeakFile(name: String): File {
        return FileUtils.createFileIfNotExist(ttsFolderPath + name + ".wav")
    }

    private fun createSpeakFile(name: String, inputStream: InputStream) {
        val file = createSpeakFile(name)
        file.writeBytes(inputStream.readBytes())
        inputStream.close()
    }

    private fun removeCacheFile() {
        val cacheFile = File(ttsFolderPath)
        if (cacheFile.exists()) {
            cacheFile.listFiles()?.let { files ->
                val sortedFiles = files.sortedBy { it.lastModified() }
                if (sortedFiles.size > 30) {
                    for (i in 0 until sortedFiles.size - 30) {
                        sortedFiles[i].delete()
                    }
                }
            }
        }
    }

    override fun pauseReadAloud(abandonFocus: Boolean) {
        super.pauseReadAloud(abandonFocus)
        downloadTask?.cancel()
        exoPlayer.pause()
        LogUtils.d(TAG, "GSV朗读暂停")
    }

    override fun resumeReadAloud() {
        super.resumeReadAloud()
        if (pageChanged) {
            play()
        } else {
            exoPlayer.play()
            LogUtils.d(TAG, "GSV朗读恢复")
        }
    }

    private fun upPlayPos() {
        playIndexJob?.cancel()
        val textChapter = textChapter ?: return
        playIndexJob = lifecycleScope.launch {
            while (coroutineContext.isActive) {
                if (exoPlayer.isPlaying) {
                    val currentMediaItemIndex = exoPlayer.currentMediaItemIndex
                    if (currentMediaItemIndex != nowSpeak) {
                        if (currentMediaItemIndex < contentList.size) {
                            nowSpeak = currentMediaItemIndex
                            readAloudNumber = textChapter.getReadLength(nowSpeak)
                            paragraphStartPos = 0
                            upTtsProgress(readAloudNumber + 1)
                            if (pageIndex + 1 < textChapter.pageSize
                                && readAloudNumber + 1 > textChapter.getReadLength(pageIndex + 1)
                            ) {
                                pageIndex++
                                ReadBook.moveToNextPage()
                            }
                        }
                    }
                }
                delay(100)
            }
        }
    }

    override fun upSpeechRate(reset: Boolean) {
        speechRate = AppConfig.speechRatePlay + 5
        exoPlayer.setPlaybackSpeed((speechRate + 5) / 10f)
        LogUtils.d(TAG, "GSV语速调整: $speechRate, reset: $reset")
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        when (playbackState) {
            Player.STATE_IDLE -> {
                // do nothing
            }
            Player.STATE_BUFFERING -> {
                // do nothing
            }
            Player.STATE_READY -> {
                if (exoPlayer.playWhenReady) {
                    upPlayPos()
                    removeCacheFile()
                }
            }
            Player.STATE_ENDED -> {
                nextChapter()
            }
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        super.onTimelineChanged(timeline, reason)
        if (timeline.isEmpty) return
        LogUtils.d(TAG, "时间线变更: windowCount=${timeline.windowCount}, reason=$reason")
        if (exoPlayer.playWhenReady && exoPlayer.playbackState == Player.STATE_READY) {
            upPlayPos()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)
        LogUtils.d(TAG, "媒体项切换: reason=$reason")
        upPlayPos()
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        LogUtils.d(TAG, "播放错误: ${error.localizedMessage}")
        playErrorNo++
        if (playErrorNo >= 5) {
            toastOnUi("朗读连续5次错误，已暂停")
            AppLog.put("朗读连续5次错误\n${error.localizedMessage}", error, true)
            pauseReadAloud(true)
        } else {
            deleteCurrentSpeakFile()
            play()
        }
    }

    private fun deleteCurrentSpeakFile() {
        lifecycleScope.launch {
            val fileName = md5SpeakFileName(contentList[nowSpeak])
            val file = getSpeakFileAsMd5(fileName)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    override fun aloudServicePendingIntent(actionStr: String): PendingIntent? {
        return servicePendingIntent<GsvTTSReadAloudService>(actionStr)
    }

    inner class CustomLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy(0) {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            return 1000
        }
    }
}