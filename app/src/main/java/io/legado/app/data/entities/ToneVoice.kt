package io.legado.app.data.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 音色实体类
 * @param id 音色ID，用于网络请求
 * @param name 音色名称
 * @param role 音色角色/分类
 * @param category 音色类别
 */
@Parcelize
data class ToneVoice(
    val id: String,
    val name: String,
    val role: String,
    val category: String
) : Parcelable {

    /**
     * 获取显示文本
     * @return 格式为 "name - category" 的显示文本
     */
    fun getDisplayText(): String {
        return if (category.isNotEmpty()) {
            "$name - $category"
        } else {
            name
        }
    }

    /**
     * 获取唯一标识
     * @return id+role 组成的唯一标识
     */
    fun getUniqueKey(): String {
        return "${id}_${role}"
    }

    /**
     * 获取完整描述
     * @return 包含所有信息的描述文本
     */
    fun getFullDescription(): String {
        return "$name ($category) - $role"
    }

    override fun toString(): String {
        return getDisplayText()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ToneVoice) return false
        return id == other.id && role == other.role
    }

    override fun hashCode(): Int {
        return (id + role).hashCode()
    }
}