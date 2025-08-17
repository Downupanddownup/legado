package io.legado.app.data.manager

import io.legado.app.data.appDb
import io.legado.app.data.entities.ToneVoice
import io.legado.app.data.entities.ToneVoiceEntity
import io.legado.app.data.entities.GsvApiResponse
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.get
import android.content.Intent
import io.legado.app.constant.AppLog
import io.legado.app.constant.IntentAction
import io.legado.app.model.ReadAloud
import io.legado.app.service.BaseReadAloudService
import io.legado.app.service.GsvTTSReadAloudService
import io.legado.app.utils.startForegroundServiceCompat
import splitties.init.appCtx
import okhttp3.Response
import io.legado.app.utils.GSON
import io.legado.app.utils.GsvConfigManager
import io.legado.app.data.entities.HttpTTS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * 音色配置管理器
 */
object ToneVoiceManager {
    
    /**
     * 观察音色配置变化
     */
    fun observeToneVoices(): Flow<List<ToneVoice>> {
        return appDb.toneVoiceDao.observeToneVoiceConfig().map { config ->
            config?.toneVoices ?: emptyList()
        }
    }
    
    /**
     * 观察选中的音色
     */
    fun observeSelectedTone(): Flow<ToneVoice?> {
        return appDb.toneVoiceDao.observeToneVoiceConfig().map { config ->
            config?.getSelectedTone()
        }
    }
    
    /**
     * 获取音色列表
     */
    suspend fun getToneVoices(): List<ToneVoice> {
        return appDb.toneVoiceDao.getToneVoiceConfig()?.toneVoices ?: emptyList()
    }
    
    /**
     * 获取选中的音色
     */
    suspend fun getSelectedTone(): ToneVoice? {
        return appDb.toneVoiceDao.getToneVoiceConfig()?.getSelectedTone()
    }
    
    /**
     * 设置选中的音色
     */
    suspend fun setSelectedTone(toneVoice: ToneVoice?) {
        val config = appDb.toneVoiceDao.getToneVoiceConfig()
        if (config != null) {
            val updatedConfig = config.updateSelectedTone(toneVoice)
            appDb.toneVoiceDao.insertOrUpdate(updatedConfig)
        } else if (toneVoice != null) {
            // 如果没有配置但要设置音色，创建新配置
            val newConfig = ToneVoiceEntity(
                toneVoices = listOf(toneVoice),
                selectedToneId = toneVoice.getUniqueKey()
            )
            appDb.toneVoiceDao.insertOrUpdate(newConfig)
        }
    }
    
    /**
     * 保存音色列表
     */
    suspend fun saveToneVoices(toneVoices: List<ToneVoice>, selectedTone: ToneVoice? = null) {
        val config = appDb.toneVoiceDao.getToneVoiceConfig()
        val updatedConfig = if (config != null) {
            config.updateToneVoices(toneVoices).updateSelectedTone(selectedTone)
        } else {
            ToneVoiceEntity(
                toneVoices = toneVoices,
                selectedToneId = selectedTone?.getUniqueKey()
            )
        }
        appDb.toneVoiceDao.insertOrUpdate(updatedConfig)
    }
    
    /**
     * 检查是否有历史数据
     */
    suspend fun hasHistoryData(): Boolean {
        return appDb.toneVoiceDao.hasToneVoiceConfig()
    }
    
    /**
     * 清空音色配置
     */
    suspend fun clearToneVoices() {
        appDb.toneVoiceDao.deleteToneVoiceConfig()
    }
    
    /**
     * 获取最后更新时间
     */
    suspend fun getLastUpdateTime(): Long? {
        return appDb.toneVoiceDao.getLastUpdateTime()
    }
    
    /**
     * 生成模拟音色数据（带动态效果）
     */
    fun generateMockToneVoices(): List<ToneVoice> {
        val roles = listOf("女声", "男声", "童声", "老年")
        val categories = listOf("甜美", "磁性", "清脆", "温和", "活泼", "沉稳")
        val names = listOf(
            "小雅", "小美", "小柔", "小萌", "小甜",
            "小刚", "小明", "小强", "小伟", "小杰",
            "小宝", "小贝", "小乖", "小可", "小爱",
            "老王", "老李", "老张", "老赵", "老陈"
        )
        
        val toneVoices = mutableListOf<ToneVoice>()
        
        // 为每个角色生成音色
        roles.forEachIndexed { roleIndex, role ->
            val roleCategories = categories.shuffled().take(Random.nextInt(2, 4))
            roleCategories.forEachIndexed { categoryIndex, category ->
                val name = names.shuffled().first()
                val id = "${role}_${category}_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
                
                toneVoices.add(
                    ToneVoice(
                        id = id,
                        name = name,
                        role = role,
                        category = category
                    )
                )
            }
        }
        
        return toneVoices.shuffled()
    }
    
