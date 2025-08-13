package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * GSV配置
 */
@Entity(tableName = "gsv_config")
data class GsvConfig(
    @PrimaryKey
    val id: Int = 1, // 使用固定ID，因为只需要存储一个GSV URL
    var url: String = "https://example.com/api"
)