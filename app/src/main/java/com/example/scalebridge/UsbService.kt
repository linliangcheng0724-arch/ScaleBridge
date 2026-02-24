package com.example.scalebridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UsbService
 * 前台服務，負責：
 * 1. 維持前台狀態，防止 Android 11 背景限制
 * 2. 處理 USB 權限請求
 * 3. 執行重量讀取
 * 4. 回傳結果 Broadcast
 * 
 * 執行緒設計：
 * - 使用單次背景執行緒處理每個請求
 * - read timeout 1000ms
 * - synchronized 保護共享資料
 * - finally 確保 close port
 */
class UsbService : Service() {

    private lateinit var weightRepository: WeightRepository
    private lateinit var usbManager: UsbManager
    private var usbPermissionReceiver: UsbPermissionReceiver? = null

    // 標記 Service 是否已經啟動前台
    private val isForegroundStarted = AtomicBoolean(false)

    // 用於等待權限結果
    @Volatile
    private var permissionLatch: CountDownLatch? = null

    @Volatile
    private var permissionGranted: Boolean = false

    override fun onCreate() {
        super.onCreate()
        Log.i(Constants.TAG, "UsbService.onCreate")

        // 初始化
        weightRepository = WeightRepository(this)
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        // 創建 Notification Channel（Android 8+）
        createNotificationChannel()

        // 註冊 USB 權限 Receiver
        registerUsbPermissionReceiver()

        Log.i(Constants.TAG, "UsbService 初始化完成")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(Constants.TAG, "UsbService.onStartCommand: action=${intent?.action}, startId=$startId")

        // 確保前台服務啟動（只需要一次）
        ensureForegroundStarted()

        when (intent?.action) {
            Constants.ACTION_GET_WEIGHT -> {
                Log.i(Constants.TAG, "處理 GET_WEIGHT 請求")
                handleGetWeightRequest()
            }
            Constants.ACTION_START_SERVICE -> {
                Log.i(Constants.TAG, "Service 啟動完成（開機自啟或手動啟動）")
            }
            else -> {
                Log.d(Constants.TAG, "收到其他 action 或 null action")
            }
        }

        // START_STICKY: 如果被系統殺死，會嘗試重啟
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // 這是一個 started service，不需要 binding
        return null
    }

    override fun onDestroy() {
        Log.i(Constants.TAG, "UsbService.onDestroy")

        // 取消註冊 Receiver
        unregisterUsbPermissionReceiver()

        // 清理回調
        UsbPermissionReceiver.setCallback(null)

        super.onDestroy()
    }

    /**
     * 創建 Notification Channel（Android 8+ 必需）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                Constants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // 低重要性，不會發出聲音
            ).apply {
                description = "ScaleBridge USB 秤橋接服務"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(Constants.TAG, "Notification Channel 已創建")
        }
    }

    /**
     * 創建前台服務 Notification
     */
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("ScaleBridge")
            .setContentText("USB 電子秤橋接服務運行中")
            .setSmallIcon(android.R.drawable.ic_menu_compass) // 使用系統圖標
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    /**
     * 確保前台服務已啟動
     */
    private fun ensureForegroundStarted() {
        if (isForegroundStarted.compareAndSet(false, true)) {
            try {
                val notification = createNotification()
                startForeground(Constants.NOTIFICATION_ID, notification)
                Log.i(Constants.TAG, "前台服務已啟動")
            } catch (e: Exception) {
                Log.e(Constants.TAG, "啟動前台服務失敗", e)
                isForegroundStarted.set(false)
            }
        }
    }