    /**
     * 获取用于 TTS 请求的完整 URL
     * @return TTS 请求的完整 URL，包含 API 端口信息
     */
    suspend fun getTtsRequestUrl(): String {
        return "${getBaseRequestUrl()}/ras"
    }

    /**
     * 获取用于 TTS 基础地址
     * @return 
     */
    suspend fun getBaseRequestUrl(): String {
        val gsvUrl = GsvConfigManager.getGsvUrl()
        val apiPort = GsvConfigManager.getApiPort()

        return if (apiPort.isNotBlank()) {
            // 从 GSV URL 中提取协议和主机部分
            val uri = try {
                java.net.URI(gsvUrl)
            } catch (e: Exception) {
                // 如果 URL 解析失败，回退到原始逻辑
                return "$gsvUrl"
            }

            val protocol = uri.scheme ?: "http"
            val host = uri.host ?: return "$gsvUrl"

            // 构建 TTS 服务的完整 URL：协议://主机:TTS端口/tts
            "$protocol://$host:$apiPort"
        } else {
            "$gsvUrl"
        }
    }

    /**
     * 切换音色 - 发起网络请求设置选中的音色
     * @param toneVoice 要设置的音色对象
     * @param roleName 角色名称（可选）
     * @return 是否设置成功
     */
    suspend fun switchToneVoice(toneVoice: ToneVoice, roleName: String? = null): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 获取基础请求地址
                val baseUrl = getBaseRequestUrl()
                if (baseUrl.isBlank()) {
                    throw Exception("TTS 服务地址未配置")
                }
                
                // 构建请求URL
                val apiUrl = "$baseUrl/set_product"
                
                // 构建查询参数
                val queryMap = mutableMapOf<String, String>()
                queryMap["product_id"] = toneVoice.id
                if (!roleName.isNullOrBlank()) {
                    queryMap["role_name"] = roleName
                }
                queryMap["stream_mode_type"] = if (AppConfig.streamReadAloudAudio) "1" else "0"
                
                // 发起网络请求
                val response = okHttpClient.newCallResponse {
                    get(apiUrl, queryMap)
                    // 添加请求头
                    addHeader("Connection", "close")
                    addHeader("Cache-Control", "no-cache")
                }
                
                // 检查响应是否成功
                if (!response.isSuccessful) {
                    throw Exception("切换音色失败: HTTP ${response.code} - ${response.message}")
                }
                
