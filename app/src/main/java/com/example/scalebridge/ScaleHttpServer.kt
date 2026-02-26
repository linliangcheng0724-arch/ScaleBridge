package com.example.scalebridge

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

/**
 * ScaleHttpServer
 * 提供 HTTP API 讓外部應用（如 Odoo POS）查詢電子秤重量
 * 
 * API Endpoint:
 *   GET /weight
 * 
 * Response (JSON):
 *   成功: {"status": "SUCCESS", "weight": "1.234"}
 *   失敗: {"status": "ERROR_CODE", "message": "錯誤說明"}
 */
class ScaleHttpServer(
    private val port: Int = 8080,
    private val weightRepository: WeightRepository
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "ScaleHttpServer"
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        
        Log.d(TAG, "收到請求: $method $uri")

        // 添加 CORS 標頭，允許跨域請求
        val corsHeaders = mutableMapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, OPTIONS",
            "Access-Control-Allow-Headers" to "Content-Type, Accept"
        )

        // 處理 OPTIONS 預檢請求（CORS）
        if (method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.OK, "text/plain", "").apply {
                corsHeaders.forEach { (key, value) -> addHeader(key, value) }
            }
        }

        // 只處理 /weight 路徑
        if (uri == "/weight") {
            return handleWeightRequest().apply {
                corsHeaders.forEach { (key, value) -> addHeader(key, value) }
            }
        }

        // 其他路徑回傳 404
        val notFoundJson = JSONObject().apply {
            put("status", "NOT_FOUND")
            put("message", "Unknown endpoint. Use GET /weight")
        }
        return newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            "application/json",
            notFoundJson.toString()
        ).apply {
            corsHeaders.forEach { (key, value) -> addHeader(key, value) }
        }
    }

    /**
     * 處理 /weight 請求
     */
    private fun handleWeightRequest(): Response {
        return try {
            Log.i(TAG, "開始讀取重量...")
            
            // 使用 WeightRepository 讀取重量
            val result = weightRepository.readWeight()
            
            Log.i(TAG, "讀取結果: status=${result.status}, weight=${result.weight}")

            val json = JSONObject().apply {
                put("status", result.status)
                if (result.weight != null) {
                    put("weight", result.weight)
                }
                put("message", result.message)
            }

            val httpStatus = if (result.status == Constants.STATUS_SUCCESS) {
                Response.Status.OK
            } else {
                Response.Status.OK  // 即使讀取失敗也回傳 200，讓前端根據 status 判斷
            }

            newFixedLengthResponse(httpStatus, "application/json", json.toString())

        } catch (e: Exception) {
            Log.e(TAG, "處理重量請求時發生異常", e)
            
            val errorJson = JSONObject().apply {
                put("status", Constants.STATUS_ERROR)
                put("message", "伺服器錯誤: ${e.message}")
            }
            
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                errorJson.toString()
            )
        }
    }

    /**
     * 啟動 HTTP Server
     */
    fun startServer() {
        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.i(TAG, "HTTP Server 啟動成功，監聽 port $port")
        } catch (e: Exception) {
            Log.e(TAG, "HTTP Server 啟動失敗", e)
        }
    }

    /**
     * 停止 HTTP Server
     */
    fun stopServer() {
        try {
            stop()
            Log.i(TAG, "HTTP Server 已停止")
        } catch (e: Exception) {
            Log.e(TAG, "HTTP Server 停止時發生異常", e)
        }
    }
}

