package com.example.videoplayer.util

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.Window
import android.widget.ProgressBar
import com.example.videoplayer.R

class ProgressDialog(context: Context) {

    private var mDialog: Dialog = Dialog(context)

    init {
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        mDialog.setContentView(R.layout.progress_dialog_view)
        mDialog.findViewById<ProgressBar>(R.id.modal_progress_bar).visibility = View.VISIBLE
        mDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        mDialog.setCanceledOnTouchOutside(false)
    }

    fun show() { mDialog.show() }
    fun hide() { mDialog.dismiss() }
}