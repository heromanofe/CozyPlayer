package com.example.cozyplayer

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager

class WindowFloating(val context: Context,val root:ViewGroup) {

        private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        private val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val rootView = layoutInflater.inflate(R.layout.fragment_player, root)
        private val windowParams = WindowManager.LayoutParams(
            0,
            0,
            0,
            0,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        private fun calculateSizeAndPosition(
            params: WindowManager.LayoutParams
        ) {
            val dm = context.resources.displayMetrics
            params.gravity = Gravity.BOTTOM or Gravity.RIGHT
            params.width = (dm.widthPixels*0.30).toInt()
            params.height = (dm.heightPixels*0.20).toInt()
            params.x = (dm.widthPixels - params.width) / 2
            params.y = (dm.heightPixels - params.height) / 2
        }

        private fun initWindowParams() {
            calculateSizeAndPosition(windowParams)
        }

        init {
            initWindowParams()
           // initWindow()
        }

        fun open() {
            try {
                windowManager.addView(rootView, windowParams)
            } catch (e: Exception) {
                // Ignore exception for now, but in production, you should have some
                // warning for the user here.
            }
        }

        fun close() {
            try {
                windowManager.removeView(rootView)
            } catch (e: Exception) {
                // Ignore exception for now, but in production, you should have some
                // warning for the user here.
            }
        }
}