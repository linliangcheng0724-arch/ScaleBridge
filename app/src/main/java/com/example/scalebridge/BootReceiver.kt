package com.example.scalebridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * BootReceiver
 * 開機自動啟動 UsbService
 * 
 * 確保：
 * 1. 開機後 Service 自動運行
 * 2. 防止 Android 11 背景限制影響
 * 3. 保持服務存活以接收 GET_WEIGHT 請求
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(Constants.TAG, "BootReceiver.onReceive: action=${intent.action}")

        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            Log.w(Constants.TAG, "收到非預期的 action: ${intent.action}")
            return
        }

        Log.i(Constants.TAG, "系統開機完成，啟動 UsbService")

        try {
            // 創建啟動 Service 的 Intent
            val serviceIntent = Intent(context, UsbService::class.java).apply {
                action = Constants.ACTION_START_SERVICE
            }

            // Android 8+ 需要使用 startForegroundService
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
                Log.i(Constants.TAG, "開機啟動: 使用 startForegroundService")
            } else {
                context.startService(serviceIntent)
                Log.i(Constants.TAG, "開機啟動: 使用 startService")
            }
        } catch (e: Exception) {
            Log.e(Constants.TAG, "開機啟動 UsbService 失敗", e)
        }
    }
}

