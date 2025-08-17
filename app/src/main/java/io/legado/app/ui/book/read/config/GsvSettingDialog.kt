package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.entities.ToneVoice
import io.legado.app.data.manager.ToneVoiceManager
import io.legado.app.databinding.DialogGsvSettingBinding
import io.legado.app.utils.GsvConfigManager
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.toastOnUi
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.constant.AppLog
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.lib.theme.primaryTextColor
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GsvSettingDialog : BaseDialogFragment(R.layout.dialog_gsv_setting) {

    private val binding by viewBinding(DialogGsvSettingBinding::bind)
    private lateinit var toneListAdapter: ToneListAdapter // 列表适配器
    
    // URL 管理 - 从数据库获取
    private var currentUrl = "https://example.com/api" // 默认 URL

    // 音色数据列表 - 从数据库加载
    private var toneVoices = emptyList<ToneVoice>()
    private var selectedTone: ToneVoice? = null // 当前选中的音色对象

    /**
     * 将音色对象列表转换为按role分组的Map结构
     * @param voices 音色对象列表
     * @return 按role分组的Map，key为role，value为该role下的音色显示文本列表
     */
    private fun convertToDisplayData(voices: List<ToneVoice>): Map<String, List<ToneVoice>> {
        return voices.groupBy { it.role }
    }

    /**
     * 根据显示文本查找对应的音色对象
     * @param displayText 显示文本
     * @return 对应的音色对象，如果未找到则返回null
     */
    private fun findToneByDisplayText(displayText: String): ToneVoice? {
        return toneVoices.find { it.getDisplayText() == displayText }
    }

    /**
     * 根据唯一标识查找对应的音色对象
     */
    private fun findToneByUniqueKey(uniqueKey: String): ToneVoice? {
        return toneVoices.find { it.getUniqueKey() == uniqueKey }
    }

    override fun onStart() {
        super.onStart()
        // 设置弹窗位置和大小
        dialog?.window?.run {
            attributes?.gravity = Gravity.BOTTOM
            attributes?.width = ViewGroup.LayoutParams.MATCH_PARENT
            attributes?.height = ViewGroup.LayoutParams.WRAP_CONTENT
            attributes?.windowAnimations = R.style.AnimBottom
            setDimAmount(0.5f)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 每次对话框显示时重新加载选中状态
        lifecycleScope.launch {
            selectedTone = ToneVoiceManager.getSelectedTone()
            AppLog.put("GsvSettingDialog onResume: 当前选中音色 = ${selectedTone?.getDisplayText() ?: "无"}")
            // 如果适配器已初始化且有数据，强制更新选中状态
            if (::toneListAdapter.isInitialized && toneVoices.isNotEmpty()) {
                val selectedKey = selectedTone?.getUniqueKey() ?: ""  // 使用getUniqueKey()因为id可能重复
                AppLog.put("GsvSettingDialog onResume: 更新适配器选中状态，Key = $selectedKey")
                toneListAdapter.updateSelectedTone(selectedKey)
            }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        // 从数据库加载 URL
        loadUrlFromDatabase()
        // 从数据库加载音色历史数据
        loadToneVoicesFromDatabase()
        
        // 初始化 GSV TTS 开关状态
        binding.switchGsvEnable.isChecked = AppConfig.useGsvTTS
        
        // 设置 GSV TTS 开关事件
        binding.switchGsvEnable.setOnCheckedChangeListener { _, isChecked ->
            AppConfig.useGsvTTS = isChecked
            toastOnUi(if (isChecked) "GSV TTS 已启用" else "GSV TTS 已禁用")
        }

        // 设置 URL 相关的点击事件
        binding.btnUrlModify.setOnClickListener {
            showUrlModifyDialog()
        }

        binding.btnUrlRefresh.setOnClickListener {
            refreshDataFromUrl()
        }
    }

    /**
     * 从数据库加载 URL
     */
    private fun loadUrlFromDatabase() {
        lifecycleScope.launch {
            currentUrl = GsvConfigManager.getGsvUrl()
            updateUrlDisplay(currentUrl)
        }
    }

    /**
     * 更新 URL 显示文本
     */
    private fun updateUrlDisplay(url: String) {
        currentUrl = url
        binding.tvUrlDisplay.text = "当前 URL: $currentUrl"
    }

    /**
     * 显示 URL 修改弹窗
     */
    private fun showUrlModifyDialog() {
        val editText = EditText(requireContext()).apply {
            setText(currentUrl)
            setHint("请输入新的 URL")
        }
        AlertDialog.Builder(requireContext())
            .setTitle("修改 URL")
            .setView(editText)
            .setPositiveButton("确定") { dialog, _ ->
                val newUrl = editText.text.toString()
                if (newUrl.isNotEmpty()) {
                    saveUrlToDatabase(newUrl)
                    refreshDataFromUrl()
                    toastOnUi("URL 已修改并保存")
                } else {
                    toastOnUi("URL 不能为空")
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * 保存 URL 到数据库
     */
    private fun saveUrlToDatabase(url: String) {
        lifecycleScope.launch {
            GsvConfigManager.setGsvUrl(url)
            updateUrlDisplay(url)
        }
    }

    /**
     * 从数据库加载音色历史数据
     */
    private fun loadToneVoicesFromDatabase() {
        lifecycleScope.launch {
            val hasHistory = ToneVoiceManager.hasHistoryData()
            AppLog.put("GsvSettingDialog loadToneVoicesFromDatabase: 是否有历史数据 = $hasHistory")
            if (hasHistory) {
                // 有历史数据，从数据库加载
                toneVoices = ToneVoiceManager.getToneVoices()
                selectedTone = ToneVoiceManager.getSelectedTone()
                AppLog.put("GsvSettingDialog loadToneVoicesFromDatabase: 加载的音色数量 = ${toneVoices.size}, 选中音色 = ${selectedTone?.getDisplayText()}")
                toastOnUi("已加载历史音色数据")
                
                // 确保选中状态正确设置
                if (selectedTone == null && toneVoices.isNotEmpty()) {
                    selectedTone = toneVoices.first()
                    AppLog.put("GsvSettingDialog loadToneVoicesFromDatabase: 没有选中音色，默认选择第一个 = ${selectedTone?.getDisplayText()}")
                    // 保存默认选中的音色到数据库
                    ToneVoiceManager.setSelectedTone(selectedTone!!)
                }
            } else {
                // 没有历史数据，显示为空
                toneVoices = emptyList()
                selectedTone = null
                toastOnUi("暂无历史数据，请点击刷新获取")
            }
            setupToneList()
        }
    }

    /**
     * 刷新数据，从 URL 获取新的音色列表（带动态生成效果）
     */
    private fun refreshDataFromUrl() {
        lifecycleScope.launch {
            try {
                toastOnUi("正在刷新数据...")
                
                // 显示加载状态
                binding.btnUrlRefresh.isEnabled = false
                binding.btnUrlRefresh.text = "刷新中..."
                
                // 使用ToneVoiceManager模拟网络请求（带动态生成效果）
                val newToneVoices = withContext(Dispatchers.IO) {
                    ToneVoiceManager.refreshFromNetwork()
                }
                
                // 更新本地数据
                toneVoices = newToneVoices
                // 尝试保持之前选中的音色，如果不存在则选择第一个
                selectedTone = selectedTone?.let { currentSelected ->
                    newToneVoices.find { it.getUniqueKey() == currentSelected.getUniqueKey() }
                } ?: newToneVoices.firstOrNull()
                
                // 更新UI
                setupToneList()
                
                toastOnUi("数据刷新完成，已保存到数据库")
                
            } catch (e: Exception) {
                val errorMsg = "刷新失败: ${e.message}"
                AppLog.put("GsvSettingDialog refreshDataFromUrl 异常", e)
                toastOnUi(errorMsg)
            } finally {
                // 恢复按钮状态
                binding.btnUrlRefresh.isEnabled = true
                binding.btnUrlRefresh.text = "刷新"
            }
        }
    }

    /**
     * 设置音色列表
     */
    private fun setupToneList() {
        val displayData = convertToDisplayData(toneVoices)
        // 使用音色的getUniqueKey()，因为id可能重复
        val selectedKey = selectedTone?.getUniqueKey() ?: ""
        AppLog.put("GsvSettingDialog setupToneList: 设置音色列表，选中Key = $selectedKey, 适配器已初始化 = ${::toneListAdapter.isInitialized}")
        
        if (::toneListAdapter.isInitialized) {
            // 如果适配器已经初始化，更新数据和选中状态
            toneListAdapter.setData(displayData)
            toneListAdapter.updateSelectedTone(selectedKey)
        } else {
            // 首次初始化适配器
            AppLog.put("GsvSettingDialog setupToneList: 首次初始化适配器，选中Key = $selectedKey")
            toneListAdapter = ToneListAdapter(displayData, selectedKey) { selectedToneVoice ->
                // 点击回调，更新选中的音色
                selectedTone = selectedToneVoice
                
                // 保存选中状态到数据库并发起网络请求切换音色
                lifecycleScope.launch {
                    try {
                        // 先保存到本地数据库
                        ToneVoiceManager.setSelectedTone(selectedToneVoice)
                        
                        // 发起网络请求切换音色
                        val success = ToneVoiceManager.switchToneVoice(selectedToneVoice, selectedToneVoice.role)
                        if (success) {
                            toastOnUi("音色切换成功: ${selectedToneVoice.getDisplayText()}")
                        } else {
                            toastOnUi("音色切换失败，但已保存到本地")
                        }
                        
                        // 添加与SpeakEngineDialog中tvOk事件相同的非UI逻辑，但ttsEngine归零
                        ReadBook.book?.setTtsEngine(null)
                        AppConfig.ttsEngine = null  // ttsEngine归零
                        ReadAloud.upReadAloudClass()
                    } catch (e: Exception) {
                        AppLog.put("音色切换失败: ${e.message}", e)
                        toastOnUi("音色切换失败: ${e.message}")
                    }
                }
            }
            binding.rvTones.apply {
                layoutManager = LinearLayoutManager(requireContext())
                adapter = toneListAdapter
            }
        }
    }

    /**
     * 音色列表适配器
     * 支持分类显示和音色选择
     */
    class ToneListAdapter(
        private var data: Map<String, List<ToneVoice>>,
        private var selectedToneKey: String,
        private val onToneSelected: (ToneVoice) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val VIEW_TYPE_CATEGORY = 0
            private const val VIEW_TYPE_TONE = 1
        }

        private var flatList: List<Any> = createFlatList(data)

        private fun createFlatList(data: Map<String, List<ToneVoice>>): List<Any> {
            val list = mutableListOf<Any>()
            data.forEach { (category, tones) ->
                list.add(category) // 添加分类
                list.addAll(tones) // 添加音色对象
            }
            return list
        }

        fun setData(newData: Map<String, List<ToneVoice>>) {
            this.data = newData
            this.flatList = createFlatList(newData)
            notifyDataSetChanged()
        }

        fun updateSelectedTone(newSelectedKey: String) {
            AppLog.put("ToneListAdapter updateSelectedTone: 旧选中=${selectedToneKey}, 新选中=${newSelectedKey}")
            selectedToneKey = newSelectedKey
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return if (flatList[position] is String && data.containsKey(flatList[position] as String)) {
                VIEW_TYPE_CATEGORY
            } else {
                VIEW_TYPE_TONE
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                VIEW_TYPE_CATEGORY -> {
                    // 创建分类的 ViewHolder
                    val textView = TextView(parent.context).apply {
                        setPadding(16, 24, 16, 8)
                        textSize = 16f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        // 根据主题设置正确的文本颜色
                        val textColor = if (ThemeConfig.isDarkTheme()) {
                            context.getColor(R.color.md_dark_primary_text) // 暗色主题用白色文本
                        } else {
                            context.getColor(R.color.md_light_primary_text) // 亮色主题用黑色文本
                        }
                        setTextColor(textColor)
                    }
                    object : RecyclerView.ViewHolder(textView) {}
                }
                else -> {
                    // 创建音色的 ViewHolder
                    val textView = TextView(parent.context).apply {
                        setPadding(32, 16, 16, 16)
                        textSize = 14f
                        // 设置布局参数，确保填满宽度
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        // 根据主题设置正确的文本颜色
                        val textColor = if (ThemeConfig.isDarkTheme()) {
                            context.getColor(R.color.md_light_primary_text)
                        } else {
                            context.getColor(R.color.md_dark_primary_text)
                        }
                        setTextColor(textColor)
                        // 根据主题模式选择合适的背景
                        if (AppConfig.isEInkMode) {
                            setBackgroundResource(R.drawable.selector_tone_item_eink_bg)
                        } else {
                            setBackgroundResource(R.drawable.selector_tone_item_bg)
                        }
                        // 设置可选中状态
                        isClickable = true
                        isFocusable = true
                    }
                    object : RecyclerView.ViewHolder(textView) {}
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = flatList[position]
            when (getItemViewType(position)) {
                VIEW_TYPE_CATEGORY -> {
                    val textView = holder.itemView as TextView
                    textView.text = item as String
                    // 根据主题设置正确的文本颜色
                    val textColor = if (ThemeConfig.isDarkTheme()) {
                        holder.itemView.context.getColor(R.color.md_dark_primary_text) // 暗色主题用白色文本
                    } else {
                        holder.itemView.context.getColor(R.color.md_light_primary_text) // 亮色主题用黑色文本
                    }
                    textView.setTextColor(textColor)
                }
                VIEW_TYPE_TONE -> {
                    val toneVoice = item as ToneVoice
                    val textView = holder.itemView as TextView
                    textView.text = toneVoice.getDisplayText()
                    
                    // 根据主题设置正确的文本颜色
                    val textColor = if (ThemeConfig.isDarkTheme()) {
                        holder.itemView.context.getColor(R.color.md_dark_primary_text) // 暗色主题用白色文本
                    } else {
                        holder.itemView.context.getColor(R.color.md_light_primary_text) // 亮色主题用黑色文本
                    }
                    textView.setTextColor(textColor)
                    
                    // 为每个音色项设置点击监听器，直接传递当前的 ToneVoice 对象
                    holder.itemView.setOnClickListener {
                        selectedToneKey = toneVoice.getUniqueKey()  // 使用getUniqueKey()因为id可能重复
                        onToneSelected(toneVoice)
                        // 每次点击都更新UI状态，确保重复选中也能正常工作
                        notifyDataSetChanged()
                    }
                    
                    // 根据是否选中来设置选中状态（使用 getUniqueKey 进行比较）
                    val isSelected = toneVoice.getUniqueKey() == selectedToneKey
                    holder.itemView.isSelected = isSelected
                    if (isSelected) {
                        AppLog.put("ToneListAdapter onBindViewHolder: 设置选中状态 - ${toneVoice.getDisplayText()}")
                    }
                }
            }
        }

        override fun getItemCount(): Int = flatList.size
    }
}