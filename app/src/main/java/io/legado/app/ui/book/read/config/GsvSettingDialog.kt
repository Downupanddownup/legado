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

    // 假设这是从 URL 获取到的二维数据
    private var toneData = mapOf(
        "分类一" to listOf("音色A", "音色B", "音色C"),
        "分类二" to listOf("音色D", "音色E"),
        "分类三" to listOf("音色F", "音色G", "音色H", "音色I")
    )
    private var selectedTone = "音色A" // 当前选中的音色

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
        setupToneList()

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
     * 刷新数据，从 URL 获取新的音色列表
     */
    private fun refreshDataFromUrl() {
        // TODO: 在这里执行网络请求，从 URL 获取最新的音色数据
        // 假设这里是一个模拟的网络请求
        toastOnUi("正在刷新数据...")

        // 模拟请求成功后更新数据
        val newData = mapOf(
            "新分类一" to listOf("新音色A", "新音色B"),
            "新分类二" to listOf("新音色C")
        )
        toneData = newData
        toneListAdapter.setData(newData)
        toastOnUi("数据刷新完成")
    }

    /**
     * 设置音色列表
     */
    private fun setupToneList() {
        // TODO: ToneListAdapter 和其 ViewHolder 需要你自己创建
        toneListAdapter = ToneListAdapter(toneData, selectedTone) { newSelectedTone ->
            // 点击回调，更新选中的音色
            selectedTone = newSelectedTone
            toastOnUi("选中了: $selectedTone")
        }
        binding.rvTones.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = toneListAdapter
        }
    }

    // TODO: 创建 RecyclerView 的适配器和 ViewHolder
    // 这是一个骨架，你需要根据你的具体需求来实现它
    // ToneListAdapter.kt
    class ToneListAdapter(
        private var data: Map<String, List<String>>,
        private var selectedTone: String,
        private val onToneSelected: (String) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val VIEW_TYPE_CATEGORY = 0
            private const val VIEW_TYPE_TONE = 1
        }

        private var flatList: List<Any> = createFlatList(data)

        private fun createFlatList(data: Map<String, List<String>>): List<Any> {
            val list = mutableListOf<Any>()
            data.forEach { (category, tones) ->
                list.add(category) // 添加分类
                list.addAll(tones) // 添加音色
            }
            return list
        }

        fun setData(newData: Map<String, List<String>>) {
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
                // TODO: 创建音色的 ViewHolder
                val textView = TextView(parent.context).apply {
                    setPadding(32, 16, 16, 16)
                    textSize = 14f
                    // TODO: 在这里设置选中背景
                    setOnClickListener {
                        val tone = (it as TextView).text.toString()
                        selectedTone = tone
                        onToneSelected(tone)
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
                    (holder.itemView as TextView).text = item as String
                    // TODO: 根据是否选中来设置背景或颜色
                    if (item == selectedTone) {
                        holder.itemView.setBackgroundResource(R.color.selected_background_color) // 你需要定义这个颜色
                    } else {
                        holder.itemView.setBackgroundResource(android.R.color.transparent)
                    }
                }
            }
        }

        override fun getItemCount(): Int = flatList.size
    }
}