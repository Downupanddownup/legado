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
import kotlinx.coroutines.launch

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

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        // 从数据库加载 URL
        loadUrlFromDatabase()
        // 从数据库加载音色历史数据
        loadToneVoicesFromDatabase()

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
            if (hasHistory) {
                // 有历史数据，从数据库加载
                toneVoices = ToneVoiceManager.getToneVoices()
                selectedTone = ToneVoiceManager.getSelectedTone()
                toastOnUi("已加载历史音色数据")
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
                val newToneVoices = ToneVoiceManager.refreshFromNetwork()
                
                // 更新本地数据
                toneVoices = newToneVoices
                selectedTone = newToneVoices.firstOrNull()
                
                // 更新UI
                val displayData = convertToDisplayData(newToneVoices)
                if (::toneListAdapter.isInitialized) {
                    toneListAdapter.setData(displayData)
                } else {
                    setupToneList()
                }
                
                toastOnUi("数据刷新完成，已保存到数据库")
                
            } catch (e: Exception) {
                toastOnUi("刷新失败: ${e.message}")
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
        toneListAdapter = ToneListAdapter(displayData, selectedTone?.getDisplayText() ?: "") { selectedDisplayText ->
            // 点击回调，更新选中的音色
            val toneVoice = findToneByDisplayText(selectedDisplayText)
            selectedTone = toneVoice
            
            // 保存选中状态到数据库
            lifecycleScope.launch {
                ToneVoiceManager.setSelectedTone(toneVoice)
            }
            
            toastOnUi("选中了: ${toneVoice?.getDisplayText() ?: selectedDisplayText}")
        }
        binding.rvTones.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = toneListAdapter
        }
    }

    /**
     * 音色列表适配器
     * 支持分类显示和音色选择
     */
    class ToneListAdapter(
        private var data: Map<String, List<ToneVoice>>,
        private var selectedTone: String,
        private val onToneSelected: (String) -> Unit
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

        override fun getItemViewType(position: Int): Int {
            return if (flatList[position] is String && data.containsKey(flatList[position] as String)) {
                VIEW_TYPE_CATEGORY
            } else {
                VIEW_TYPE_TONE
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == VIEW_TYPE_CATEGORY) {
                // TODO: 创建分类的 ViewHolder
                val textView = TextView(parent.context).apply {
                    setPadding(16, 16, 16, 8)
                    textSize = 16f
                    // 正确的设置粗体的方式
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                object : RecyclerView.ViewHolder(textView) {}
            } else {
                // 创建音色的 ViewHolder
                val textView = TextView(parent.context).apply {
                    setPadding(32, 16, 16, 16)
                    textSize = 14f
                    setOnClickListener {
                        val displayText = (it as TextView).text.toString()
                        selectedTone = displayText
                        onToneSelected(displayText)
                        notifyDataSetChanged()
                    }
                }
                object : RecyclerView.ViewHolder(textView) {}
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = flatList[position]
            when (holder.itemViewType) {
                VIEW_TYPE_CATEGORY -> {
                    (holder.itemView as TextView).text = item as String
                }
                VIEW_TYPE_TONE -> {
                    val toneVoice = item as ToneVoice
                    (holder.itemView as TextView).text = toneVoice.getDisplayText()
                    // 根据是否选中来设置背景或颜色
                    if (toneVoice.getDisplayText() == selectedTone) {
                        holder.itemView.setBackgroundResource(android.R.color.holo_blue_light)
                    } else {
                        holder.itemView.setBackgroundResource(android.R.color.transparent)
                    }
                }
            }
        }

        override fun getItemCount(): Int = flatList.size
    }
}