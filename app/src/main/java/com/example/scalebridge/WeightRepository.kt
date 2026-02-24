package com.example.scalebridge

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException
import java.util.regex.Pattern

/**
 * 重量讀取結果封裝類
 */
data class WeightResult(
    val weight: String?,
    val status: String,
    val message: String
)

/**
 * WeightRepository
 * 負責：
 * 1. 掃描所有 USB Serial 設備
 * 2. 嘗試開啟連接
 * 3. 讀取數據
 * 4. 解析重量值
 * 
 * 設計理念：
 * - 不硬寫 VID/PID
 * - 逐一嘗試所有驅動
 * - 使用 9600/8N2 配置
 */
class WeightRepository(private val context: Context) {

    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private val weightPattern: Pattern = Pattern.compile(Constants.WEIGHT_REGEX)

    // 用於同步保護
    private val lock = Any()

    /**
     * 獲取所有可用的 USB Serial 驅動
     */
    fun findAllDrivers(): List<UsbSerialDriver> {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        Log.i(Constants.TAG, "找到 ${drivers.size} 個 USB Serial 驅動")
        
        drivers.forEachIndexed { index, driver ->
            val device = driver.device
            Log.i(Constants.TAG, "驅動 #$index: VID=0x${String.format("%04X", device.vendorId)}, " +
                    "PID=0x${String.format("%04X", device.productId)}, " +
                    "Name=${device.deviceName}")
        }
        
        return drivers
    }

    /**
     * 檢查設備是否有 USB 權限
     */
    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    /**
     * 獲取第一個需要權限的設備
     */
    fun getFirstDeviceNeedingPermission(): UsbDevice? {
        val drivers = findAllDrivers()
        for (driver in drivers) {
            if (!usbManager.hasPermission(driver.device)) {
                Log.d(Constants.TAG, "設備需要權限: VID=0x${String.format("%04X", driver.device.vendorId)}")
                return driver.device
            }
        }
        return null
    }

    /**
     * 讀取重量（主要入口方法）
     * 使用 synchronized 保護，確保線程安全
     */
    fun readWeight(): WeightResult {
        synchronized(lock) {
            return readWeightInternal()
        }
    }

    /**
     * 內部讀取重量實現
     */
    private fun readWeightInternal(): WeightResult {
        val drivers = findAllDrivers()
        
        if (drivers.isEmpty()) {
            Log.w(Constants.TAG, "找不到任何 USB Serial 設備")
            return WeightResult(
                weight = null,
                status = Constants.STATUS_NO_DEVICE,
                message = "找不到任何 USB Serial 設備"
            )
        }

        // 逐一嘗試每個驅動
        for ((index, driver) in drivers.withIndex()) {
            val device = driver.device
            Log.i(Constants.TAG, "嘗試驅動 #$index: VID=0x${String.format("%04X", device.vendorId)}, " +
                    "PID=0x${String.format("%04X", device.productId)}")

            // 檢查權限
            if (!usbManager.hasPermission(device)) {
                Log.w(Constants.TAG, "驅動 #$index 缺少 USB 權限")
                continue
            }

            // 嘗試開啟並讀取
            val result = tryOpenAndRead(driver)
            if (result != null) {
                return result
            }
        }

        // 所有驅動都嘗試失敗
        // 檢查是否是權限問題
        val hasAnyPermission = drivers.any { usbManager.hasPermission(it.device) }
        
        return if (!hasAnyPermission) {
            Log.w(Constants.TAG, "所有設備都缺少權限")
            WeightResult(
                weight = null,
                status = Constants.STATUS_NO_PERMISSION,
                message = "所有 USB 設備都缺少權限"
            )
        } else {
            Log.w(Constants.TAG, "所有驅動都無法成功讀取")
            WeightResult(
                weight = null,
                status = Constants.STATUS_OPEN_FAIL,
                message = "無法開啟任何 USB Serial 設備"
            )
        }
    }

