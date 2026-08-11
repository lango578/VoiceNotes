package com.voicenotes.app.record

import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 讯飞语音听写（流式版）WebSocket 客户端。
 *
 * 官方文档：https://www.xfyun.cn/doc/asr/voicedictation/API.html
 * - 地址：wss://iat-api.xfyun.cn/v2/iat
 * - 鉴权：URL query 参数 authorization / date / host（HMAC-SHA256 签名）
 * - 音频：16kHz、16bit、单声道 PCM，每段最长 60 秒
 * - 返回：JSON text 帧；启用动态修正(dwa=wpgs)后，
 *         pgs="rgp" 为实时结果（替换本句）、pgs="apd" 为最终结果（追加）。
 *
 * 为支持长录音，本类在每段约 50 秒时自动结束当前会话并开启下一段（链式切换）。
 */
class XunfeiIatClient(
    private val appId: String,
    private val apiKey: String,
    private val apiSecret: String,
    private val onResult: (isFinal: Boolean, text: String) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "XunfeiIatClient"
        private const val HOST = "iat-api.xfyun.cn"
        private const val PATH = "/v2/iat"
        private const val WS_URL = "wss://$HOST$PATH"
        /** 单次连接最长音频 60s，这里 50s 提前收尾并自动开下一段。 */
        private const val SESSION_MAX_MS = 50_000L
        /** 发送结束帧后等待服务端最后一帧的最长时间。 */
        private const val CLOSE_TIMEOUT_MS = 6_000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var sessionStartedAt = 0L
    @Volatile private var sessionEnding = false
    @Volatile private var active = false
    @Volatile private var stopRequested = false
    private var onStopped: (() -> Unit)? = null

    /** 开始识别（打开第一个会话）。 */
    fun start() {
        active = true
        stopRequested = false
        openSession()
    }

    /** 录音进行中持续调用：上传 PCM。 */
    fun feedPcm(data: ByteArray) {
        if (stopRequested || !active) return
        val ws = webSocket
        if (ws == null || sessionEnding) return // 会话建立中或正在收尾，丢弃这一小段
        if (System.currentTimeMillis() - sessionStartedAt > SESSION_MAX_MS) {
            endCurrentSession()
            return
        }
        val frame = JSONObject()
            .put(
                "data", JSONObject()
                    .put("status", 1)
                    .put("audio", Base64.encodeToString(data, Base64.NO_WRAP))
            )
        try {
            ws.send(frame.toString())
        } catch (e: Exception) {
            Log.e(TAG, "发送音频失败", e)
        }
    }

    /** 停止识别，等待服务端返回最后的最终结果后回调 [onStopped]。 */
    fun stop(onStopped: () -> Unit) {
        if (!active && webSocket == null) {
            onStopped()
            return
        }
        this.onStopped = onStopped
        stopRequested = true
        active = false
        endCurrentSession()
    }

    /** 释放 OkHttp 资源（应在 onStopped 回调之后调用）。 */
    fun shutdown() {
        webSocket?.close(1000, null)
        webSocket = null
        client.dispatcher.executorService.shutdown()
    }

    // ---------------- 内部实现 ----------------

    private fun openSession() {
        sessionEnding = false
        sessionStartedAt = System.currentTimeMillis()
        try {
            val request = Request.Builder().url(buildAuthUrl()).build()
            webSocket = client.newWebSocket(request, listener)
        } catch (e: Exception) {
            Log.e(TAG, "打开连接失败", e)
            onError("连接讯飞失败: ${e.message}")
        }
    }

    private fun endCurrentSession() {
        if (sessionEnding) return
        sessionEnding = true
        val ws = webSocket
        if (ws == null) {
            finishSession()
            return
        }
        try {
            ws.send(
                JSONObject()
                    .put("data", JSONObject().put("status", 2).put("audio", ""))
                    .toString()
            )
        } catch (e: Exception) {
            Log.e(TAG, "发送结束帧失败", e)
            finishSession()
            return
        }
        // 兜底：若服务端迟迟未返回最后一帧，超时后强制收尾（只针对当前会话）。
        Thread {
            try {
                Thread.sleep(CLOSE_TIMEOUT_MS)
            } catch (_: InterruptedException) {
            }
            if (sessionEnding && webSocket === ws) {
                finishSession()
            }
        }.start()
    }

    @Synchronized
    private fun finishSession() {
        if (!sessionEnding && webSocket == null && !stopRequested) return
        sessionEnding = false
        webSocket?.close(1000, null)
        webSocket = null
        if (stopRequested) {
            stopRequested = false
            val cb = onStopped
            onStopped = null
            cb?.invoke()
        } else if (active) {
            // 链式切换或断线自动重连
            openSession()
        }
    }

    private val listener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "会话已建立")
            val frame = JSONObject()
                .put("common", JSONObject().put("app_id", appId))
                .put(
                    "business", JSONObject()
                        .put("language", "zh_cn")
                        .put("domain", "iat")
                        .put("accent", "mandarin")
                        .put("vad_eos", 10000)   // 静音 10 秒结束当前句
                        .put("dwa", "wpgs")       // 动态修正：字级实时结果
                        .put("ptt", 1)            // 中文标点
                )
                .put(
                    "data", JSONObject()
                        .put("status", 0)
                        .put("format", "audio/L16;rate=16000")
                        .put("encoding", "raw")
                        .put("audio", "")
                )
            webSocket.send(frame.toString())
        }


        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val root = JSONObject(text)
                val code = root.optString("code", "0")
                if (code != "0") {
                    Log.e(TAG, "服务端错误: $text")
                    onError(root.optString("message", "识别服务错误($code)"))
                    return
                }
                val data = root.optJSONObject("data")
                if (data != null) {
                    val resultStr = data.optString("result", "")
                    if (resultStr.isNotEmpty()) {
                        handleResult(resultStr)
                    }
                    if (data.optInt("status", 0) == 2) {
                        // 服务端已返回全部结果，本段会话结束
                        finishSession()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析响应失败", e)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "连接异常", t)
            if (!stopRequested) {
                onError("连接讯飞失败: ${t.message}")
            }
            finishSession()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "连接已关闭: $code $reason")
            if (sessionEnding) finishSession()
        }
    }

    private fun handleResult(resultJson: String) {
        val r = JSONObject(resultJson)
        val pgs = r.optString("pgs", "")
        val sb = StringBuilder()
        val wsArr = r.optJSONArray("ws")
        if (wsArr != null) {
            for (i in 0 until wsArr.length()) {
                val item = wsArr.optJSONObject(i) ?: continue
                val cw = item.optJSONArray("cw") ?: continue
                for (j in 0 until cw.length()) {
                    val word = cw.optJSONObject(j) ?: continue
                    sb.append(word.optString("w", ""))
                }
            }
        }
        val text = sb.toString()
        if (text.isEmpty()) return
        val isFinal = pgs == "apd"
        onResult(isFinal, text)
    }

    private fun buildAuthUrl(): String {
        val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("GMT") }
            .format(Date())

        val signatureOrigin = "host: $HOST\ndate: $date\nGET $PATH HTTP/1.1"

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(apiSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val signature = Base64.encodeToString(
            mac.doFinal(signatureOrigin.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )

        val authorizationOrigin =
            "api_key=\"$apiKey\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"$signature\""
        val authorization = Base64.encodeToString(
            authorizationOrigin.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )

        return "$WS_URL?authorization=${URLEncoder.encode(authorization, "UTF-8")}" +
            "&date=${URLEncoder.encode(date, "UTF-8")}" +
            "&host=${URLEncoder.encode(HOST, "UTF-8")}"
    }
}
