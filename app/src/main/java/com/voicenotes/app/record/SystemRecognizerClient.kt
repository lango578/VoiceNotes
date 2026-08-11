package com.voicenotes.app.record

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * 系统 SpeechRecognizer 封装：连续识别、出错自动重启。
 *
 * 注意：依赖设备上存在系统识别服务（通常仅带 Google 服务的手机可用），
 * 使用前应先检查 [SpeechRecognizer.isRecognitionAvailable]。
 */
class SystemRecognizerClient(
    private val context: Context,
    private val onResult: (isFinal: Boolean, text: String) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "SystemRecognizer"
        private const val RESTART_DELAY_MS = 800L
    }

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    @Volatile private var running = false
    @Volatile private var restarting = false

    fun start() {
        if (running) return
        running = true
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        sr.setRecognitionListener(listener)
        listen()
    }

    fun stop() {
        running = false
        recognizer?.stopListening()
    }

    fun destroy() {
        running = false
        recognizer?.destroy()
        recognizer = null
    }

    private fun listen() {
        if (!running) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startListening 失败", e)
            onError("系统识别启动失败: ${e.message}")
        }
    }

    private fun restart() {
        if (!running || restarting) return
        restarting = true
        main.postDelayed({
            restarting = false
            if (running) listen()
        }, RESTART_DELAY_MS)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            restart()
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restart()
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    onError("缺少麦克风权限")
                else -> {
                    onError("系统识别错误: $error")
                    restart()
                }
            }
        }

        override fun onResults(results: Bundle?) {
            val arr = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!arr.isNullOrEmpty()) onResult(true, arr[0])
            restart()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val arr = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!arr.isNullOrEmpty()) onResult(false, arr[0])
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