    /**
     * 嘗試開啟驅動並讀取數據
     * @return 成功返回 WeightResult，失敗返回 null（以便嘗試下一個驅動）
     */
    private fun tryOpenAndRead(driver: UsbSerialDriver): WeightResult? {
        var port: UsbSerialPort? = null
        var connection: android.hardware.usb.UsbDeviceConnection? = null

        try {
            // 獲取第一個 port
            if (driver.ports.isEmpty()) {
                Log.w(Constants.TAG, "驅動沒有可用的 port")
                return null
            }

            port = driver.ports[0]
            connection = usbManager.openDevice(driver.device)

            if (connection == null) {
                Log.w(Constants.TAG, "無法建立 USB 連接")
                return null
            }

            Log.i(Constants.TAG, "USB 連接建立成功")

            // 開啟 port
            port.open(connection)
            Log.i(Constants.TAG, "Serial port 開啟成功")

            // 設定參數：9600 / 8N2
            port.setParameters(
                Constants.BAUD_RATE,
                Constants.DATA_BITS,
                Constants.STOP_BITS,
                Constants.PARITY
            )
            Log.i(Constants.TAG, "Serial 參數設定: ${Constants.BAUD_RATE}/8N2")

            // 讀取數據
            val buffer = ByteArray(Constants.READ_BUFFER_SIZE)
            val dataBuilder = StringBuilder()
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < Constants.READ_TIMEOUT_MS) {
                try {
                    val bytesRead = port.read(buffer, 100) // 100ms 單次讀取超時
                    if (bytesRead > 0) {
                        val chunk = String(buffer, 0, bytesRead, Charsets.US_ASCII)
                        dataBuilder.append(chunk)
                        Log.d(Constants.TAG, "讀取 $bytesRead bytes: ${chunk.replace("\n", "\\n").replace("\r", "\\r")}")

                        // 嘗試解析
                        val weight = parseWeight(dataBuilder.toString())
                        if (weight != null) {
                            Log.i(Constants.TAG, "解析成功: weight=$weight")
                            return WeightResult(
                                weight = weight,
                                status = Constants.STATUS_SUCCESS,
                                message = "成功讀取重量: $weight"
                            )
                        }
                    }
                } catch (e: IOException) {
                    Log.w(Constants.TAG, "讀取時發生 IO 異常: ${e.message}")
                }
            }

            // 超時但有數據
            val rawData = dataBuilder.toString()
            if (rawData.isNotEmpty()) {
                Log.w(Constants.TAG, "讀取超時，原始數據: ${rawData.replace("\n", "\\n").replace("\r", "\\r")}")
                
                // 最後嘗試解析
                val weight = parseWeight(rawData)
                if (weight != null) {
                    Log.i(Constants.TAG, "超時後解析成功: weight=$weight")
                    return WeightResult(
                        weight = weight,
                        status = Constants.STATUS_SUCCESS,
                        message = "成功讀取重量: $weight"
                    )
                }
                
                return WeightResult(
                    weight = null,
                    status = Constants.STATUS_PARSE_FAIL,
                    message = "無法解析重量數據，原始數據: $rawData"
                )
            }

            Log.w(Constants.TAG, "讀取超時，無數據")
            return WeightResult(
                weight = null,
                status = Constants.STATUS_TIMEOUT,
                message = "讀取超時，未收到數據"
            )

        } catch (e: IOException) {
            Log.e(Constants.TAG, "開啟 port 時發生 IO 異常", e)
            return null
        } catch (e: SecurityException) {
            Log.e(Constants.TAG, "開啟 port 時發生安全異常", e)
            return null
        } catch (e: Exception) {
            Log.e(Constants.TAG, "開啟 port 時發生未預期異常", e)
            return null
        } finally {
            // 確保關閉 port，防止資源洩漏
            try {
                port?.close()
                Log.i(Constants.TAG, "Serial port 已關閉")
            } catch (e: Exception) {
                Log.e(Constants.TAG, "關閉 port 時發生異常", e)
            }
        }
    }

    /**
     * 解析重量字串
     * 使用正則表達式匹配格式：[0-9]+.[0-9]{3}
     */
    private fun parseWeight(data: String): String? {
        val matcher = weightPattern.matcher(data)
        return if (matcher.find()) {
            matcher.group()
        } else {
            null
        }
    }
}

