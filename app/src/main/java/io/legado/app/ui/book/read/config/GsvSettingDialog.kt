package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogGsvSettingBinding
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.toastOnUi

class GsvSettingDialog : BaseDialogFragment(R.layout.dialog_gsv_setting) {

    private val binding by viewBinding(DialogGsvSettingBinding::bind)

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            attributes?.gravity = Gravity.BOTTOM
            attributes?.width = ViewGroup.LayoutParams.MATCH_PARENT
            attributes?.height = ViewGroup.LayoutParams.WRAP_CONTENT
            attributes?.windowAnimations = R.style.AnimBottom
            setDimAmount(0.5f)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        // 在这里添加你的自定义逻辑，例如按钮点击事件
        binding.btnOption1.setOnClickListener {
            toastOnUi("点击了选项一")
            dismiss()
        }

        binding.btnOption2.setOnClickListener {
            toastOnUi("点击了选项二")
            dismiss()
        }
    }

    // 你可以移除 onFragmentCreated，因为它在父类中已经有抽象定义了
    // 这里是为了清晰起见，将你之前在 onViewCreated 中的代码移到了这里
    // 原始的 onViewCreated 方法可以删除，因为它已被父类覆盖
}