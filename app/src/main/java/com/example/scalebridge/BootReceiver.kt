package com.example.scalebridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * BootReceiver
 * 開機自動啟動 UsbService，以及服務被殺掉時重啟
 * 
 * 功能：
 * 1. 開機後 Service 自動運行
 * 2. 服務被殺掉時自動重啟
 * 3. 保持服務存活以接收 HTTP 請求
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_RESTART_SERVICE = "com.example.scalebridge.RESTART_SERVICE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(Constants.TAG, "BootReceiver.onReceive: action=${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.i(Constants.TAG, "系統開機完成，啟動 UsbService")
                startUsbService(context)
            }
            ACTION_RESTART_SERVICE -> {
                Log.i(Constants.TAG, "收到重啟請求，重新啟動 UsbService")
                startUsbService(context)
            }
            else -> {
                Log.w(Constants.TAG, "收到非預期的 action: ${intent.action}")
            }
        }
    }

    private fun startUsbService(context: Context) {
        try {
            val serviceIntent = Intent(context, UsbService::class.java).apply {
                action = Constants.ACTION_START_SERVICE
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
                Log.i(Constants.TAG, "使用 startForegroundService 啟動")
            } else {
                context.startService(serviceIntent)
                Log.i(Constants.TAG, "使用 startService 啟動")
            }
        } catch (e: Exception) {
            Log.e(Constants.TAG, "啟動 UsbService 失敗", e)
        }
    }
}
