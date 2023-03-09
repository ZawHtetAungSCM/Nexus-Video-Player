package com.example.videoplayer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

class Constants {
    companion object {

        //Error Message
        @SuppressLint("StaticFieldLeak")
        var mCurrentActivity: Activity? = null

        // Encryption/Decryption
        const val AES_ALGORITHM = "AES"
        const val AES_TRANSFORMATION = "AES/CTR/NoPadding"
        const val CHUNKS_SIZE = 4096
        val ENCRYPT_KEY = ("S-C-M-MobileTeam").toByteArray()

        // API URL
        const val API = "http://172.20.10.70:8080/api/"

        // get the current activity for token expire check logout function
        fun getCurrentActivity(): Activity? {
            return mCurrentActivity
        }

        // set Current Activity
        fun setCurrentActivity(mCurrentActivity: Activity?) {
            this.mCurrentActivity = mCurrentActivity
        }

        // check the current activity of network
        fun checkNetwork(): Boolean {
            val connManager = mCurrentActivity!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork: NetworkInfo? = connManager.activeNetworkInfo
            val isConnected: Boolean = activeNetwork?.isConnectedOrConnecting == true
            if (!isConnected) {
                Toast.makeText( this.mCurrentActivity, "Network connection is not available", Toast.LENGTH_SHORT).show()
            }
            return isConnected
        }

        // Get CoroutineContext wih Handler
        fun getContextWithHandler(context: Context, block: () -> Unit): CoroutineContext {
            return Dispatchers.Main + CoroutineExceptionHandler { _, exception ->
                block()
                Log.d("getContextWithHandler","Coroutine Exception Handler got $exception")
                // Toast.makeText(context, "Connection Error", Toast.LENGTH_SHORT).show()
            }
        }
    }
}