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

    /**
     * 获取当前的GSV URL
     * @return 当前配置的URL，如果没有配置则返回默认URL
     */
    suspend fun getGsvUrl(): String {
        return appDb.gsvConfigDao.getConfig()?.url ?: DEFAULT_URL
    }

    /**
     * 设置GSV URL
     * @param url 要设置的URL
     */
    suspend fun setGsvUrl(url: String) {
        val config = GsvConfig(url = url)
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
     * 重置GSV URL为默认值
     */
    suspend fun resetToDefault() {
        setGsvUrl(DEFAULT_URL)
    }

    /**
     * 检查是否已配置GSV URL
     * @return true如果已配置，false如果使用默认值
     */
    suspend fun isConfigured(): Boolean {
        return appDb.gsvConfigDao.getConfig() != null
    }
}