                // 检查响应内容
                val responseBody = response.body?.string() ?: ""
                AppLog.put("ToneVoiceManager"+ "音色切换响应: '$responseBody'+ 长度: ${responseBody.length}+ trim后: '${responseBody.trim()}'")
                val trimmedResponse = responseBody.trim().lowercase()
                if (trimmedResponse == "ok" || trimmedResponse.contains("ok")) {
                    // 音色切换成功后，清空GsvTTSReadAloudService的缓存
                    if (AppConfig.useGsvTTS && BaseReadAloudService.isRun) {
                        val intent = Intent(appCtx, GsvTTSReadAloudService::class.java)
                        intent.action = IntentAction.clearCache
                        appCtx.startForegroundServiceCompat(intent)
                    }
                    return@withContext true
                } else {
                    AppLog.put("ToneVoiceManager音色切换失败，响应不包含ok: '$responseBody'")
                    throw Exception("服务器返回错误: '$responseBody' (期望包含'ok')")
                }
                
            } catch (e: Exception) {
                throw Exception("切换音色失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 初始化音色设置
     * 在应用启动时调用，自动切换到数据库中已选中的音色
     * 只有在GSV TTS功能开启时才进行初始化
     */
    suspend fun initializeToneVoice() {
        try {
            // 检查GSV TTS开关是否开启
            if (!AppConfig.useGsvTTS) {
                return
            }
            
            val selectedTone = getSelectedTone()
            if (selectedTone != null) {
                switchToneVoice(selectedTone, selectedTone.role)
            }
        } catch (e: Exception) {
            // 初始化失败时不影响应用启动，只记录日志
            e.printStackTrace()
        }
    }
    
    /**
     * 从网络刷新数据
     */
    suspend fun refreshFromNetwork(): List<ToneVoice> {
        try {
            // 获取配置的 URL
            val baseUrl = GsvConfigManager.getGsvUrl()
            AppLog.put("ToneVoiceManager获取到的baseUrl: $baseUrl")
            
            if (baseUrl.isNullOrBlank()) {
                throw Exception("GSV URL 未配置")
            }
            
            val apiUrl = "$baseUrl/product/get_all_databases_finished_product_list"
            
            // 调试日志：打印构建的API URL
            AppLog.put("ToneVoiceManager构建的API URL: $apiUrl")
            
            // 发起网络请求，增加重试次数
            val response = okHttpClient.newCallStrResponse(retry = 2) {
                url(apiUrl)
                get()
                // 添加超时相关的请求头
                addHeader("Connection", "close") // 避免连接复用问题
                addHeader("Cache-Control", "no-cache")
            }
            
            // 检查响应是否成功
            if (!response.isSuccessful()) {
                throw Exception("网络请求失败: HTTP ${response.code()} - ${response.message()}")
            }
            
            // 解析 JSON 响应，增加更详细的错误处理
            val responseBody = response.body
            if (responseBody.isNullOrBlank()) {
                throw Exception("服务器返回空响应")
            }
            
            val apiResponse = try {
                GSON.fromJson(responseBody, GsvApiResponse::class.java)
            } catch (e: Exception) {
                throw Exception("JSON 解析失败: ${e.message}，响应内容: ${responseBody.take(200)}")
            }
            
            if (apiResponse == null) {
                throw Exception("响应数据解析为 null，响应内容: ${responseBody.take(200)}")
            }
            
            // 检查 API 响应状态
            if (apiResponse.code != 0) {
                throw Exception("API 返回错误: ${apiResponse.msg} (code: ${apiResponse.code})")
            }
            
            // 检查数据完整性
            if (apiResponse.data == null) {
                throw Exception("API 返回的 data 字段为 null")
            }
            
            if (apiResponse.data.productList == null) {
                throw Exception("API 返回的 productList 字段为 null")
            }
            
            // 保存 API 端口信息到配置中
            val apiPort = apiResponse.data.apiPort?.toString() ?: ""
            GsvConfigManager.setApiPort(apiPort)
            
            // 转换为 ToneVoice 列表
            val newToneVoices = apiResponse.data.productList.mapNotNull { product ->
                try {
                    product.toToneVoice()
                } catch (e: Exception) {
                    // 记录转换失败的产品，但不中断整个流程
                    null
                }
            }
            
            if (newToneVoices.isEmpty()) {
                throw Exception("没有有效的音色数据")
            }
            
            // 保存到数据库
            saveToneVoices(newToneVoices, newToneVoices.firstOrNull())
            
            return newToneVoices
            
        } catch (e: Exception) {
            // 网络请求失败时，返回模拟数据作为备用
            val mockToneVoices = generateMockToneVoices()
            saveToneVoices(mockToneVoices, mockToneVoices.firstOrNull())
            throw e // 重新抛出异常，让调用方知道网络请求失败了
        }
    }
    
    /**
     * 创建httpTTS对象实例
     * 根据用户提供的JSON结构创建HttpTTS对象，并将url中的协议、IP和端口替换为getTtsRequestUrl的返回值
     */
    suspend fun createHttpTTSInstance(): HttpTTS {
        val ttsRequestUrl = getTtsRequestUrl()
        
        // 构建完整的TTS URL，包含请求参数
        val fullUrl = "$ttsRequestUrl?text={{java.encodeURI(speakText)}}&text_language=zh&timestamp=${System.currentTimeMillis()},{\n" +
                "     \"method\": \"GET\"\n" +
                "}\n"
        
        return HttpTTS(
            name = "gsv",
            url = fullUrl,
        )
    }
}