package com.voicenotes.app.record

import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 腾讯云实时语音识别（WebSocket）。
 *
 * 官方文档：https://cloud.tencent.com/document/product/1093/48982
 * - 地址：wss://asr.cloud.tencent.com/asr/v2/<AppID>?{参数}
 * - 鉴权：HMAC-SHA1 签名（SecretID/SecretKey），参数需按 key 排序
 * - 音频：16kHz/16bit/单声道 PCM，binary 帧上传
 * - 返回：JSON text 帧；result.slice_type=1 为实时结果、=2 为最终结果；
 *         收到 final=1 表示全部识别结束
 */
class TencentIatClient(
    private val appId: String,
    private val secretId: String,
    private val secretKey: String,
    private val onResult: (isFinal: Boolean, text: String) -> Unit,
    private val onError: (String) -> Unit
) : IatStreamClient {

    companion object {
        private const val TAG = "TencentIatClient"
        private const val HOST = "asr.cloud.tencent.com"
        private const val PATH = "/asr/v2"
        /** 16k 中文普通话引擎。 */
        private const val ENGINE_MODEL = "16k_zh"
        private const val CLOSE_TIMEOUT_MS = 6_000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var running = false
    @Volatile private var stopRequested = false
    private var onStopped: (() -> Unit)? = null

    override fun start() {
        running = true
        stopRequested = false
        openSession()
    }

    override fun feedPcm(data: ByteArray) {
        if (stopRequested || !running) return
        try {
            webSocket?.send(ByteString.of(*data))
        } catch (e: Exception) {
            Log.e(TAG, "发送音频失败", e)
        }
    }

    override fun stop(onStopped: () -> Unit) {
        if (!running && webSocket == null) {
            onStopped()
            return
        }
        this.onStopped = onStopped
        stopRequested = true
        running = false
        val ws = webSocket
        if (ws == null) {
            finish()
            return
        }
        try {
            ws.send("""{"type":"end"}""")
        } catch (e: Exception) {
            Log.e(TAG, "发送结束帧失败", e)
            finish()
            return
        }
        Thread {
            try {
                Thread.sleep(CLOSE_TIMEOUT_MS)
            } catch (_: InterruptedException) {
            }
            if (webSocket != null) finish()
        }.start()
    }

    override fun shutdown() {
        webSocket?.close(1000, null)
        webSocket = null
        client.dispatcher.executorService.shutdown()
    }

    // ---------------- 内部实现 ----------------

    private fun openSession() {
        try {
            val request = Request.Builder().url(buildUrl()).build()
            webSocket = client.newWebSocket(request, listener)
        } catch (e: Exception) {
            Log.e(TAG, "打开连接失败", e)
            onError("连接腾讯云失败: ${e.message}")
        }
    }

    private fun buildUrl(): String {
        val now = System.currentTimeMillis() / 1000
        val expired = now + 3600
        val nonce = ((System.nanoTime() % 9_000_000_000L) + 1_000_000_000L).toString()
        val voiceId = UUID.randomUUID().toString()

        val params = sortedMapOf(
            "engine_model_type" to ENGINE_MODEL,
            "expired" to expired.toString(),
            "needvad" to "1",
            "nonce" to nonce,
            "secretid" to secretId,
            "timestamp" to now.toString(),
            "voice_format" to "1",
            "voice_id" to voiceId
        )
        val sortedQuery = params.entries.joinToString("&") { "${it.key}=${it.value}" }

        val signatureOrigin = "$HOST$PATH/$appId?$sortedQuery"
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        val signature = Base64.encodeToString(
            mac.doFinal(signatureOrigin.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )
        val encodedSignature = URLEncoder.encode(signature, "UTF-8")

        return "wss://$HOST$PATH/$appId?$sortedQuery&signature=$encodedSignature"
    }

    private val listener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "连接成功")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val root = JSONObject(text)
                val code = root.optInt("code", -1)
                if (code != 0) {
                    Log.e(TAG, "服务端错误: $text")
                    onError(root.optString("message", "腾讯云识别错误($code)"))
                    return
                }
                val result = root.optJSONObject("result")
                if (result != null) {
                    val sliceType = result.optInt("slice_type", 0)
                    val t = result.optString("voice_text_str", "")
                    if (t.isNotEmpty()) {
                        onResult(sliceType == 2, t)
                    }
                }
                if (root.optInt("final", 0) == 1) {
                    // 全部识别结束
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析响应失败", e)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "连接异常", t)
            if (!stopRequested) {
                onError("连接腾讯云失败: ${t.message}")
            }
            finish()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "连接已关闭: $code $reason")
            if (!stopRequested) finish()
        }
    }

    @Synchronized
    private fun finish() {
        val ws = webSocket
        webSocket = null
        ws?.close(1000, null)
        if (stopRequested) {
            stopRequested = false
            val cb = onStopped
            onStopped = null
            cb?.invoke()
        }
    }
}
