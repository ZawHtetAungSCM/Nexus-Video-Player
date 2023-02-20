package com.example.videoplayer.ext

import android.app.Activity
import android.widget.Toast

fun Activity.showToast(message: String, isShort: Boolean? = true) {
    Toast.makeText(this, message, if (isShort!!) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
}