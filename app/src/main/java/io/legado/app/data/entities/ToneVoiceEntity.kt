package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 音色配置实体类，用于Room数据库存储
 */
@Entity(tableName = "tone_voices")
@TypeConverters(ToneVoiceEntity.Converters::class)
data class ToneVoiceEntity(
    @PrimaryKey
    val id: Int = 1, // 固定ID，单例存储
    val toneVoices: List<ToneVoice> = emptyList(), // 音色列表
    val selectedToneId: String? = null, // 当前选中的音色UniqueKey（格式：id_role）
    val lastUpdateTime: Long = System.currentTimeMillis() // 最后更新时间
) {
    
    /**
     * 类型转换器，用于List<ToneVoice>与String之间的转换
     */
    class Converters {
        private val gson = Gson()
        
        @TypeConverter
        fun fromToneVoiceList(value: List<ToneVoice>?): String {
            return if (value == null) {
                ""
            } else {
                gson.toJson(value)
            }
        }
        
        @TypeConverter
        fun toToneVoiceList(value: String): List<ToneVoice> {
            return if (value.isEmpty()) {
                emptyList()
            } else {
                try {
                    val listType = object : TypeToken<List<ToneVoice>>() {}.type
                    gson.fromJson(value, listType) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
    }
    
    /**
     * 获取选中的音色对象
     */
    fun getSelectedTone(): ToneVoice? {
        return selectedToneId?.let { uniqueKey ->
            toneVoices.find { it.getUniqueKey() == uniqueKey }
        }
    }
    
    /**
     * 更新选中的音色
     */
    fun updateSelectedTone(toneVoice: ToneVoice?): ToneVoiceEntity {
        return copy(selectedToneId = toneVoice?.getUniqueKey())
    }
    
    /**
     * 更新音色列表
     */
    fun updateToneVoices(newToneVoices: List<ToneVoice>): ToneVoiceEntity {
        return copy(
            toneVoices = newToneVoices,
            lastUpdateTime = System.currentTimeMillis()
        )
    }
}