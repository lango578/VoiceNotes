package com.voicenotes.app.record

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 百度智能云实时语音识别（WebSocket）。
 *
 * 官方文档：https://cloud.baidu.com/doc/SPEECH/index.html （语音识别→实时语音识别）
 * - 令牌：POST https://aip.baidubce.com/oauth/2.0/token （client_id=APIKey, client_secret=SecretKey）
 * - 地址：wss://vop.baidu.com/realtime_asr?sn=&appid=&dev_pid=15372&cuid=&format=pcm&sample=16000&rate=16000&access_token=
 * - 流程：连接后发 START 文本帧 → 上传 PCM binary 帧 → 结束发 FINISH 帧
 * - 返回：MID_TEXT（实时）、FIN_TEXT（最终）、FINISH（会话结束）、ERROR
 */
class BaiduIatClient(
    private val appId: String,
    private val apiKey: String,
    private val secretKey: String,
    private val language: String,
    private val onResult: (isFinal: Boolean, text: String) -> Unit,
    private val onError: (String) -> Unit
) : IatStreamClient {

    companion object {
        private const val TAG = "BaiduIatClient"
        private const val HOST = "vop.baidu.com"
        private const val PATH = "/realtime_asr"
        private const val CLOSE_TIMEOUT_MS = 6_000L
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val wsClient = OkHttpClient.Builder()
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
        Thread {
            fetchTokenAndConnect()
        }.start()
    }

    private fun fetchTokenAndConnect() {
        try {
            val url = "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials" +
                "&client_id=$apiKey&client_secret=$secretKey"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string()
                val token = body?.let { JSONObject(it).optString("access_token", "") } ?: ""
                if (token.isNotEmpty()) {
                    openSession(token)
                } else {
                    Log.e(TAG, "access_token 获取失败: $body")
                    onError("获取百度 access_token 失败，请检查 APIKey/SecretKey")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取 token 失败", e)
            onError("获取百度 token 失败: ${e.message}")
        }
    }

    private fun openSession(token: String) {
        try {
            val sn = UUID.randomUUID().toString().replace("-", "")
            val cuid = "voicenotes_" + (System.currentTimeMillis() % 1_000_000L)
            val url = "wss://$HOST$PATH?sn=$sn&appid=$appId&dev_pid=$devPid" +
                "&cuid=$cuid&format=pcm&sample=16000&rate=16000&access_token=$token"
            val request = Request.Builder().url(url).build()
            webSocket = wsClient.newWebSocket(request, listener)
        } catch (e: Exception) {
            Log.e(TAG, "打开连接失败", e)
            onError("连接百度失败: ${e.message}")
        }
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
            ws.send("""{"type":"FINISH"}""")
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
        httpClient.dispatcher.executorService.shutdown()
        wsClient.dispatcher.executorService.shutdown()
    }

    // ---------------- 内部实现 ----------------

    /** dev_pid：15372 普通话 / 16362 粤语 / 17372 英语 / 19362 四川话。 */
    private val devPid: Int
        get() = when (language) {
            "cantonese" -> 16362
            "english" -> 17372
            "sichuan" -> 19362
            else -> 15372
        }

    private val listener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "连接成功")
            // 发送 START 帧
            try {
                val data = JSONObject()
                    .put("appid", appId)
                    .put("appkey", apiKey)
                    .put("dev_pid", devPid)
                    .put("cuid", "voicenotes")
                    .put("format", "pcm")
                    .put("sample", 16000)
                    .put("rate", 16000)
                val start = JSONObject().put("type", "START").put("data", data)
                webSocket.send(start.toString())
            } catch (e: Exception) {
                Log.e(TAG, "发送 START 失败", e)
                onError("百度 START 帧发送失败: ${e.message}")
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val root = JSONObject(text)
                when (root.optString("type", "")) {
                    "MID_TEXT" -> {
                        val t = root.optJSONObject("result")?.optString("text", "").orEmpty()
                        if (t.isNotEmpty()) onResult(false, t)
                    }
                    "FIN_TEXT" -> {
                        val t = root.optJSONObject("result")?.optString("text", "").orEmpty()
                        if (t.isNotEmpty()) onResult(true, t)
                    }
                    "FINISH" -> finish()
                    "ERROR" -> {
                        val msg = root.optString("desc", "百度识别错误 ${root.optInt("err_no", -1)}")
                        Log.e(TAG, "识别错误: $text")
                        onError(msg)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析响应失败", e)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "连接异常", t)
            if (!stopRequested) {
                onError("连接百度失败: ${t.message}")
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

