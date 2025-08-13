package io.legado.app.data.manager

import io.legado.app.data.appDb
import io.legado.app.data.entities.ToneVoice
import io.legado.app.data.entities.ToneVoiceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
                selectedToneId = toneVoice.id
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
                selectedToneId = selectedTone?.id
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
     * 模拟网络刷新数据
     */
    suspend fun refreshFromNetwork(): List<ToneVoice> {
        // 模拟网络延迟
        kotlinx.coroutines.delay(Random.nextLong(500, 1500))
        
        val newToneVoices = generateMockToneVoices()
        
        // 保存到数据库
        saveToneVoices(newToneVoices, newToneVoices.firstOrNull())
        
        return newToneVoices
    }
}