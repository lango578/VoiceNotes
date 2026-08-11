package com.voicenotes.app.record

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 自建后端**流式**识别客户端（WebSocket → 后端 /ws/transcribe，Whisper 边录边出字）。
 *
 * 协议：
 *   - 连接 ws://host:8000/ws/transcribe?lang=en|zh|yue
 *   - 上行：二进制帧 = 16kHz/16bit/单声道 PCM；文本帧 "flush"（停止并取最终结果）
 *   - 下行：JSON 文本帧 {"type":"interim|final|error", ...}
 *
 * 若服务端不可用 / 未安装 faster-whisper / 中途断开，[streamFailed] 置位，
 * 上层（RecordingService）会回退为「录音结束后整段上传」的方式保存笔记。
 */
class BackendStreamClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val appLang: String,
    private val onResult: (isFinal: Boolean, text: String) -> Unit,
    private val onError: (msg: String) -> Unit
) : IatStreamClient {

    companion object {
        private const val TAG = "BackendStreamClient"
        private const val STOP_TIMEOUT_MS = 300_000L
        private const val PING_INTERVAL_SEC = 20L

        /** App 语言设置 → Whisper 语言代码（空 = 自动检测）。 */
        fun langToWhisper(appLang: String): String = when (appLang) {
            "english" -> "en"
            "cantonese" -> "yue"
            "mandarin" -> "zh"
            "sichuan" -> "zh"
            else -> ""
        }
    }

    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    @Volatile private var streamFailed = false
    @Volatile private var gotFinal = false

    private var ws: WebSocket? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)          // 长连接不超时
        .pingInterval(PING_INTERVAL_SEC, TimeUnit.SECONDS)
        .build()

    /** 服务端是否已返回最终结果（成功路径）。 */
    fun gotFinalResult(): Boolean = gotFinal

    /** 流式链路是否失败（失败时上层应回退整段上传）。 */
    fun isStreamFailed(): Boolean = streamFailed

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        val wsUrl = baseUrl.trimEnd('/')
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://") +
            "/ws/transcribe?lang=" + langToWhisper(appLang)
        val builder = Request.Builder().url(wsUrl)
        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
        }
        try {
            ws = client.newWebSocket(builder.build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i(TAG, "已连接后端流式识别: $wsUrl")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val json = JSONObject(text)
                        when (json.optString("type")) {
                            "interim" -> onResult(false, json.optString("text", ""))
                            "final" -> {
                                gotFinal = true
                                onResult(true, json.optString("text", ""))
                            }
                            "error" -> {
                                streamFailed = true
                                onError(json.optString("message", "后端流式识别错误"))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "解析后端消息失败", e)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    streamFailed = true
                    Log.e(TAG, "后端 WebSocket 失败", t)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (code == 1000) webSocket.close(1000, null)
                }
            })
        } catch (e: Exception) {
            streamFailed = true
            onError("连接后端流式识别失败: ${e.message}")
        }
    }

    override fun feedPcm(data: ByteArray) {
        if (closed.get() || streamFailed) return
        try {
            ws?.send(ByteString.of(*data))
        } catch (e: Exception) {
            streamFailed = true
            Log.e(TAG, "发送 PCM 失败", e)
        }
    }

    /** 非阻塞：发送 flush 后在后台等待最终结果/超时，然后回调。 */
    override fun stop(onStopped: () -> Unit) {
        try {
            ws?.send("flush")
        } catch (_: Exception) {
            streamFailed = true
        }
        Thread({
            val deadline = System.currentTimeMillis() + STOP_TIMEOUT_MS
            while (!gotFinal && !streamFailed && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(200)
                } catch (_: InterruptedException) {
                    break
                }
            }
            if (!gotFinal && !streamFailed) {
                // 超时仍未收到最终结果：标记失败，由上层回退
                streamFailed = true
            }
            onStopped()
        }, "backend-stream-stop").start()
    }

    override fun shutdown() {
        if (closed.compareAndSet(false, true)) {
            try {
                ws?.close(1000, null)
            } catch (_: Exception) {
            }
            ws = null
            client.dispatcher.executorService.shutdown()
        }
    }
}
