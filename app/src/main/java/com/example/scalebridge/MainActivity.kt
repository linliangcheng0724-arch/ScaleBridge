package com.example.scalebridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * MainActivity
 * 提供 UI 介面讓用戶：
 * 1. 查看服務狀態
 * 2. 查看 API URL
 * 3. 測試讀取重量
 * 4. 手動啟動/停止服務
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvIpAddress: TextView
    private lateinit var tvApiUrl: TextView
    private lateinit var tvLastWeight: TextView
    private lateinit var btnStartService: Button
    private lateinit var btnStopService: Button
    private lateinit var btnTestRead: Button

    private lateinit var weightRepository: WeightRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化 WeightRepository
        weightRepository = WeightRepository(this)

        // 初始化 UI 元件
        initViews()

        // 更新顯示
        updateDisplay()

        // 設定按鈕事件
        setupButtons()

        // 自動啟動服務
        startUsbService()

        // 檢查並請求忽略電池優化
        checkBatteryOptimization()
    }

    /**
     * 檢查並請求忽略電池優化
     * 這樣可以讓 App 在背景持續運行
     */
    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName
            
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                // 顯示對話框詢問用戶
                AlertDialog.Builder(this)
                    .setTitle("需要關閉電池優化")
                    .setMessage("為了讓 ScaleBridge 在背景持續運行，請允許此 App 忽略電池優化。\n\n這不會明顯增加電量消耗。")
                    .setPositiveButton("前往設定") { _, _ ->
                        requestIgnoreBatteryOptimization()
                    }
                    .setNegativeButton("稍後再說", null)
                    .show()
            }
        }
    }

    /**
     * 請求忽略電池優化
     */
    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // 如果直接請求失敗，打開電池優化設定頁面
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                    Toast.makeText(this, "請在列表中找到 ScaleBridge 並關閉優化", Toast.LENGTH_LONG).show()
                } catch (e2: Exception) {
                    Toast.makeText(this, "無法打開設定，請手動前往設定關閉電池優化", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateDisplay()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvIpAddress = findViewById(R.id.tvIpAddress)
        tvApiUrl = findViewById(R.id.tvApiUrl)
        tvLastWeight = findViewById(R.id.tvLastWeight)
        btnStartService = findViewById(R.id.btnStartService)
        btnStopService = findViewById(R.id.btnStopService)
        btnTestRead = findViewById(R.id.btnTestRead)
    }

    private fun setupButtons() {
        btnStartService.setOnClickListener {
            startUsbService()
            Toast.makeText(this, "服務啟動中...", Toast.LENGTH_SHORT).show()
            updateDisplay()
        }

        btnStopService.setOnClickListener {
            stopUsbService()
            Toast.makeText(this, "服務已停止", Toast.LENGTH_SHORT).show()
            updateDisplay()
        }

        btnTestRead.setOnClickListener {
            testReadWeight()
        }
    }

    private fun updateDisplay() {
        // 更新 IP 地址
        val ip = getLocalIpAddress()
        if (ip != null) {
            tvIpAddress.text = "IP 地址: $ip"
            tvApiUrl.text = "API URL: http://$ip:${Constants.HTTP_SERVER_PORT}/weight"
            tvStatus.text = "狀態: 服務運行中 ✓"
            tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            tvIpAddress.text = "IP 地址: 未連接 WiFi"
            tvApiUrl.text = "API URL: 請先連接 WiFi"
            tvStatus.text = "狀態: 未連接網路"
            tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }

    private fun startUsbService() {
        val intent = Intent(this, UsbService::class.java).apply {
            action = Constants.ACTION_START_SERVICE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopUsbService() {
        val intent = Intent(this, UsbService::class.java)
        stopService(intent)
    }

    private fun testReadWeight() {
        tvLastWeight.text = "讀取中..."
        tvLastWeight.setTextColor(getColor(android.R.color.darker_gray))
        
        // 在背景執行緒讀取重量
        Thread {
            val result = weightRepository.readWeight()
            
            runOnUiThread {
                if (result.status == Constants.STATUS_SUCCESS && result.weight != null) {
                    tvLastWeight.text = "最新重量: ${result.weight} kg"
                    tvLastWeight.setTextColor(getColor(android.R.color.holo_green_dark))
                    Toast.makeText(this, "讀取成功: ${result.weight} kg", Toast.LENGTH_SHORT).show()
                } else {
                    tvLastWeight.text = "讀取失敗: ${result.message}"
                    tvLastWeight.setTextColor(getColor(android.R.color.holo_red_dark))
                    Toast.makeText(this, "讀取失敗: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}

