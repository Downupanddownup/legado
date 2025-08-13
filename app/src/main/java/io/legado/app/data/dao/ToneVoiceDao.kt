package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.ToneVoice
import io.legado.app.data.entities.ToneVoiceEntity
import kotlinx.coroutines.flow.Flow

/**
 * 音色配置数据访问对象
 */
@Dao
interface ToneVoiceDao {
    
    /**
     * 观察音色配置变化
     */
    @Query("SELECT * FROM tone_voices WHERE id = 1")
    fun observeToneVoiceConfig(): Flow<ToneVoiceEntity?>
    
    /**
     * 获取音色配置
     */
    @Query("SELECT * FROM tone_voices WHERE id = 1")
    suspend fun getToneVoiceConfig(): ToneVoiceEntity?
    
    /**
     * 插入或更新音色配置
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(config: ToneVoiceEntity)
    
    /**
     * 更新音色列表
     */
    @Query("UPDATE tone_voices SET toneVoices = :toneVoicesJson, lastUpdateTime = :updateTime WHERE id = 1")
    suspend fun updateToneVoices(toneVoicesJson: String, updateTime: Long)
    
    /**
     * 更新选中的音色ID
     */
    @Query("UPDATE tone_voices SET selectedToneId = :selectedToneId WHERE id = 1")
    suspend fun updateSelectedToneId(selectedToneId: String?)
    
    /**
     * 删除音色配置
     */
    @Query("DELETE FROM tone_voices WHERE id = 1")
    suspend fun deleteToneVoiceConfig()
    
    /**
     * 检查是否存在音色配置
     */
    @Query("SELECT COUNT(*) > 0 FROM tone_voices WHERE id = 1")
    suspend fun hasToneVoiceConfig(): Boolean
    
    /**
     * 获取最后更新时间
     */
    @Query("SELECT lastUpdateTime FROM tone_voices WHERE id = 1")
    suspend fun getLastUpdateTime(): Long?
}