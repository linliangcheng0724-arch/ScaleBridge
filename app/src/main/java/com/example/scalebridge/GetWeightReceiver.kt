package com.example.scalebridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * GetWeightReceiver
 * 接收外部應用的重量讀取請求
 * 
 * 外部應用發送 Broadcast:
 * Action: com.myapp.GET_WEIGHT
 * 
 * 此 Receiver 會：
 * 1. 啟動/喚醒 UsbService
 * 2. 由 UsbService 執行實際的重量讀取
 * 3. UsbService 完成後發送結果 Broadcast
 */
class GetWeightReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(Constants.TAG, "GetWeightReceiver.onReceive: action=${intent.action}")

        if (intent.action != Constants.ACTION_GET_WEIGHT) {
            Log.w(Constants.TAG, "收到非預期的 action: ${intent.action}")
            return
        }

        Log.i(Constants.TAG, "收到 GET_WEIGHT 請求，啟動 UsbService")

        try {
            // 創建啟動 Service 的 Intent
            val serviceIntent = Intent(context, UsbService::class.java).apply {
                action = Constants.ACTION_GET_WEIGHT
            }

            // Android 8+ 需要使用 startForegroundService
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
                Log.d(Constants.TAG, "使用 startForegroundService 啟動")
            } else {
                context.startService(serviceIntent)
                Log.d(Constants.TAG, "使用 startService 啟動")
            }
        } catch (e: Exception) {
            Log.e(Constants.TAG, "啟動 UsbService 失敗", e)
            
            // 發送錯誤結果
            sendErrorResult(context, Constants.STATUS_ERROR, "啟動服務失敗: ${e.message}")
        }
    }

    /**
     * 發送錯誤結果 Broadcast
     */
    private fun sendErrorResult(context: Context, status: String, message: String) {
        Log.w(Constants.TAG, "發送錯誤結果: status=$status, message=$message")
        
        val resultIntent = Intent(Constants.ACTION_WEIGHT_RESULT).apply {
            putExtra(Constants.EXTRA_STATUS, status)
            putExtra(Constants.EXTRA_MESSAGE, message)
            // 設置 package 以提高安全性（可選）
            // setPackage("com.target.app")
        }
        
        context.sendBroadcast(resultIntent)
    }
}

