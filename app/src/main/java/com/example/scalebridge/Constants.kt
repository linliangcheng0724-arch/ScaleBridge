package com.example.scalebridge

/**
 * ScaleBridge 常量定義
 */
object Constants {

    // ==================== LOG TAG ====================
    const val TAG = "ScaleBridge"

    // ==================== USB 權限 ====================
    const val ACTION_USB_PERMISSION = "com.example.scalebridge.USB_PERMISSION"

    // ==================== STATUS CODES ====================
    const val STATUS_SUCCESS = "SUCCESS"
    const val STATUS_NO_DEVICE = "NO_DEVICE"
    const val STATUS_NO_PERMISSION = "NO_PERMISSION"
    const val STATUS_OPEN_FAIL = "OPEN_FAIL"
    const val STATUS_TIMEOUT = "TIMEOUT"
    const val STATUS_PARSE_FAIL = "PARSE_FAIL"
    const val STATUS_ERROR = "ERROR"

    // ==================== SERIAL PORT CONFIG ====================
    const val BAUD_RATE = 9600
    const val DATA_BITS = 8
    const val STOP_BITS = 2
    const val PARITY = 0
    const val READ_TIMEOUT_MS = 1000
    const val READ_BUFFER_SIZE = 1024

    // ==================== WEIGHT PARSING ====================
    // 匹配數字格式：小數（1-6位整數 + 小數點 + 1-4位小數）
    // 例如：1.2, 12.34, 1.234, 123.456, 0.5
    // 會自動從文字中提取，例如 "ST,GS,+  1.234kg" → "1.234"
    const val WEIGHT_REGEX = "[0-9]{1,6}\\.[0-9]{1,4}"

    // ==================== NOTIFICATION ====================
    const val NOTIFICATION_CHANNEL_ID = "scalebridge_channel"
    const val NOTIFICATION_CHANNEL_NAME = "ScaleBridge Service"
    const val NOTIFICATION_ID = 1001

    // ==================== SERVICE ====================
    const val ACTION_START_SERVICE = "com.example.scalebridge.START_SERVICE"

    // ==================== HTTP SERVER ====================
    const val HTTP_SERVER_PORT = 8080
}