    /**
     * 註冊 USB 權限 BroadcastReceiver
     */
    private fun registerUsbPermissionReceiver() {
        if (usbPermissionReceiver == null) {
            usbPermissionReceiver = UsbPermissionReceiver()
            val filter = IntentFilter(Constants.ACTION_USB_PERMISSION)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(usbPermissionReceiver, filter)
            }

            Log.d(Constants.TAG, "USB 權限 Receiver 已註冊")
        }
    }

    /**
     * 取消註冊 USB 權限 BroadcastReceiver
     */
    private fun unregisterUsbPermissionReceiver() {
        usbPermissionReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d(Constants.TAG, "USB 權限 Receiver 已取消註冊")
            } catch (e: Exception) {
                Log.w(Constants.TAG, "取消註冊 Receiver 時發生異常: ${e.message}")
            }
            usbPermissionReceiver = null
        }
    }

    /**
     * 處理 GET_WEIGHT 請求
     * 在背景執行緒中執行，避免阻塞主執行緒
     */
    private fun handleGetWeightRequest() {
        // 使用單次背景執行緒
        Thread {
            try {
                Log.i(Constants.TAG, "開始處理重量讀取請求（背景執行緒）")
                processWeightRequest()
            } catch (e: Exception) {
                Log.e(Constants.TAG, "處理重量請求時發生未預期異常", e)
                sendResult(null, Constants.STATUS_ERROR, "未預期錯誤: ${e.message}")
            }
        }.start()
    }

    /**
     * 處理重量請求的核心邏輯
     */
    private fun processWeightRequest() {
        // Step 1: 檢查是否有 USB 設備
        val drivers = weightRepository.findAllDrivers()
        if (drivers.isEmpty()) {
            Log.w(Constants.TAG, "找不到任何 USB Serial 設備")
            sendResult(null, Constants.STATUS_NO_DEVICE, "找不到任何 USB Serial 設備")
            return
        }

        // Step 2: 檢查權限，必要時請求
        val deviceNeedingPermission = weightRepository.getFirstDeviceNeedingPermission()
        if (deviceNeedingPermission != null) {
            Log.i(Constants.TAG, "需要請求 USB 權限")
            
            val permissionGranted = requestUsbPermission(deviceNeedingPermission)
            if (!permissionGranted) {
                Log.w(Constants.TAG, "USB 權限被拒絕")
                sendResult(null, Constants.STATUS_NO_PERMISSION, "USB 權限被拒絕")
                return
            }
            
            Log.i(Constants.TAG, "USB 權限已授予")
        }

        // Step 3: 讀取重量
        val result = weightRepository.readWeight()
        Log.i(Constants.TAG, "重量讀取結果: status=${result.status}, weight=${result.weight}")

        // Step 4: 發送結果
        sendResult(result.weight, result.status, result.message)
    }

    /**
     * 請求 USB 權限
     * @return true 如果權限被授予，false 如果被拒絕或超時
     */
    private fun requestUsbPermission(device: UsbDevice): Boolean {
        Log.i(Constants.TAG, "請求 USB 權限: VID=0x${String.format("%04X", device.vendorId)}")

        // 重置狀態
        permissionGranted = false
        permissionLatch = CountDownLatch(1)

        // 設置權限回調
        UsbPermissionReceiver.setCallback(object : UsbPermissionReceiver.PermissionCallback {
            override fun onPermissionResult(device: UsbDevice?, granted: Boolean) {
                Log.d(Constants.TAG, "權限回調: granted=$granted")
                permissionGranted = granted
                permissionLatch?.countDown()
            }
        })

        try {
            // 創建 PendingIntent
            val permissionIntent = Intent(Constants.ACTION_USB_PERMISSION)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(this, 0, permissionIntent, flags)

            // 請求權限
            usbManager.requestPermission(device, pendingIntent)
            Log.d(Constants.TAG, "USB 權限請求已發送")

            // 等待權限結果（最多等待 30 秒）
            val received = permissionLatch?.await(30, TimeUnit.SECONDS) ?: false
            
            if (!received) {
                Log.w(Constants.TAG, "等待 USB 權限超時")
                return false
            }

            return permissionGranted

        } catch (e: Exception) {
            Log.e(Constants.TAG, "請求 USB 權限時發生異常", e)
            return false
        } finally {
            // 清理
            UsbPermissionReceiver.setCallback(null)
            permissionLatch = null
        }
    }

    /**
     * 發送結果 Broadcast
     */
    private fun sendResult(weight: String?, status: String, message: String) {
        Log.i(Constants.TAG, "發送結果: status=$status, weight=$weight, message=$message")

        val resultIntent = Intent(Constants.ACTION_WEIGHT_RESULT).apply {
            if (weight != null) {
                putExtra(Constants.EXTRA_WEIGHT, weight)
            }
            putExtra(Constants.EXTRA_STATUS, status)
            putExtra(Constants.EXTRA_MESSAGE, message)
        }

        sendBroadcast(resultIntent)
        Log.d(Constants.TAG, "結果 Broadcast 已發送")
    }
}

