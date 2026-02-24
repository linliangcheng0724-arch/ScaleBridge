package com.example.scalebridge

/**
 * ScaleBridge 常量定義
 * 包含所有 Broadcast Action、Extra Key、狀態碼等
 */
object Constants {

    // ==================== LOG TAG ====================
    const val TAG = "ScaleBridge"

    // ==================== BROADCAST ACTIONS ====================
    /**
     * 請求讀取重量的 Broadcast Action
     * 外部應用發送此 Action 來觸發重量讀取
     */
    const val ACTION_GET_WEIGHT = "com.myapp.GET_WEIGHT"

    /**
     * 回傳重量結果的 Broadcast Action
     * ScaleBridge 讀取完成後發送此 Action
     */
    const val ACTION_WEIGHT_RESULT = "com.myapp.WEIGHT_RESULT"

    /**
     * USB 權限請求的內部 Action
     */
    const val ACTION_USB_PERMISSION = "com.example.scalebridge.USB_PERMISSION"

    // ==================== BROADCAST EXTRAS ====================
    /**
     * 重量值 Extra Key
     * 成功時包含重量字串，如 "12.345"
     */
    const val EXTRA_WEIGHT = "weight"

    /**
     * 狀態碼 Extra Key
     * 包含操作結果狀態
     */
    const val EXTRA_STATUS = "status"

    /**
     * 詳細訊息 Extra Key
     * 包含人類可讀的詳細說明
     */
    const val EXTRA_MESSAGE = "message"

    // ==================== STATUS CODES ====================
    /**
     * 成功讀取重量
     */
    const val STATUS_SUCCESS = "SUCCESS"

    /**
     * 找不到 USB 設備
     */
    const val STATUS_NO_DEVICE = "NO_DEVICE"

    /**
     * 缺少 USB 權限
     */
    const val STATUS_NO_PERMISSION = "NO_PERMISSION"

    /**
     * 無法開啟 Serial Port
     */
    const val STATUS_OPEN_FAIL = "OPEN_FAIL"

    /**
     * 讀取超時
     */
    const val STATUS_TIMEOUT = "TIMEOUT"

    /**
     * 解析重量數據失敗
     */
    const val STATUS_PARSE_FAIL = "PARSE_FAIL"

    /**
     * 其他錯誤
     */
    const val STATUS_ERROR = "ERROR"

    // ==================== SERIAL PORT CONFIG ====================
    /**
     * 波特率
     */
    const val BAUD_RATE = 9600

    /**
     * 數據位
     */
    const val DATA_BITS = 8

    /**
     * 停止位 (2 = STOPBITS_2)
     */
    const val STOP_BITS = 2

    /**
     * 奇偶校驗 (0 = PARITY_NONE)
     */
    const val PARITY = 0

    /**
     * 讀取超時（毫秒）
     */
    const val READ_TIMEOUT_MS = 1000

    /**
     * 讀取緩衝區大小
     */
    const val READ_BUFFER_SIZE = 1024

    // ==================== WEIGHT PARSING ====================
    /**
     * 重量數據正則表達式
     * 匹配格式如：12.345, 0.123, 100.000
     */
    const val WEIGHT_REGEX = "[0-9]+\\.[0-9]{3}"

    // ==================== NOTIFICATION ====================
    /**
     * Notification Channel ID
     */
    const val NOTIFICATION_CHANNEL_ID = "scalebridge_channel"

    /**
     * Notification Channel 名稱
     */
    const val NOTIFICATION_CHANNEL_NAME = "ScaleBridge Service"

    /**
     * Notification ID
     */
    const val NOTIFICATION_ID = 1001

    // ==================== SERVICE ====================
    /**
     * Service 啟動 Action
     */
    const val ACTION_START_SERVICE = "com.example.scalebridge.START_SERVICE"
}

