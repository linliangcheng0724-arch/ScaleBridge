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
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UsbService
 * 前台服務，負責：
 * 1. 維持前台狀態，防止被系統殺掉
 * 2. 運行 HTTP Server 供 POS 查詢重量
 * 3. 處理 USB 權限請求
 */
class UsbService : Service() {

    private lateinit var weightRepository: WeightRepository
    private lateinit var usbManager: UsbManager
    private var usbPermissionReceiver: UsbPermissionReceiver? = null

    // HTTP Server for POS integration
    private var httpServer: ScaleHttpServer? = null

    // WakeLock 防止 CPU 休眠
    private var wakeLock: PowerManager.WakeLock? = null

    // 標記 Service 是否已經啟動前台
    private val isForegroundStarted = AtomicBoolean(false)

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

        // 啟動 HTTP Server（供 POS 查詢重量）
        startHttpServer()

        // 獲取 WakeLock 防止 CPU 休眠
        acquireWakeLock()

        Log.i(Constants.TAG, "UsbService 初始化完成")
    }

    /**
     * 獲取 WakeLock 防止 CPU 休眠
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ScaleBridge::ServiceWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(24 * 60 * 60 * 1000L) // 24 小時
            }
            Log.i(Constants.TAG, "WakeLock 已獲取")
        } catch (e: Exception) {
            Log.e(Constants.TAG, "獲取 WakeLock 失敗", e)
        }
    }

    /**
     * 釋放 WakeLock
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.i(Constants.TAG, "WakeLock 已釋放")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(Constants.TAG, "釋放 WakeLock 時發生異常", e)
        }
    }

    /**
     * 啟動 HTTP Server
     */
    private fun startHttpServer() {
        try {
            httpServer = ScaleHttpServer(
                port = Constants.HTTP_SERVER_PORT,
                weightRepository = weightRepository
            )
            httpServer?.startServer()
            Log.i(Constants.TAG, "HTTP Server 已啟動，port=${Constants.HTTP_SERVER_PORT}")
        } catch (e: Exception) {
            Log.e(Constants.TAG, "啟動 HTTP Server 失敗", e)
        }
    }

    /**
     * 停止 HTTP Server
     */
    private fun stopHttpServer() {
        try {
            httpServer?.stopServer()
            httpServer = null
            Log.i(Constants.TAG, "HTTP Server 已停止")
        } catch (e: Exception) {
            Log.e(Constants.TAG, "停止 HTTP Server 時發生異常", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(Constants.TAG, "UsbService.onStartCommand: action=${intent?.action}, startId=$startId")

        // 確保前台服務啟動（只需要一次）
        ensureForegroundStarted()

        when (intent?.action) {
            Constants.ACTION_START_SERVICE -> {
                Log.i(Constants.TAG, "Service 啟動完成")
            }
            else -> {
                Log.d(Constants.TAG, "收到其他 action 或 null action")
            }
        }

        // START_STICKY: 如果被系統殺死，會嘗試重啟
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        Log.i(Constants.TAG, "UsbService.onDestroy")

        // 釋放 WakeLock
        releaseWakeLock()

        // 停止 HTTP Server
        stopHttpServer()

        // 取消註冊 Receiver
        unregisterUsbPermissionReceiver()

        // 清理回調
        UsbPermissionReceiver.setCallback(null)

        super.onDestroy()
    }

    /**
     * 當服務被系統殺掉時，嘗試重啟
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(Constants.TAG, "onTaskRemoved - 嘗試重啟服務")
        
        val restartIntent = Intent(this, BootReceiver::class.java).apply {
            action = "com.example.scalebridge.RESTART_SERVICE"
        }
        sendBroadcast(restartIntent)
        
        super.onTaskRemoved(rootIntent)
    }

    /**
     * 創建 Notification Channel（Android 8+ 必需）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                Constants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
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
        val ipAddress = getLocalIpAddress() ?: "未連接 WiFi"
        val apiUrl = if (ipAddress != "未連接 WiFi") {
            "http://$ipAddress:${Constants.HTTP_SERVER_PORT}/weight"
        } else {
            "請先連接 WiFi"
        }

        // 點擊通知時打開 MainActivity
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("⚖️ ScaleBridge 運行中")
            .setContentText("API: $apiUrl")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("HTTP API 已啟動\n\nPOS 設定網址:\n$apiUrl"))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    /**
     * 取得本機 IP 地址
     */
    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(Constants.TAG, "取得 IP 地址失敗", e)
        }
        return null
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
}
