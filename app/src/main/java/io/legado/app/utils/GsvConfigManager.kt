package io.legado.app.utils

import io.legado.app.data.appDb
import io.legado.app.data.entities.GsvConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * GSV配置管理器
 * 提供便捷的方法来访问和管理GSV URL
 */
object GsvConfigManager {

    private const val DEFAULT_URL = "https://example.com/api"
    private const val DEFAULT_API_PORT = ""

    /**
     * 获取当前的GSV URL
     * @return 当前配置的URL，如果没有配置则返回默认URL
     */
    suspend fun getGsvUrl(): String {
        return appDb.gsvConfigDao.getConfig()?.url ?: DEFAULT_URL
    }

    /**
     * 获取当前的API端口
     * @return 当前配置的API端口，如果没有配置则返回默认值
     */
    suspend fun getApiPort(): String {
        return appDb.gsvConfigDao.getConfig()?.apiPort ?: DEFAULT_API_PORT
    }

    /**
     * 设置GSV URL
     * @param url 要设置的URL
     */
    suspend fun setGsvUrl(url: String) {
        val existingConfig = appDb.gsvConfigDao.getConfig()
        val config = if (existingConfig != null) {
            existingConfig.copy(url = url)
        } else {
            GsvConfig(url = url)
        }
        appDb.gsvConfigDao.insert(config)
    }

    /**
     * 设置API端口
     * @param apiPort 要设置的API端口
     */
    suspend fun setApiPort(apiPort: String) {
        val existingConfig = appDb.gsvConfigDao.getConfig()
        val config = if (existingConfig != null) {
            existingConfig.copy(apiPort = apiPort)
        } else {
            GsvConfig(apiPort = apiPort)
        }
        appDb.gsvConfigDao.insert(config)
    }

    /**
     * 同时设置GSV URL和API端口
     * @param url 要设置的URL
     * @param apiPort 要设置的API端口
     */
    suspend fun setGsvConfig(url: String, apiPort: String) {
        val config = GsvConfig(url = url, apiPort = apiPort)
        appDb.gsvConfigDao.insert(config)
    }

    /**
     * 观察GSV URL的变化
     * @return Flow<String> URL变化的流
     */
    fun observeGsvUrl(): Flow<String> {
        return appDb.gsvConfigDao.observeConfig().map { config ->
            config?.url ?: DEFAULT_URL
        }
    }

    /**
     * 观察API端口的变化
     * @return Flow<String> API端口变化的流
     */
    fun observeApiPort(): Flow<String> {
        return appDb.gsvConfigDao.observeConfig().map { config ->
            config?.apiPort ?: DEFAULT_API_PORT
        }
    }

    /**
     * 重置GSV配置为默认值
     */
    suspend fun resetToDefault() {
        setGsvConfig(DEFAULT_URL, DEFAULT_API_PORT)
    }

    /**
     * 检查是否已配置GSV URL
     * @return true如果已配置，false如果使用默认值
     */
    suspend fun isConfigured(): Boolean {
        return appDb.gsvConfigDao.getConfig() != null
    }
}