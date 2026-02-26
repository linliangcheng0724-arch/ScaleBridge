package com.example.scalebridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log

/**
 * UsbPermissionReceiver
 * 處理 USB 權限請求的回調
 * 
 * 當用戶授予或拒絕 USB 權限時，系統會發送 Broadcast 到這裡
 */
class UsbPermissionReceiver : BroadcastReceiver() {

    companion object {
        /**
         * 權限回調監聽器
         * 使用 volatile 確保多線程可見性
         */
        @Volatile
        var permissionCallback: PermissionCallback? = null

        /**
         * 設置權限回調
         */
        fun setCallback(callback: PermissionCallback?) {
            permissionCallback = callback
        }
    }

    /**
     * 權限回調介面
     */
    interface PermissionCallback {
        /**
         * 權限結果回調
         * @param device USB 設備
         * @param granted 是否授予權限
         */
        fun onPermissionResult(device: UsbDevice?, granted: Boolean)
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(Constants.TAG, "UsbPermissionReceiver.onReceive: action=${intent.action}")

        if (intent.action != Constants.ACTION_USB_PERMISSION) {
            Log.w(Constants.TAG, "收到非預期的 action: ${intent.action}")
            return
        }

        synchronized(this) {
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }

            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

            if (device != null) {
                Log.i(Constants.TAG, "USB 權限結果: VID=0x${String.format("%04X", device.vendorId)}, " +
                        "PID=0x${String.format("%04X", device.productId)}, granted=$granted")
            } else {
                Log.w(Constants.TAG, "USB 權限結果: device=null, granted=$granted")
            }

            // 回調通知等待的請求
            val callback = permissionCallback
            if (callback != null) {
                Log.d(Constants.TAG, "觸發權限回調")
                callback.onPermissionResult(device, granted)
            } else {
                Log.w(Constants.TAG, "沒有設置權限回調")
            }
        }
    }
}


