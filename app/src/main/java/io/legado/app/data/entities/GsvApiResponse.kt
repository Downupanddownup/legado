package io.legado.app.data.entities

import com.google.gson.annotations.SerializedName

/**
 * GSV API 响应数据类
 */
data class GsvApiResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("msg")
    val msg: String,
    @SerializedName("count")
    val count: Int,
    @SerializedName("data")
    val data: GsvApiData
)

data class GsvApiData(
    @SerializedName("api_port")
    val apiPort: Int,
    @SerializedName("total_role_count")
    val totalRoleCount: Int,
    @SerializedName("total_product_count")
    val totalProductCount: Int,
    @SerializedName("product_list")
    val productList: List<GsvProduct>
)

data class GsvProduct(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("category")
    val category: String,
    @SerializedName("gpt_sovits_version")
    val gptSovitsVersion: String,
    @SerializedName("gpt_model_name")
    val gptModelName: String,
    @SerializedName("gpt_model_path")
    val gptModelPath: String,
    @SerializedName("vits_model_name")
    val vitsModelName: String,
    @SerializedName("vits_model_path")
    val vitsModelPath: String,
    @SerializedName("audio_id")
    val audioId: Int,
    @SerializedName("audio_name")
    val audioName: String,
    @SerializedName("audio_path")
    val audioPath: String,
    @SerializedName("content")
    val content: String,
    @SerializedName("language")
    val language: String,
    @SerializedName("audio_length")
    val audioLength: Double,
    @SerializedName("top_k")
    val topK: Double,
    @SerializedName("top_p")
    val topP: Double,
    @SerializedName("temperature")
    val temperature: Double,
    @SerializedName("text_delimiter")
    val textDelimiter: String,
    @SerializedName("speed")
    val speed: Double,
    @SerializedName("sample_steps")
    val sampleSteps: Int?,
    @SerializedName("if_sr")
    val ifSr: Boolean?,
    @SerializedName("inp_refs")
    val inpRefs: String,
    @SerializedName("sound_fusion_list")
    val soundFusionList: List<Any>,
    @SerializedName("score")
    val score: Int,
    @SerializedName("remark")
    val remark: String,
    @SerializedName("create_time")
    val createTime: String,
    @SerializedName("role_category")
    val roleCategory: String,
    @SerializedName("role_name")
    val roleName: String
) {
    /**
     * 转换为 ToneVoice 对象
     */
    fun toToneVoice(): ToneVoice {
        return ToneVoice(
            id = id.toString(),
            name = name,
            role = roleName,
            category = category
        )
    }
}